import express from "express";
import pkg from "pg";
import cors from "cors";
import dotenv from "dotenv";

dotenv.config();
const { Pool } = pkg;

const app = express();
app.use(cors());
app.use(express.json());

const pool = new Pool({
  host: process.env.PGHOST,
  port: process.env.PGPORT,
  user: process.env.PGUSER,
  password: process.env.PGPASSWORD,
  database: process.env.PGDATABASE,
  ssl: { rejectUnauthorized: false },
});

// ===============================
// TEST API
// ===============================
app.get("/", (req, res) => {
  res.send("ENOTI API hoạt động bình thường ✅");
});

// ===============================
// Lấy tất cả thông báo
// ===============================
app.get("/api/notifications", async (req, res) => {
  try {
    const result = await pool.query(
      "SELECT * FROM notification ORDER BY created_at DESC"
    );
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).send("Lỗi server khi lấy danh sách thông báo");
  }
});

// ===============================
// Lấy thông báo theo ID
// ===============================
app.get("/api/notifications/:id", async (req, res) => {
  try {
    const { id } = req.params;
    const result = await pool.query(
      "SELECT * FROM notification WHERE notification_id = $1",
      [id]
    );
    if (result.rows.length === 0)
      return res.status(404).json({ message: "Không tìm thấy thông báo" });
    res.json(result.rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).send("Lỗi server khi lấy thông báo theo ID");
  }
});

// ===============================
// Thêm thông báo mới
// ===============================
app.post("/api/notifications", async (req, res) => {
  try {
    const { title, content, created_by, type, status, expired_date } = req.body;

    const result = await pool.query(
      `INSERT INTO notification (title, content, created_at, created_by, type, status, expired_date)
       VALUES ($1, $2, NOW(), $3, $4, $5, $6)
       RETURNING *`,
      [title, content, created_by, type, status, expired_date]
    );

    res.status(201).json(result.rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).send("Lỗi khi thêm thông báo mới");
  }
});

// ===============================
// Sửa thông báo theo ID
// ===============================
app.put("/api/notifications/:id", async (req, res) => {
  try {
    const { id } = req.params;
    const { title, content, type, status, expired_date } = req.body;

    const result = await pool.query(
      `UPDATE notification
       SET title = $1, content = $2, type = $3, status = $4, expired_date = $5
       WHERE notification_id = $6
       RETURNING *`,
      [title, content, type, status, expired_date, id]
    );

    if (result.rows.length === 0)
      return res.status(404).json({ message: "Không tìm thấy thông báo để cập nhật" });

    res.json(result.rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).send("Lỗi khi cập nhật thông báo");
  }
});

// ===============================
// Xóa thông báo theo ID
// ===============================
app.delete("/api/notifications/:id", async (req, res) => {
  try {
    const { id } = req.params;
    const result = await pool.query(
      "DELETE FROM notification WHERE notification_id = $1 RETURNING *",
      [id]
    );

    if (result.rows.length === 0)
      return res.status(404).json({ message: "Không tìm thấy thông báo để xóa" });

    res.json({ message: "Đã xóa thành công", deleted: result.rows[0] });
  } catch (err) {
    console.error(err);
    res.status(500).send("Lỗi khi xóa thông báo");
  }
});

// ===============================
// Khởi động server
// ===============================
const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
  console.log(`Server đang chạy tại http://localhost:${PORT}`);
});
