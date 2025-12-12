import express from "express";
import { pool } from "../db.js";
import admin from "firebase-admin"; // Dùng để verify token
import ExcelJS from 'exceljs';      // Dùng để xuất Excel
import { sendNotification } from "../utils/firebaseHelper.js";

const router = express.Router();

// 🧩 Helper query
const query = (text, params) => pool.query(text, params);

// ==================================================================
// 🧱 1. KHỞI TẠO BẢNG (Giữ nguyên)
// ==================================================================
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
// 🟢 2. CẬP NHẬT TRẠNG THÁI (ĐÃ SỬA LẠI ĐỂ LOG CHI TIẾT)
// ==================================================================

// [ADMIN/ACCOUNTANT] Cập nhật trạng thái thanh toán theo PHÒNG
router.put("/update-status", async (req, res) => {
  // Nhận đủ tham số từ Android
  const { room, finance_id, status, admin_id } = req.body;

  // 🔥 Log để debug xem App có gửi lên không
  console.log(`[UPDATE] Room: ${room}, FinanceID: ${finance_id}, Status: ${status}, By: ${admin_id}`);

  if (!room || !finance_id || !status) {
    console.error("❌ Thiếu tham số update-status");
    return res.status(400).json({ error: "Thiếu room, finance_id hoặc status" });
  }

  try {
    const validStatuses = ["chua_thanh_toan", "da_thanh_toan", "da_qua_han"];
    if (!validStatuses.includes(status)) {
      return res.status(400).json({ error: "Trạng thái không hợp lệ (phải là: chua_thanh_toan / da_thanh_toan)" });
    }

    // Câu lệnh SQL update dựa trên join bảng apartment và user_item
    const result = await query(
      `
      UPDATE user_finances uf
      SET status = $1
      FROM user_item ui
      JOIN relationship r ON ui.relationship = r.relationship_id
      JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE uf.user_id = ui.user_id
        AND a.apartment_number = $2
        AND uf.finance_id = $3
      RETURNING uf.id
      `,
      [status, room, finance_id]
    );

    // 🔥 Kiểm tra xem có dòng nào được update không
    if (result.rowCount === 0) {
        console.warn(`⚠️ Không tìm thấy user nào trong phòng ${room} có khoản thu ${finance_id}`);
        // Vẫn trả về 200 success nhưng kèm message cảnh báo, hoặc trả 404 tùy logic app
        return res.json({ success: false, message: "Không tìm thấy dữ liệu để cập nhật (Check lại Phòng/ID)" });
    }

    console.log(`✅ Đã cập nhật ${result.rowCount} dòng cho phòng ${room}`);
    res.json({
      success: true,
      message: `Cập nhật trạng thái phòng ${room} → ${status}`,
      updated_count: result.rowCount
    });

  } catch (err) {
    console.error("💥 Error updating finance by room:", err);
    res.status(500).json({ error: "Lỗi server khi cập nhật trạng thái phòng." });
  }
});

// [USER] Thanh toán → cập nhật trạng thái cho toàn phòng
router.put("/user/update-status", async (req, res) => {
  const { user_id, finance_id, status } = req.body;

  if (!user_id || !finance_id || !status) {
    return res.status(400).json({ error: "Thiếu thông tin." });
  }

  try {
    // 1. Tìm phòng của user
    const roomRes = await query(`
      SELECT a.apartment_number FROM user_item ui
      JOIN relationship r ON ui.relationship = r.relationship_id
      JOIN apartment a ON r.apartment_id = a.apartment_id WHERE ui.user_id = $1
    `, [user_id]);

    if (roomRes.rowCount === 0) return res.status(404).json({ error: "Không tìm thấy phòng" });
    const room = roomRes.rows[0].apartment_number;

    // 2. Update status
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
// 🖨️ 3. XUẤT EXCEL (ĐÃ FIX TOKEN URL)
// ==================================================================
router.get("/export-excel", async (req, res) => {
  try {
    // Lấy token từ URL (do Android DownloadManager hay làm mất Header)
    let token = req.query.token;
    if (!token && req.headers.authorization) {
        token = req.headers.authorization.split(" ")[1];
    }

    if (!token) return res.status(401).send("Thiếu Token xác thực");

    try {
        await admin.auth().verifyIdToken(token);
    } catch (e) {
        return res.status(403).send("Token không hợp lệ hoặc đã hết hạn");
    }

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

    worksheet.addRow({});
    const totalRow = worksheet.addRow({
      title: 'TỔNG KẾT:',
      progress: `Thu: ${grandTotalRevenue.toLocaleString('vi-VN')} - Chi: ${grandTotalExpense.toLocaleString('vi-VN')}`,
      total_collected: (grandTotalRevenue - grandTotalExpense)
    });
    totalRow.font = { bold: true, size: 12 };

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
// ✏️ 4. SỬA / XÓA / TẠO (Giữ nguyên)
// ==================================================================

router.put("/:id", async (req, res) => {
  const { id } = req.params;
  const { title, content, amount, due_date } = req.body;

  if (!title) return res.status(400).json({ error: "Tiêu đề là bắt buộc." });
  const finalAmount = (amount === "" || amount === null || amount === "null") ? null : amount;

  try {
    const result = await query(
      `UPDATE finances SET title=$1, content=$2, amount=$3, due_date=TO_DATE($4, 'DD-MM-YYYY') WHERE id=$5 RETURNING id`,
      [title, content, finalAmount, due_date, id]
    );
    if (result.rowCount === 0) return res.status(404).json({ error: "Không tìm thấy" });
    res.json({ success: true });
  } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

router.delete("/:id", async (req, res) => {
  const { id } = req.params;
  try {
    const result = await query("DELETE FROM finances WHERE id = $1 RETURNING id", [id]);
    if (result.rowCount === 0) return res.status(404).json({ error: "Không tìm thấy" });
    res.json({ success: true });
  } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

// ==================================================================
// 🔍 5. CÁC API GET (Giữ nguyên)
// ==================================================================

router.get("/all", async (req, res) => {
  try {
    const result = await query(`SELECT id, title, content, amount, type, TO_CHAR(due_date, 'YYYY-MM-DD') AS due_date, created_by FROM finances ORDER BY due_date ASC NULLS LAST`);
    res.json(result.rows);
  } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

router.get("/user/:userId", async (req, res) => {
  const { userId } = req.params;
  try {
    const result = await query(`
      SELECT f.id, f.title, f.content, f.amount AS price, f.type, TO_CHAR(f.due_date, 'DD-MM-YYYY') AS due_date, uf.status
      FROM finances f JOIN user_finances uf ON f.id = uf.finance_id
      WHERE uf.user_id = $1 AND f.type != 'chi_phi' ORDER BY f.due_date ASC NULLS LAST`, [userId]);
    res.json(result.rows);
  } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

router.get("/:financeId/users", async (req, res) => {
  const { financeId } = req.params;
  try {
    const result = await query(`
       SELECT ui.full_name, uf.user_id, a.apartment_number AS room, uf.status
       FROM user_finances uf
       JOIN user_item ui ON uf.user_id = ui.user_id
       JOIN relationship r ON ui.relationship = r.relationship_id
       JOIN apartment a ON r.apartment_id = a.apartment_id
       WHERE uf.finance_id = $1 ORDER BY a.apartment_number ASC`, [financeId]);
    res.json(result.rows);
  } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

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
  } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

// ==================================================================
// 🧾 6. CÁC API TẠO (CREATE & BULK) (Giữ nguyên)
// ==================================================================

router.post("/create", async (req, res) => {
  const { title, content, amount, due_date, target_rooms, type, created_by } = req.body;
  if (!title || !target_rooms) return res.status(400).json({ error: "Thiếu dữ liệu" });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const financeResult = await client.query(
      `INSERT INTO finances (title, content, amount, due_date, type, created_by)
       VALUES ($1, $2, $3, TO_DATE($4, 'DD-MM-YYYY'), $5, $6) RETURNING id`,
      [title, content || "", amount, due_date, type || "Bắt buộc", created_by]
    );
    const newId = financeResult.rows[0].id;

    const userResult = await client.query(`
      SELECT ui.user_id, u.fcm_token FROM user_item ui
      JOIN users u ON ui.user_id = u.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE a.apartment_number = ANY($1)
    `, [target_rooms]);

    for (const row of userResult.rows) {
      await client.query(`INSERT INTO user_finances (user_id, finance_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`, [row.user_id, newId]);
      if (row.fcm_token) sendNotification(row.fcm_token, "🔔 Phí mới", `Khoản thu mới: "${title}"`, { type: "finance", id: newId.toString() });
    }
    await client.query("COMMIT");
    res.status(201).json({ success: true });
  } catch (err) { await client.query("ROLLBACK"); res.status(500).json({ error: err.message }); } finally { client.release(); }
});

router.post("/create-utility-bulk", async (req, res) => {
  const { data, type, month, year } = req.body;
  if (!data || data.length === 0) return res.status(400).json({ error: "Dữ liệu sai" });

  const client = await pool.connect();
  let successCount = 0, errors = [];
  try {
    await client.query("BEGIN");
    const ratesRes = await client.query("SELECT * FROM utility_rates WHERE type = $1 ORDER BY min_usage ASC", [type]);
    const rates = ratesRes.rows;
    const typeName = type === 'electricity' ? "Tiền điện" : "Tiền nước";

    for (const item of data) {
        const { room, old_index, new_index } = item;
        if (!room || new_index <= old_index) { errors.push(`P${room}: Số liệu sai`); continue; }

        const titlePattern = `${typeName} T${month}/${year} - P${room}`;
        const checkExist = await client.query("SELECT id FROM finances WHERE title = $1", [titlePattern]);
        if (checkExist.rows.length > 0) { errors.push(`P${room}: Đã có hóa đơn`); continue; }

        const usage = new_index - old_index;
        let totalCost = 0, remaining = usage;
        for (const tier of rates) {
            if (remaining <= 0) break;
            const range = tier.max_usage ? (tier.max_usage - tier.min_usage + 1) : Infinity;
            const used = Math.min(remaining, range);
            totalCost += used * parseFloat(tier.price);
            remaining -= used;
        }

        const userRes = await client.query(`
            SELECT ui.user_id, u.fcm_token FROM user_item ui
            JOIN users u ON ui.user_id = u.user_id
            JOIN relationship r ON ui.relationship = r.relationship_id
            JOIN apartment a ON r.apartment_id = a.apartment_id
            WHERE a.apartment_number = $1
        `, [room]);

        if (userRes.rows.length === 0) { errors.push(`P${room}: Vắng chủ`); continue; }

        const fRes = await client.query(
            `INSERT INTO finances (title, content, amount, type, due_date, created_by)
             VALUES ($1, $2, $3, 'bat_buoc', NOW() + INTERVAL '10 days', 1) RETURNING id`,
             [titlePattern, `Cũ: ${old_index} | Mới: ${new_index} | Dùng: ${usage}`, totalCost]
        );
        const fId = fRes.rows[0].id;

        for (const u of userRes.rows) {
            await client.query("INSERT INTO user_finances (user_id, finance_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", [u.user_id, fId]);
            if (u.fcm_token) sendNotification(u.fcm_token, `📝 ${typeName}`, `P${room}: ${totalCost.toLocaleString()}đ`, { type: "finance", id: fId.toString() });
        }
        successCount++;
    }
    await client.query("COMMIT");
    res.json({ success: true, message: `Đã tạo ${successCount} hóa đơn.`, errors });
  } catch (err) { await client.query("ROLLBACK"); res.status(500).json({ error: "Lỗi server" }); } finally { client.release(); }
});

// ==================================================================
// ⚙️ 7. KHÁC (STATS, RATES)
// ==================================================================

router.get("/statistics", async (req, res) => {
  try {
    const { month, year } = req.query;
    const m = (month && month !== '0') ? parseInt(month) : null;
    const y = year ? parseInt(year) : null;
    const revRes = await query(`SELECT COALESCE(SUM(amount), 0) as val FROM invoice WHERE ($1::int IS NULL OR EXTRACT(MONTH FROM paytime)=$1) AND ($2::int IS NULL OR EXTRACT(YEAR FROM paytime)=$2)`, [m, y]);
    const expRes = await query(`SELECT COALESCE(SUM(amount), 0) as val FROM finances WHERE type='chi_phi' AND ($1::int IS NULL OR EXTRACT(MONTH FROM due_date)=$1) AND ($2::int IS NULL OR EXTRACT(YEAR FROM due_date)=$2)`, [m, y]);
    res.json({ revenue: parseFloat(revRes.rows[0].val), expense: parseFloat(expRes.rows[0].val) });
  } catch (err) { res.status(500).json({ error: "Lỗi" }); }
});

router.post("/update-rates", async (req, res) => {
  const { type, tiers } = req.body;
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query("DELETE FROM utility_rates WHERE type = $1", [type]);
    for (const t of tiers) await client.query("INSERT INTO utility_rates (type, tier_name, min_usage, max_usage, price) VALUES ($1, $2, $3, $4, $5)", [type, t.tier_name, t.min, t.max, t.price]);
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