// backend/routes/change_password.js
import express from "express";
import { pool } from "../db.js";
import bcrypt from "bcryptjs";

const router = express.Router();

/**
 * POST /api/changepassword
 * Body:
 * {
 *   "user_id": 123,
 *   "old_password": "oldpass",
 *   "new_password": "newpass"
 * }
 */
router.post("/", async (req, res) => {
  console.log("[POST] /api/changepassword - Body:", {
    user_id: req.body?.user_id,
    // DON'T log passwords
  });

  const { user_id, old_password, new_password } = req.body || {};

  // Validate inputs
  if (!user_id || !old_password || !new_password) {
    return res.status(400).json({
      success: false,
      message: "Thiếu thông tin cần thiết (user_id, old_password, new_password)."
    });
  }

  // optional: enforce password length policy
  const MIN_LEN = 6;
  const MAX_LEN = 128;
  if (typeof new_password !== "string" || new_password.length < MIN_LEN || new_password.length > MAX_LEN) {
    return res.status(400).json({
      success: false,
      message: `Mật khẩu mới phải có độ dài từ ${MIN_LEN} đến ${MAX_LEN} ký tự.`
    });
  }

  // ensure numeric user_id when DB expects integer
  const uid = Number(user_id);
  if (Number.isNaN(uid)) {
    return res.status(400).json({ success: false, message: "user_id không hợp lệ." });
  }

  try {
    // 1) Lấy hash mật khẩu từ DB (cột của bạn là password_hash)
    const q = "SELECT password_hash FROM users WHERE user_id = $1";
    const result = await pool.query(q, [uid]);

    if (result.rows.length === 0) {
      return res.status(404).json({ success: false, message: "Không tìm thấy người dùng." });
    }

    const storedHash = result.rows[0].password_hash;
    if (!storedHash || typeof storedHash !== "string") {
      console.error("[CHANGE_PASSWORD] stored hash missing or invalid for user:", uid);
      return res.status(500).json({ success: false, message: "Dữ liệu mật khẩu không hợp lệ." });
    }

    // 2) So sánh mật khẩu cũ (plaintext) với hash (không cần decrypt)
    const isMatch = await bcrypt.compare(old_password, storedHash);
    if (!isMatch) {
      return res.status(401).json({ success: false, message: "Mật khẩu cũ không đúng." });
    }

    // 3) Hash mật khẩu mới và lưu
    const newHash = await bcrypt.hash(new_password, 10); // salt rounds = 10
    await pool.query("UPDATE users SET password_hash = $1 WHERE user_id = $2", [newHash, uid]);

    console.log(`[CHANGE_PASSWORD] User ${uid} đổi mật khẩu thành công.`);
    return res.status(200).json({ success: true, message: "Đổi mật khẩu thành công." });

  } catch (err) {
    console.error("[CHANGE_PASSWORD] Lỗi khi đổi mật khẩu:", err);
    return res.status(500).json({ success: false, message: "Lỗi máy chủ khi đổi mật khẩu." });
  }
});

export default router;
