import express from "express";
import { pool } from "../db.js";

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

// 🧾 [ADMIN] Tạo khoản thu theo phòng
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

    // 🧾 Lưu admin tạo khoản thu
    const financeResult = await client.query(
      `
      INSERT INTO finances (title, content, amount, due_date, type, created_by)
      VALUES ($1, $2, $3, TO_DATE($4, 'DD-MM-YYYY'), $5, $6)
      RETURNING id
      `,
      [title, content || "", amount, due_date, finalType, created_by || null]
    );

    const newFinanceId = financeResult.rows[0].id;

    // 🧍‍♂️ Lấy danh sách cư dân thuộc các phòng được chọn
    const userQuery = `
      SELECT ui.user_id
      FROM user_item ui
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

    // 🧾 Gán khoản thu cho từng cư dân
    const insertUserFinance = `
      INSERT INTO user_finances (user_id, finance_id)
      VALUES ($1, $2)
      ON CONFLICT (user_id, finance_id) DO NOTHING
    `;

    for (const { user_id } of userResult.rows) {
      await client.query(insertUserFinance, [user_id, newFinanceId]);
    }

    await client.query("COMMIT");
    res.status(201).json({
      success: true,
      message: "Tạo khoản thu thành công.",
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
        COUNT(DISTINCT a.apartment_number) AS total_rooms,
        COUNT(DISTINCT CASE WHEN uf.status = 'da_thanh_toan' THEN a.apartment_number END) AS paid_rooms
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

export default router;
