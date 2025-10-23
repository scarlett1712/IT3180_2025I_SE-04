import express from "express";
import { pool } from "../db.js";
import bcrypt from "bcryptjs";

const router = express.Router();

router.post("/login", async (req, res) => {
  try {
    const { phone, password } = req.body || {};

    if (!phone || !password)
      return res.status(400).json({ error: "Thiếu số điện thoại hoặc mật khẩu" });

    // 1️⃣ Tìm user
    const userRes = await pool.query(
      `SELECT user_id, phone, password_hash FROM users WHERE phone = $1`,
      [phone]
    );

    if (userRes.rows.length === 0)
      return res.status(400).json({ error: "Số điện thoại không tồn tại" });

    const user = userRes.rows[0];

    // 2️⃣ Kiểm tra mật khẩu
    const match = await bcrypt.compare(password, user.password_hash);
    if (!match)
      return res.status(401).json({ error: "Sai mật khẩu" });

    // 3️⃣ Lấy thông tin đầy đủ
    const infoRes = await pool.query(
      `SELECT
          u.user_id,
          u.phone,
          ur.role_id,
          ui.full_name,
          ui.gender,
          TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob,
          a.apartment_number,
          r.relationship_with_the_head_of_household,
          ui.email,
          ui.is_living,
          ui.avatar_path
       FROM users u
       LEFT JOIN userrole ur ON u.user_id = ur.user_id
       LEFT JOIN user_item ui ON u.user_id = ui.user_id
       LEFT JOIN relationship r ON ui.relationship = r.relationship_id
       LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
       WHERE u.user_id = $1`,
      [user.user_id]
    );

    if (infoRes.rows.length === 0)
      return res.status(404).json({ error: "Không tìm thấy thông tin người dùng" });

    const info = infoRes.rows[0];

    // 4️⃣ Chuẩn hóa vai trò (role)
    const role =
      info.role_id === 2 ? "ADMIN" :
      info.role_id === 1 ? "USER" :
      "USER";

    // 5️⃣ Trả về JSON phù hợp với app Android
    res.json({
      message: "Đăng nhập thành công",
      user: {
        user_id: info.user_id,
        phone: info.phone,
        role,
        full_name: info.full_name || info.phone,
        gender: info.gender || "Không rõ",
        dob: info.dob || "2000-01-01",
        family_id: info.family_id || "Không rõ",
        relationship_with_the_head_of_household:
          info.relationship_with_the_head_of_household || "Thành viên",
        email: info.email || "",
        apartment_number: info.apartment_number || null,
        is_living: info.is_living ?? true,
      }
    });

  } catch (err) {
    console.error("💥 [LOGIN ERROR]", err);
    res.status(500).json({ error: "Lỗi server", details: err.message });
  }
});

export default router;
