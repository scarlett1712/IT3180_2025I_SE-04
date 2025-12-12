import express from "express";
import ExcelJS from 'exceljs';
import { pool } from "../db.js";
// 🔥 Import helper để gửi thông báo
import { sendNotification } from "../utils/firebaseHelper.js";
import { verifySession } from "../middleware/authMiddleware.js";

const router = express.Router();

// 🧩 Helper query
const query = (text, params) => pool.query(text, params);

// 🧱 Tạo bảng nếu chưa có
export const createFinanceTables = async () => {
  try {
    await query(`
      CREATE TABLE IF NOT EXISTS finances (
        id SERIAL PRIMARY KEY,
        title VARCHAR(255) NOT NULL,
        content TEXT,
        amount NUMERIC(12, 2),
        type VARCHAR(50) DEFAULT 'khoan_thu' NOT NULL,
        due_date DATE,
        created_by INTEGER,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );
    `);

    await query(`
      CREATE TABLE IF NOT EXISTS user_finances (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL,
        finance_id INTEGER NOT NULL REFERENCES finances(id) ON DELETE CASCADE,
        status VARCHAR(50) DEFAULT 'chua_thanh_toan',
        UNIQUE(user_id, finance_id)
      );
    `);

    // Bảng giá điện nước (Giữ nguyên)
    await query(`
      CREATE TABLE IF NOT EXISTS utility_rates (
        rate_id serial PRIMARY KEY,
        type character varying(20) NOT NULL,
        tier_name character varying(100),
        min_usage integer DEFAULT 0,
        max_usage integer,
        price numeric(10, 2) NOT NULL,
        updated_at timestamp without time zone DEFAULT now()
      );
    `);

    await query(`CREATE INDEX IF NOT EXISTS idx_finances_created_by ON finances(created_by);`);
    await query(`CREATE INDEX IF NOT EXISTS idx_user_finances_finance_id ON user_finances(finance_id);`);
    await query(`CREATE INDEX IF NOT EXISTS idx_user_finances_user_id ON user_finances(user_id);`);

    console.log("✅ Finance tables and indexes verified or created successfully.");
  } catch (err) {
    console.error("💥 Error creating finance tables or indexes:", err);
  }
};

// 🟢 [ADMIN] Lấy toàn bộ khoản thu
router.get("/all", async (req, res) => {
  try {
    const result = await query(`
      SELECT id, title, content, amount, type,
             TO_CHAR(due_date, 'YYYY-MM-DD') AS due_date,
             TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI') AS created_at,
             created_by
      FROM finances
      ORDER BY due_date ASC NULLS LAST
    `);
    res.json(result.rows);
  } catch (err) {
    console.error("💥 Error fetching finances:", err);
    res.status(500).json({ error: "Lỗi server khi lấy dữ liệu tài chính." });
  }
});

// 🟡 [USER] Lấy khoản thu của 1 user
router.get("/user/:userId", async (req, res) => {
  const { userId } = req.params;
  try {
    const result = await query(
      `
      SELECT f.id, f.title, f.content, f.amount AS price, f.type,
             TO_CHAR(f.due_date, 'DD-MM-YYYY') AS due_date,
             uf.status
      FROM finances f
      JOIN user_finances uf ON f.id = uf.finance_id
      WHERE uf.user_id = $1
        AND f.type != 'chi_phi'
      ORDER BY f.due_date ASC NULLS LAST;
    `,
      [userId]
    );
    res.json(result.rows);
  } catch (err) {
    console.error("💥 Error fetching user finances:", err);
    res.status(500).json({ error: "Lỗi server khi lấy dữ liệu tài chính người dùng." });
  }
});

// 🧾 [ADMIN] Tạo khoản thu theo phòng (CÓ GỬI THÔNG BÁO)
router.post("/create", async (req, res) => {
  const { title, content, amount, due_date, target_rooms, type, created_by } = req.body;

  if (!title || !target_rooms || !Array.isArray(target_rooms)) {
    return res
      .status(400)
      .json({ error: "Thiếu trường bắt buộc hoặc target_rooms không hợp lệ." });
  }

  const finalType = type && type.trim() !== "" ? type : "Bắt buộc";

  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    // 🧾 1. Tạo khoản thu
    const financeResult = await client.query(
      `
      INSERT INTO finances (title, content, amount, due_date, type, created_by)
      VALUES ($1, $2, $3, TO_DATE($4, 'DD-MM-YYYY'), $5, $6)
      RETURNING id
      `,
      [title, content || "", amount, due_date, finalType, created_by || null]
    );

    const newFinanceId = financeResult.rows[0].id;

    // 🧍‍♂️ 2. Lấy danh sách cư dân & TOKEN
    const userQuery = `
      SELECT ui.user_id, u.fcm_token
      FROM user_item ui
      JOIN users u ON ui.user_id = u.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE a.apartment_number = ANY($1)
    `;
    const userResult = await client.query(userQuery, [target_rooms]);

    if (userResult.rows.length === 0) {
      await client.query("ROLLBACK");
      return res
        .status(404)
        .json({ error: "Không tìm thấy cư dân thuộc các phòng được chọn." });
    }

    // 🧾 3. Gán khoản thu & Gửi thông báo
    const insertUserFinance = `
      INSERT INTO user_finances (user_id, finance_id)
      VALUES ($1, $2)
      ON CONFLICT (user_id, finance_id) DO NOTHING
    `;

    for (const row of userResult.rows) {
      await client.query(insertUserFinance, [row.user_id, newFinanceId]);

      // 🔥 Gửi thông báo
      if (row.fcm_token) {
          sendNotification(
              row.fcm_token,
              "🔔 Thông báo phí mới",
              `Bạn có khoản thu mới: "${title}". Vui lòng kiểm tra và thanh toán.`,
              { type: "finance", id: newFinanceId.toString() }
          );
      }
    }

    await client.query("COMMIT");
    res.status(201).json({
      success: true,
      message: "Tạo khoản thu thành công và đã gửi thông báo.",
      finance_id: newFinanceId,
      assigned_users: userResult.rows.length,
    });
  } catch (err) {
    await client.query("ROLLBACK");
    console.error("💥 Error creating finance:", err);
    res.status(500).json({ error: err.message });
  } finally {
    client.release();
  }
});

// 🧾 [ADMIN] Lấy tất cả khoản thu (bỏ lọc theo admin)
router.get("/admin", async (req, res) => {
  try {
    const result = await query(`
      SELECT
        f.id,
        f.title,
        f.content,
        f.amount AS price,
        f.type,
        TO_CHAR(f.due_date, 'DD-MM-YYYY') AS due_date,
        TO_CHAR(f.created_at, 'DD-MM-YYYY HH24:MI') AS created_at,
        COUNT(DISTINCT a.apartment_number) FILTER (WHERE f.type != 'chi_phi') AS total_rooms,
        COUNT(DISTINCT CASE WHEN uf.status = 'da_thanh_toan' THEN a.apartment_number END) FILTER (WHERE f.type != 'chi_phi') AS paid_rooms
      FROM finances f
      LEFT JOIN user_finances uf ON f.id = uf.finance_id
      LEFT JOIN user_item ui ON uf.user_id = ui.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      GROUP BY f.id, f.title, f.content, f.amount, f.type, f.due_date, f.created_at
      ORDER BY f.created_at DESC;
    `);

    res.json(result.rows);
  } catch (err) {
    console.error("💥 Error fetching all finances:", err);
    res.status(500).json({
      error: "Lỗi server khi lấy danh sách tất cả khoản thu.",
    });
  }
});


// 🧾 [ADMIN] Lấy danh sách cư dân trong 1 khoản thu
router.get("/:financeId/users", async (req, res) => {
  const { financeId } = req.params;
  try {
    const result = await query(
      `SELECT ui.full_name, uf.user_id, a.apartment_number AS room, uf.status
       FROM user_finances uf
       JOIN user_item ui ON uf.user_id = ui.user_id
       JOIN relationship r ON ui.relationship = r.relationship_id
       JOIN apartment a ON r.apartment_id = a.apartment_id
       WHERE uf.finance_id = $1
       ORDER BY a.apartment_number ASC`,
      [financeId]
    );
    res.json(result.rows);
  } catch (err) {
    console.error("💥 Error fetching users by finance:", err);
    res.status(500).json({ error: "Lỗi server khi lấy danh sách cư dân." });
  }
});

// 🟢 [ADMIN] Cập nhật trạng thái thanh toán theo PHÒNG
router.put("/update-status", async (req, res) => {
  const { room, finance_id, status, admin_id } = req.body;

  if (!room || !finance_id || !admin_id) {
    return res.status(400).json({ error: "Thiếu room, finance_id hoặc admin_id" });
  }

  try {
    const validStatuses = ["chua_thanh_toan", "da_thanh_toan", "da_qua_han"];
    if (!validStatuses.includes(status)) {
      return res.status(400).json({ error: "Trạng thái không hợp lệ." });
    }

    await query(
      `
      UPDATE user_finances uf
      SET status = $1
      FROM user_item ui
      JOIN relationship r ON ui.relationship = r.relationship_id
      JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE uf.user_id = ui.user_id
        AND a.apartment_number = $2
        AND uf.finance_id = $3
      `,
      [status, room, finance_id]
    );

    res.json({
      success: true,
      message: `Cập nhật trạng thái phòng ${room} → ${status}`,
    });
  } catch (err) {
    console.error("💥 Error updating finance by room:", err);
    res.status(500).json({ error: "Lỗi server khi cập nhật trạng thái phòng." });
  }
});

// 🟢 [USER] Thanh toán → cập nhật trạng thái cho toàn phòng
router.put("/user/update-status", async (req, res) => {
  const { user_id, finance_id, status } = req.body;

  if (!user_id || !finance_id || !status) {
    return res.status(400).json({
      error: "Thiếu user_id, finance_id hoặc status.",
    });
  }

  try {
    const validStatuses = ["chua_thanh_toan", "da_thanh_toan", "da_huy"];
    if (!validStatuses.includes(status)) {
      return res.status(400).json({ error: "Trạng thái không hợp lệ." });
    }

    // 1️⃣ Lấy phong (apartment_number) từ user_id
    const roomResult = await query(
      `
      SELECT a.apartment_number
      FROM user_item ui
      JOIN relationship r ON ui.relationship = r.relationship_id
      JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE ui.user_id = $1
      `,
      [user_id]
    );

    if (roomResult.rowCount === 0) {
      return res.status(404).json({
        error: "Không tìm thấy phòng của user.",
      });
    }

    const room = roomResult.rows[0].apartment_number;

    // 2️⃣ Update tất cả user trong phòng này
    const updateResult = await query(
      `
      UPDATE user_finances uf
      SET status = $1
      FROM user_item ui
      JOIN relationship r ON ui.relationship = r.relationship_id
      JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE uf.user_id = ui.user_id
        AND a.apartment_number = $2
        AND uf.finance_id = $3
      `,
      [status, room, finance_id]
    );

    res.json({
      success: true,
      message: `Đã cập nhật trạng thái cho toàn bộ phòng ${room} → ${status}`,
    });

  } catch (err) {
    console.error("💥 Error updating user finance status:", err);
    res.status(500).json({
      error: "Lỗi server khi cập nhật trạng thái thanh toán.",
    });
  }
});

router.post("/trigger-reminder", async (req, res) => {
    try {
        // Gọi hàm logic nhắc nợ ngay lập tức
        await manualCheck();
        res.json({ message: "Đã kích hoạt quét nhắc nợ." });
    } catch (err) {
        res.status(500).json({ error: "Lỗi khi chạy nhắc nợ." });
    }
});

// API THỐNG KÊ
// Revenue: Từ bảng INVOICE (dựa trên paytime)
// Expense: Từ bảng FINANCES (type = chi_phi)
router.get("/statistics", async (req, res) => {
  try {
    const { month, year } = req.query;
    const selectedMonth = (month && month !== '0') ? parseInt(month) : null;
    const selectedYear = year ? parseInt(year) : null;

    // --- QUERY 1: TÍNH TỔNG THU (Từ bảng INVOICE) ---
    const revenueQuery = `
      SELECT COALESCE(SUM(amount), 0) as total_revenue
      FROM invoice
      WHERE
        ($1::int IS NULL OR EXTRACT(MONTH FROM paytime) = $1)
        AND ($2::int IS NULL OR EXTRACT(YEAR FROM paytime) = $2)
    `;

    const revenueResult = await pool.query(revenueQuery, [selectedMonth, selectedYear]);
    const totalRevenue = parseFloat(revenueResult.rows[0].total_revenue);

    // --- QUERY 2: TÍNH TỔNG CHI (Từ bảng FINANCES) ---
    const expenseQuery = `
      SELECT COALESCE(SUM(amount), 0) as total_expense
      FROM finances
      WHERE type = 'chi_phi'
        AND ($1::int IS NULL OR EXTRACT(MONTH FROM due_date) = $1)
        AND ($2::int IS NULL OR EXTRACT(YEAR FROM due_date) = $2)
    `;

    const expenseResult = await pool.query(expenseQuery, [selectedMonth, selectedYear]);
    const totalExpense = parseFloat(expenseResult.rows[0].total_expense);

    res.json({
        revenue: totalRevenue,
        expense: totalExpense
    });

  } catch (err) {
    console.error("Lỗi thống kê:", err);
    res.status(500).json({ error: "Lỗi thống kê tài chính" });
  }
});

// [ADMIN] Tạo hóa đơn Điện/Nước HÀNG LOẠT (Bulk Create) (CÓ GỬI THÔNG BÁO)
router.post("/create-utility-bulk", async (req, res) => {
  const { data, type, month, year } = req.body;
  // data: [{ room: '101', old_index: 100, new_index: 150 }, { room: '102', ... }]

  if (!data || !Array.isArray(data) || data.length === 0) {
      return res.status(400).json({ error: "Dữ liệu không hợp lệ" });
  }

  const client = await pool.connect();
  let successCount = 0;
  let errors = [];

  try {
    await client.query("BEGIN");

    // 1. Lấy bảng giá (Lấy 1 lần dùng chung)
    const ratesRes = await client.query(
        "SELECT * FROM utility_rates WHERE type = $1 ORDER BY min_usage ASC",
        [type]
    );
    const rates = ratesRes.rows;
    const typeName = type === 'electricity' ? "Tiền điện" : "Tiền nước";

    // 2. Duyệt qua từng phòng gửi lên
    for (const item of data) {
        const { room, old_index, new_index } = item;

        // Bỏ qua nếu dữ liệu dòng này sai
        if (!room || new_index <= old_index) {
            errors.push(`Phòng ${room}: Số liệu sai`);
            continue;
        }

        const usage = new_index - old_index;

        // Tính tiền bậc thang
        let totalCost = 0;
        let remainingUsage = usage;
        for (const tier of rates) {
            if (remainingUsage <= 0) break;
            const tierRange = tier.max_usage ? (tier.max_usage - tier.min_usage + 1) : Infinity;
            const usageInThisTier = Math.min(remainingUsage, tierRange);
            totalCost += usageInThisTier * parseFloat(tier.price);
            remainingUsage -= usageInThisTier;
        }

        // Tìm cư dân & TOKEN trong phòng
        const userRes = await client.query(`
            SELECT ui.user_id, u.fcm_token
            FROM user_item ui
            JOIN users u ON ui.user_id = u.user_id
            JOIN relationship r ON ui.relationship = r.relationship_id
            JOIN apartment a ON r.apartment_id = a.apartment_id
            WHERE a.apartment_number = $1
        `, [room]);

        if (userRes.rows.length === 0) {
            errors.push(`Phòng ${room}: Không có cư dân`);
            continue;
        }

        // Tạo khoản thu
        const title = `${typeName} T${month}/${year} - P${room}`;
        const content = `Cũ: ${old_index} | Mới: ${new_index} | Dùng: ${usage}`;

        const financeRes = await client.query(
            `INSERT INTO finances (title, content, amount, type, due_date, created_by)
             VALUES ($1, $2, $3, 'bat_buoc', NOW() + INTERVAL '10 days', 1)
             RETURNING id`,
             [title, content, totalCost]
        );
        const financeId = financeRes.rows[0].id;

        // Gán cho user & Gửi thông báo
        for (const u of userRes.rows) {
            await client.query(
                "INSERT INTO user_finances (user_id, finance_id, status) VALUES ($1, $2, 'chua_thanh_toan') ON CONFLICT DO NOTHING",
                [u.user_id, financeId]
            );

            // 🔥 Gửi thông báo
            if (u.fcm_token) {
                sendNotification(
                    u.fcm_token,
                    `📝 Hóa đơn ${typeName} T${month}`,
                    `Phòng ${room} đã có hóa đơn ${typeName}. Số tiền: ${totalCost.toLocaleString()} VNĐ.`,
                    { type: "finance", id: financeId.toString() }
                );
            }
        }
        successCount++;
    }

    await client.query("COMMIT");

    res.json({
        success: true,
        message: `Đã tạo ${successCount} hóa đơn và gửi thông báo.`,
        errors: errors
    });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error(err);
    res.status(500).json({ error: "Lỗi xử lý hàng loạt" });
  } finally {
    client.release();
  }
});

// ⚙️ [ADMIN] Cập nhật Bảng giá Điện/Nước
router.post("/update-rates", async (req, res) => {
  const { type, tiers } = req.body;
  // type: 'electricity' hoặc 'water'
  // tiers: [{ tier_name: "Bậc 1", min: 0, max: 50, price: 1700 }, ...]

  if (!type || !tiers || !Array.isArray(tiers)) {
      return res.status(400).json({ error: "Dữ liệu không hợp lệ" });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Xóa giá cũ của loại này
    await client.query("DELETE FROM utility_rates WHERE type = $1", [type]);

    // 2. Thêm giá mới
    for (const tier of tiers) {
        await client.query(
            `INSERT INTO utility_rates (type, tier_name, min_usage, max_usage, price)
             VALUES ($1, $2, $3, $4, $5)`,
            [type, tier.tier_name, tier.min, tier.max, tier.price]
        );
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Đã cập nhật bảng giá thành công!" });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error(err);
    res.status(500).json({ error: "Lỗi cập nhật bảng giá" });
  } finally {
    client.release();
  }
});

// ⚙️ [ADMIN] Lấy bảng giá (để hiển thị lên form sửa)
router.get("/utility-rates", async (req, res) => {
    const { type } = req.query;
    try {
        const result = await pool.query(
            "SELECT * FROM utility_rates WHERE type = $1 ORDER BY min_usage ASC",
            [type]
        );
        res.json(result.rows);
    } catch (err) {
        res.status(500).json({ error: "Lỗi lấy dữ liệu" });
    }
});

// ✏️ [ADMIN/ACCOUNTANT] Cập nhật thông tin khoản thu
router.put("/:id", async (req, res) => {
  const { id } = req.params;
  const { title, content, amount, due_date } = req.body;

  // 🔥 SỬA: Chỉ kiểm tra title, không bắt buộc amount nữa
  if (!title) {
    return res.status(400).json({ error: "Tiêu đề là bắt buộc." });
  }

  // 🔥 Xử lý amount: Nếu gửi lên là null, "null", hoặc rỗng "" thì lưu vào DB là NULL
  const finalAmount = (amount === "" || amount === null || amount === "null") ? null : amount;

  try {
    const result = await query(
      `UPDATE finances
       SET title = $1, content = $2, amount = $3, due_date = TO_DATE($4, 'DD-MM-YYYY')
       WHERE id = $5
       RETURNING id`,
      [title, content, finalAmount, due_date, id]
    );

    if (result.rowCount === 0) {
      return res.status(404).json({ error: "Không tìm thấy khoản thu." });
    }

    res.json({ success: true, message: "Cập nhật thành công!" });
  } catch (err) {
    console.error("Lỗi cập nhật:", err);
    res.status(500).json({ error: "Lỗi server khi cập nhật." });
  }
});

// 🗑️ [ADMIN/ACCOUNTANT] Xóa khoản thu
router.delete("/:id", async (req, res) => {
  const { id } = req.params;

  try {
    // Lưu ý: user_finances sẽ tự động xóa nếu bạn đã thiết lập ON DELETE CASCADE trong DB
    // Nếu chưa, hãy chạy: DELETE FROM user_finances WHERE finance_id = $1 trước.

    const result = await query("DELETE FROM finances WHERE id = $1 RETURNING id", [id]);

    if (result.rowCount === 0) {
      return res.status(404).json({ error: "Không tìm thấy khoản thu hoặc đã bị xóa." });
    }

    res.json({ success: true, message: "Đã xóa khoản thu thành công." });
  } catch (err) {
    console.error("Lỗi xóa:", err);
    res.status(500).json({ error: "Không thể xóa (có thể do ràng buộc dữ liệu)." });
  }
});

router.get("/export-excel", async (req, res) => {
  try {
    // 1. Lấy dữ liệu từ DB (Lấy danh sách thu chi + thống kê ai đã nộp)
    const result = await query(`
      SELECT
        f.id, f.title, f.amount, f.type,
        TO_CHAR(f.due_date, 'DD/MM/YYYY') AS due_date,
        COUNT(DISTINCT a.apartment_number) FILTER (WHERE f.type != 'chi_phi') AS total_rooms,
        COUNT(DISTINCT CASE WHEN uf.status = 'da_thanh_toan' THEN a.apartment_number END) FILTER (WHERE f.type != 'chi_phi') AS paid_rooms
      FROM finances f
      LEFT JOIN user_finances uf ON f.id = uf.finance_id
      LEFT JOIN user_item ui ON uf.user_id = ui.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      GROUP BY f.id, f.title, f.amount, f.type, f.due_date
      ORDER BY f.due_date DESC
    `);

    // 2. Tạo Workbook & Worksheet
    const workbook = new ExcelJS.Workbook();
    const worksheet = workbook.addWorksheet('Báo cáo Tài chính');

    // 3. Định nghĩa cột
    worksheet.columns = [
      { header: 'ID', key: 'id', width: 10 },
      { header: 'Tiêu đề', key: 'title', width: 30 },
      { header: 'Loại', key: 'type', width: 15 },
      { header: 'Số tiền (VNĐ)', key: 'amount', width: 20 },
      { header: 'Hạn nộp', key: 'due_date', width: 15 },
      { header: 'Tiến độ', key: 'progress', width: 20 },
      { header: 'Tổng thu được', key: 'total_collected', width: 20 },
    ];

    // 4. Style cho Header (In đậm, nền xanh)
    worksheet.getRow(1).font = { bold: true, color: { argb: 'FFFFFFFF' } };
    worksheet.getRow(1).fill = {
      type: 'pattern',
      pattern: 'solid',
      fgColor: { argb: 'FF009688' } // Màu xanh Teal giống App
    };

    // 5. Thêm dữ liệu
    let grandTotalRevenue = 0;
    let grandTotalExpense = 0;

    result.rows.forEach(row => {
      const isExpense = row.type === 'chi_phi';
      const amount = parseFloat(row.amount || 0);

      // Tính toán thống kê
      const paid = parseInt(row.paid_rooms || 0);
      const total = parseInt(row.total_rooms || 0);
      const collected = isExpense ? amount : (amount * paid);

      if (isExpense) grandTotalExpense += amount;
      else grandTotalRevenue += collected;

      worksheet.addRow({
        id: row.id,
        title: row.title,
        type: isExpense ? 'Chi phí' : 'Khoản thu',
        amount: amount, // Có thể format số sau
        due_date: row.due_date,
        progress: isExpense ? '-' : `${paid}/${total} phòng`,
        total_collected: collected
      });
    });

    // 6. Thêm dòng Tổng kết cuối cùng
    worksheet.addRow({}); // Dòng trống
    const totalRow = worksheet.addRow({
      title: 'TỔNG KẾT:',
      progress: `Thu: ${grandTotalRevenue.toLocaleString()} - Chi: ${grandTotalExpense.toLocaleString()}`,
      total_collected: (grandTotalRevenue - grandTotalExpense)
    });
    totalRow.font = { bold: true, size: 12 };

    // 7. Gửi file về Client
    res.setHeader(
      "Content-Type",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    res.setHeader(
      "Content-Disposition",
      "attachment; filename=" + "BaoCao_TaiChinh.xlsx"
    );

    await workbook.xlsx.write(res);
    res.end();

  } catch (err) {
    console.error("Lỗi xuất Excel:", err);
    res.status(500).send("Lỗi tạo file Excel");
  }
});

export default router;