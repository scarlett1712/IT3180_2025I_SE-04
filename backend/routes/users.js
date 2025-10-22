import express from "express";
import { pool } from "../db.js";
import bcrypt from "bcryptjs";

const router = express.Router();

/* ------------------ LOGIN ------------------ */
router.post("/login", async (req, res) => {
  try {
    const { phone, password } = req.body || {};

    if (!phone || !password) {
      return res.status(400).json({ error: "Thiếu số điện thoại hoặc mật khẩu" });
    }

    // 1️⃣ Tìm user trong bảng users
    const userRes = await pool.query(
      `SELECT u.user_id, u.phone, u.password_hash
       FROM users u
       WHERE u.phone = $1`,
      [phone]
    );

    if (userRes.rows.length === 0)
      return res.status(400).json({ error: "Số điện thoại không tồn tại" });

    const user = userRes.rows[0];

    // 2️⃣ Kiểm tra mật khẩu
    const match = await bcrypt.compare(password, user.password_hash);
    if (!match)
      return res.status(401).json({ error: "Sai mật khẩu" });

    // 3️⃣ JOIN role và user_item
    const infoRes = await pool.query(
      `SELECT
          u.user_id,
          u.phone,
          ur.role_id,
          ui.full_name,
          ui.gender,
          TO_CHAR(ui.dob, 'YYYY-MM-DD') AS dob,
          ui.family_id,
          ui.relationship,
          ui.email,
          ui.is_living
       FROM users u
       LEFT JOIN userrole ur ON u.user_id = ur.user_id
       LEFT JOIN user_item ui ON u.user_id = ui.user_id
       WHERE u.user_id = $1`,
      [user.user_id]
    );

    const info = infoRes.rows[0];

    // 4️⃣ Chuyển role_id sang role name
    const role =
      info.role_id === 2 ? "ADMIN" :
      info.role_id === 1 ? "USER" :
      "USER";

    // 5️⃣ Trả JSON cho app
    res.json({
      message: "Đăng nhập thành công",
      user: {
        user_id: info.user_id,
        phone: info.phone,
        role: role,
        name: info.full_name || info.phone,
        gender: info.gender || "MALE",
        dob: info.dob || "2000-01-01",
        family_id: info.family_id || "FAMILY001",
        relationship: info.relationship || "Thành viên",
        email: info.email,
        is_living: info.is_living ?? true
      }
    });

  } catch (err) {
    console.error("💥 [LOGIN ERROR]", err);
    res.status(500).json({ error: "Lỗi server", details: err.message });
  }
});

/* ------------------ REGISTER ------------------ */
router.post("/register", async (req, res) => {
  try {
    const { phone, password } = req.body || {};

    if (!phone || !password)
      return res.status(400).json({ error: "Thiếu số điện thoại hoặc mật khẩu" });

    // Kiểm tra trùng
    const exist = await pool.query(`SELECT * FROM users WHERE phone = $1`, [phone]);
    if (exist.rows.length > 0)
      return res.status(400).json({ error: "Số điện thoại đã tồn tại" });

    // Tạo tài khoản
    const hash = await bcrypt.hash(password, 10);
    const result = await pool.query(
      `INSERT INTO users (phone, password_hash) VALUES ($1, $2) RETURNING user_id`,
      [phone, hash]
    );
    const userId = result.rows[0].user_id;

    // Thêm role mặc định
    await pool.query(`INSERT INTO userrole (user_id, role_id) VALUES ($1, 1)`, [userId]);

    // Thêm user_item trống
    await pool.query(
      `INSERT INTO user_item (user_id, full_name, gender, dob, family_id, relationship, email, is_living)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [userId, phone, "MALE", "2000-01-01", "FAMILY001", "Thành viên", "default@gmail,com", true]
    );

    res.json({ message: "Đăng ký thành công" });
  } catch (err) {
    console.error("💥 [REGISTER ERROR]", err);
    res.status(500).json({ error: "Lỗi server" });
  }
});

export default router;
