import express from "express";
import { pool } from "../db.js";
import admin from "firebase-admin"; // 🔥 Import Firebase Admin để verify token thủ công
import ExcelJS from 'exceljs';      // 🔥 Import thư viện xuất Excel
import { sendNotification } from "../utils/firebaseHelper.js";

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

    // Bảng giá điện nước
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

    console.log("✅ Finance tables verified.");
  } catch (err) {
    console.error("💥 Error creating finance tables:", err);
  }
};

// ==================================================================
// 🖨️ [EXCEL] Xuất báo cáo tài chính (FIX LỖI DOWNLOAD MANAGER)
// ==================================================================
router.get("/export-excel", async (req, res) => {
  try {
    // 1️⃣ XÁC THỰC THỦ CÔNG (Lấy token từ URL query hoặc Header)
    let token = req.query.token;
    if (!token && req.headers.authorization) {
        token = req.headers.authorization.split(" ")[1];
    }

    if (!token) {
        return res.status(401).send("Thiếu Token xác thực");
    }

    try {
        await admin.auth().verifyIdToken(token);
    } catch (e) {
        return res.status(403).send("Token không hợp lệ hoặc đã hết hạn");
    }

    // 2️⃣ Lấy dữ liệu từ DB
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

    // 3️⃣ Tạo Workbook & Worksheet
    const workbook = new ExcelJS.Workbook();
    const worksheet = workbook.addWorksheet('Báo cáo Tài chính');

    worksheet.columns = [
      { header: 'ID', key: 'id', width: 10 },
      { header: 'Tiêu đề', key: 'title', width: 35 },
      { header: 'Loại', key: 'type', width: 15 },
      { header: 'Số tiền (VNĐ)', key: 'amount', width: 20 },
      { header: 'Hạn nộp', key: 'due_date', width: 15 },
      { header: 'Tiến độ', key: 'progress', width: 20 },
      { header: 'Tổng thu thực tế', key: 'total_collected', width: 25 },
    ];

    // Style Header
    const headerRow = worksheet.getRow(1);
    headerRow.font = { bold: true, color: { argb: 'FFFFFFFF' } };
    headerRow.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF009688' } };

    let grandTotalRevenue = 0;
    let grandTotalExpense = 0;

    result.rows.forEach(row => {
      const isExpense = row.type === 'chi_phi';
      const amount = parseFloat(row.amount || 0);
      const paid = parseInt(row.paid_rooms || 0);
      const total = parseInt(row.total_rooms || 0);

      // Nếu là chi phí -> Tính vào chi. Nếu là khoản thu -> Tính tiền dựa trên số người đã đóng.
      const collected = isExpense ? amount : (amount * paid);

      if (isExpense) grandTotalExpense += amount;
      else grandTotalRevenue += collected;

      worksheet.addRow({
        id: row.id,
        title: row.title,
        type: isExpense ? 'Chi phí' : 'Khoản thu',
        amount: amount,
        due_date: row.due_date,
        progress: isExpense ? '-' : `${paid}/${total} phòng`,
        total_collected: collected
      });
    });

    // Dòng tổng kết
    worksheet.addRow({});
    const totalRow = worksheet.addRow({
      title: 'TỔNG KẾT:',
      progress: `Thu: ${grandTotalRevenue.toLocaleString('vi-VN')} - Chi: ${grandTotalExpense.toLocaleString('vi-VN')}`,
      total_collected: (grandTotalRevenue - grandTotalExpense)
    });
    totalRow.font = { bold: true, size: 12 };

    // 4️⃣ Gửi file
    res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    res.setHeader("Content-Disposition", "attachment; filename=" + "BaoCao_TaiChinh.xlsx");

    await workbook.xlsx.write(res);
    res.end();

  } catch (err) {
    console.error("Lỗi xuất Excel:", err);
    res.status(500).send("Lỗi Server: " + err.message);
  }
});

// ==================================================================
// ✏️ [EDIT] Cập nhật khoản thu (Cho phép NULL số tiền)
// ==================================================================
router.put("/:id", async (req, res) => {
  const { id } = req.params;
  const { title, content, amount, due_date } = req.body;

  if (!title) {
    return res.status(400).json({ error: "Tiêu đề là bắt buộc." });
  }

  // Nếu amount là chuỗi rỗng hoặc "null", gán là null
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

// ==================================================================
// 🗑️ [DELETE] Xóa khoản thu
// ==================================================================
router.delete("/:id", async (req, res) => {
  const { id } = req.params;
  try {
    const result = await query("DELETE FROM finances WHERE id = $1 RETURNING id", [id]);

    if (result.rowCount === 0) {
      return res.status(404).json({ error: "Không tìm thấy khoản thu hoặc đã bị xóa." });
    }
    res.json({ success: true, message: "Đã xóa khoản thu thành công." });
  } catch (err) {
    console.error("Lỗi xóa:", err);
    res.status(500).json({ error: "Không thể xóa (lỗi ràng buộc dữ liệu)." });
  }
});

// ==================================================================
// 🟢 [GET] Các API lấy dữ liệu cơ bản
// ==================================================================

// Lấy toàn bộ (Admin)
router.get("/all", async (req, res) => {
  try {
    const result = await query(`
      SELECT id, title, content, amount, type,
             TO_CHAR(due_date, 'YYYY-MM-DD') AS due_date,
             TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI') AS created_at, created_by
      FROM finances ORDER BY due_date ASC NULLS LAST
    `);
    res.json(result.rows);
  } catch (err) { res.status(500).json({ error: "Lỗi server." }); }
});

// Lấy cho User
router.get("/user/:userId", async (req, res) => {
  const { userId } = req.params;
  try {
    const result = await query(`
      SELECT f.id, f.title, f.content, f.amount AS price, f.type,
             TO_CHAR(f.due_date, 'DD-MM-YYYY') AS due_date, uf.status
      FROM finances f
      JOIN user_finances uf ON f.id = uf.finance_id
      WHERE uf.user_id = $1 AND f.type != 'chi_phi'
      ORDER BY f.due_date ASC NULLS LAST;
    `, [userId]);
    res.json(result.rows);
  } catch (err) { res.status(500).json({ error: "Lỗi server." }); }
});

// Lấy danh sách cư dân trong 1 khoản thu
router.get("/:financeId/users", async (req, res) => {
  const { financeId } = req.params;
  try {
    const result = await query(`
       SELECT ui.full_name, uf.user_id, a.apartment_number AS room, uf.status
       FROM user_finances uf
       JOIN user_item ui ON uf.user_id = ui.user_id
       JOIN relationship r ON ui.relationship = r.relationship_id
       JOIN apartment a ON r.apartment_id = a.apartment_id
       WHERE uf.finance_id = $1
       ORDER BY a.apartment_number ASC`, [financeId]);
    res.json(result.rows);
  } catch (err) { res.status(500).json({ error: "Lỗi server." }); }
});

// Lấy danh sách Admin (Tổng quan)
router.get("/admin", async (req, res) => {
  try {
    const result = await query(`
      SELECT f.id, f.title, f.content, f.amount AS price, f.type,
        TO_CHAR(f.due_date, 'DD-MM-YYYY') AS due_date,
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
  } catch (err) { res.status(500).json({ error: "Lỗi server." }); }
});

// ==================================================================
// 🧾 [CREATE] Tạo khoản thu thường (Thủ công)
// ==================================================================
router.post("/create", async (req, res) => {
  const { title, content, amount, due_date, target_rooms, type, created_by } = req.body;

  if (!title || !target_rooms || !Array.isArray(target_rooms)) {
    return res.status(400).json({ error: "Thiếu trường bắt buộc." });
  }

  const finalType = type && type.trim() !== "" ? type : "Bắt buộc";
  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    // Tạo khoản thu
    const financeResult = await client.query(
      `INSERT INTO finances (title, content, amount, due_date, type, created_by)
       VALUES ($1, $2, $3, TO_DATE($4, 'DD-MM-YYYY'), $5, $6) RETURNING id`,
      [title, content || "", amount, due_date, finalType, created_by || null]
    );
    const newFinanceId = financeResult.rows[0].id;

    // Lấy user thuộc phòng
    const userResult = await client.query(`
      SELECT ui.user_id, u.fcm_token
      FROM user_item ui
      JOIN users u ON ui.user_id = u.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE a.apartment_number = ANY($1)
    `, [target_rooms]);

    if (userResult.rows.length === 0) {
      await client.query("ROLLBACK");
      return res.status(404).json({ error: "Không tìm thấy cư dân." });
    }

    for (const row of userResult.rows) {
      await client.query(
        `INSERT INTO user_finances (user_id, finance_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
        [row.user_id, newFinanceId]
      );
      if (row.fcm_token) {
          sendNotification(row.fcm_token, "🔔 Thông báo phí mới",
              `Bạn có khoản thu mới: "${title}".`, { type: "finance", id: newFinanceId.toString() });
      }
    }

    await client.query("COMMIT");
    res.status(201).json({ success: true, message: "Tạo thành công." });
  } catch (err) {
    await client.query("ROLLBACK");
    res.status(500).json({ error: err.message });
  } finally { client.release(); }
});

// ==================================================================
// 🧾 [BULK CREATE] Tạo Điện/Nước Hàng Loạt (CÓ CHECK TRÙNG)
// ==================================================================
router.post("/create-utility-bulk", async (req, res) => {
  const { data, type, month, year } = req.body;
  if (!data || !Array.isArray(data) || data.length === 0) return res.status(400).json({ error: "Dữ liệu sai" });

  const client = await pool.connect();
  let successCount = 0;
  let errors = [];

  try {
    await client.query("BEGIN");

    const ratesRes = await client.query("SELECT * FROM utility_rates WHERE type = $1 ORDER BY min_usage ASC", [type]);
    const rates = ratesRes.rows;
    const typeName = type === 'electricity' ? "Tiền điện" : "Tiền nước";

    for (const item of data) {
        const { room, old_index, new_index } = item;
        if (!room || new_index <= old_index) {
            errors.push(`Phòng ${room}: Số liệu sai`);
            continue;
        }

        // 🔥 CHECK TRÙNG LẶP
        const titlePattern = `${typeName} T${month}/${year} - P${room}`;
        const checkExist = await client.query("SELECT id FROM finances WHERE title = $1", [titlePattern]);

        if (checkExist.rows.length > 0) {
            errors.push(`Phòng ${room}: Đã có hóa đơn tháng ${month}/${year}`);
            continue;
        }

        // Tính tiền bậc thang
        const usage = new_index - old_index;
        let totalCost = 0;
        let remainingUsage = usage;
        for (const tier of rates) {
            if (remainingUsage <= 0) break;
            const tierRange = tier.max_usage ? (tier.max_usage - tier.min_usage + 1) : Infinity;
            const usageInThisTier = Math.min(remainingUsage, tierRange);
            totalCost += usageInThisTier * parseFloat(tier.price);
            remainingUsage -= usageInThisTier;
        }

        const userRes = await client.query(`
            SELECT ui.user_id, u.fcm_token FROM user_item ui
            JOIN users u ON ui.user_id = u.user_id
            JOIN relationship r ON ui.relationship = r.relationship_id
            JOIN apartment a ON r.apartment_id = a.apartment_id
            WHERE a.apartment_number = $1
        `, [room]);

        if (userRes.rows.length === 0) {
            errors.push(`Phòng ${room}: Không có cư dân`);
            continue;
        }

        const financeRes = await client.query(
            `INSERT INTO finances (title, content, amount, type, due_date, created_by)
             VALUES ($1, $2, $3, 'bat_buoc', NOW() + INTERVAL '10 days', 1) RETURNING id`,
             [titlePattern, `Cũ: ${old_index} | Mới: ${new_index} | Dùng: ${usage}`, totalCost]
        );
        const financeId = financeRes.rows[0].id;

        for (const u of userRes.rows) {
            await client.query("INSERT INTO user_finances (user_id, finance_id, status) VALUES ($1, $2, 'chua_thanh_toan') ON CONFLICT DO NOTHING", [u.user_id, financeId]);
            if (u.fcm_token) sendNotification(u.fcm_token, `📝 Hóa đơn ${typeName}`, `Phòng ${room} có hóa đơn ${totalCost.toLocaleString()}đ.`, { type: "finance", id: financeId.toString() });
        }
        successCount++;
    }

    await client.query("COMMIT");
    res.json({ success: true, message: `Đã tạo ${successCount} hóa đơn.`, errors: errors });

  } catch (err) {
    await client.query("ROLLBACK");
    res.status(500).json({ error: "Lỗi xử lý hàng loạt" });
  } finally { client.release(); }
});

// ==================================================================
// 🟢 [STATUS] Cập nhật trạng thái
// ==================================================================

// Admin/Kế toán cập nhật
router.put("/update-status", async (req, res) => {
  const { room, finance_id, status } = req.body;
  try {
    await query(`
      UPDATE user_finances uf SET status = $1
      FROM user_item ui JOIN relationship r ON ui.relationship = r.relationship_id
      JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE uf.user_id = ui.user_id AND a.apartment_number = $2 AND uf.finance_id = $3
    `, [status, room, finance_id]);
    res.json({ success: true, message: "Cập nhật thành công" });
  } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

// User cập nhật (Thanh toán)
router.put("/user/update-status", async (req, res) => {
  const { user_id, finance_id, status } = req.body;
  try {
    const roomRes = await query(`
      SELECT a.apartment_number FROM user_item ui
      JOIN relationship r ON ui.relationship = r.relationship_id
      JOIN apartment a ON r.apartment_id = a.apartment_id WHERE ui.user_id = $1
    `, [user_id]);

    if (roomRes.rowCount === 0) return res.status(404).json({ error: "Không tìm thấy phòng" });
    const room = roomRes.rows[0].apartment_number;

    await query(`
      UPDATE user_finances uf SET status = $1
      FROM user_item ui JOIN relationship r ON ui.relationship = r.relationship_id
      JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE uf.user_id = ui.user_id AND a.apartment_number = $2 AND uf.finance_id = $3
    `, [status, room, finance_id]);

    res.json({ success: true, message: "Đã cập nhật thanh toán" });
  } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

// ==================================================================
// 📊 [STATS] Thống kê & Bảng giá
// ==================================================================

router.get("/statistics", async (req, res) => {
  try {
    const { month, year } = req.query;
    const m = (month && month !== '0') ? parseInt(month) : null;
    const y = year ? parseInt(year) : null;

    const revRes = await query(`
      SELECT COALESCE(SUM(amount), 0) as val FROM invoice
      WHERE ($1::int IS NULL OR EXTRACT(MONTH FROM paytime) = $1)
      AND ($2::int IS NULL OR EXTRACT(YEAR FROM paytime) = $2)
    `, [m, y]);

    const expRes = await query(`
      SELECT COALESCE(SUM(amount), 0) as val FROM finances
      WHERE type = 'chi_phi'
      AND ($1::int IS NULL OR EXTRACT(MONTH FROM due_date) = $1)
      AND ($2::int IS NULL OR EXTRACT(YEAR FROM due_date) = $2)
    `, [m, y]);

    res.json({ revenue: parseFloat(revRes.rows[0].val), expense: parseFloat(expRes.rows[0].val) });
  } catch (err) { res.status(500).json({ error: "Lỗi thống kê" }); }
});

router.post("/trigger-reminder", async (req, res) => {
    res.json({ message: "Đã kích hoạt quét nhắc nợ." });
});

router.post("/update-rates", async (req, res) => {
  const { type, tiers } = req.body;
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query("DELETE FROM utility_rates WHERE type = $1", [type]);
    for (const tier of tiers) {
        await client.query("INSERT INTO utility_rates (type, tier_name, min_usage, max_usage, price) VALUES ($1, $2, $3, $4, $5)",
            [type, tier.tier_name, tier.min, tier.max, tier.price]);
    }
    await client.query("COMMIT");
    res.json({ success: true });
  } catch (e) { await client.query("ROLLBACK"); res.status(500).json({ error: "Lỗi" }); } finally { client.release(); }
});

router.get("/utility-rates", async (req, res) => {
    try {
        const result = await pool.query("SELECT * FROM utility_rates WHERE type = $1 ORDER BY min_usage ASC", [req.query.type]);
        res.json(result.rows);
    } catch (e) { res.status(500).json({ error: "Lỗi" }); }
});

export default router;