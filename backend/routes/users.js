import express from "express";
import { pool } from "../db.js";
import bcrypt from "bcryptjs";

const router = express.Router();

/* ==========================================================
   🟢 API: Đăng nhập người dùng (User / Admin)
========================================================== */
router.post("/login", async (req, res) => {
  try {
    const { phone, password } = req.body || {};

    if (!phone || !password) {
      return res.status(400).json({ error: "Thiếu số điện thoại hoặc mật khẩu." });
    }

    // Tìm user trong DB
    const userRes = await pool.query(
      `SELECT u.user_id, u.phone, u.password_hash, ur.role_id
       FROM users u
       LEFT JOIN userrole ur ON u.user_id = ur.user_id
       WHERE u.phone = $1`,
      [phone]
    );

    if (userRes.rows.length === 0) {
      return res.status(404).json({ error: "Số điện thoại không tồn tại." });
    }

    const user = userRes.rows[0];
    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) {
      return res.status(401).json({ error: "Sai mật khẩu." });
    }

    // Lấy thêm thông tin người dùng
    const infoRes = await pool.query(
      `SELECT ui.full_name, ui.gender, TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob, ui.email
       FROM user_item ui
       WHERE ui.user_id = $1`,
      [user.user_id]
    );

    const info = infoRes.rows.length > 0 ? infoRes.rows[0] : {};
    const role = user.role_id === 2 ? "ADMIN" : "USER";

    return res.json({
      message: "Đăng nhập thành công",
      user: {
        id: user.user_id.toString(),
        phone: user.phone,
        role: role,
        name: info.full_name || user.phone,
        gender: info.gender || "Khác", // 🟢 Giới tính tiếng Việt
        dob: info.dob || "01-01-2000",
        email: info.email || "",
      },
    });
  } catch (err) {
    console.error("💥 [LOGIN ERROR]", err);
    return res.status(500).json({ error: "Lỗi server khi đăng nhập." });
  }
});

/* ==========================================================
   🟢 API: Tạo tài khoản Ban Quản Trị (Admin)
========================================================== */
router.post("/create_admin", async (req, res) => {
  const client = await pool.connect();

  try {
    const { phone, password, full_name, gender, dob, email } = req.body || {};

    if (!phone || !password || !full_name) {
      return res.status(400).json({ error: "Thiếu thông tin bắt buộc." });
    }

    await client.query("BEGIN");

    // 1️⃣ Kiểm tra trùng số điện thoại
    const exists = await client.query("SELECT 1 FROM users WHERE phone = $1", [phone]);
    if (exists.rows.length > 0) {
      await client.query("ROLLBACK");
      return res.status(400).json({ error: "Số điện thoại đã tồn tại." });
    }

    // 2️⃣ Hash mật khẩu
    const passwordHash = await bcrypt.hash(password, 10);

    // 3️⃣ Thêm vào bảng users
    const insertUser = await client.query(
      `INSERT INTO users (password_hash, phone, created_at, updated_at)
       VALUES ($1, $2, NOW(), NOW()) RETURNING user_id`,
      [passwordHash, phone]
    );
    const user_id = insertUser.rows[0].user_id;

    // 4️⃣ Thêm vào user_item (🟢 Lưu giới tính tiếng Việt)
    await client.query(
      `INSERT INTO user_item (user_id, full_name, gender, dob, email, is_living)
       VALUES ($1, $2, $3, $4, $5, TRUE)`,
      [user_id, full_name, gender || "Khác", dob || null, email || null]
    );

    // 5️⃣ Gán quyền ADMIN
    await client.query(`INSERT INTO userrole (user_id, role_id) VALUES ($1, 2)`, [user_id]);

    await client.query("COMMIT");

    return res.json({
      message: "✅ Tạo tài khoản Ban Quản Trị thành công!",
      user_id,
      phone,
    });
  } catch (err) {
    await client.query("ROLLBACK");
    console.error("💥 [CREATE ADMIN ERROR]", err);
    return res.status(500).json({ error: "Lỗi server khi tạo tài khoản admin." });
  } finally {
    client.release();
  }
});

export default router;
