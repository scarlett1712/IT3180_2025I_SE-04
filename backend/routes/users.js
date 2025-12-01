import express from "express";
import { pool } from "../db.js";
import bcrypt from "bcryptjs";
import crypto from "crypto";

const router = express.Router();

// 🛠️ Tạo bảng login_requests nếu chưa có
(async () => {
  try {
    await pool.query(`
      CREATE TABLE IF NOT EXISTS login_requests (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL,
        status VARCHAR(20) DEFAULT 'pending', -- pending, approved, rejected
        temp_token VARCHAR(255), -- Token tạm sẽ cấp cho máy B nếu được duyệt
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );
    `);
    console.log("✅ Table 'login_requests' ready.");
  } catch (err) {
    console.error("Error creating login_requests table", err);
  }
})();

/* ==========================================================
   🟢 API: Đăng nhập (Sửa đổi logic bảo mật)
========================================================== */
router.post("/login", async (req, res) => {
  try {
    const { phone, password, is_polling } = req.body || {};

    // 1. Nếu là máy B đang thăm dò kết quả (Polling)
    if (is_polling) {
        const { request_id } = req.body;
        const reqRes = await pool.query("SELECT * FROM login_requests WHERE id = $1", [request_id]);
        if (reqRes.rows.length === 0) return res.status(404).json({ error: "Yêu cầu không tồn tại" });

        const request = reqRes.rows[0];
        if (request.status === 'pending') return res.json({ status: 'pending' });
        if (request.status === 'rejected') return res.status(403).json({ error: "Đăng nhập bị từ chối bởi thiết bị chính." });

        // Nếu approved, trả về token thật và thông tin user (để máy B đăng nhập thành công)
        // (Ở đây giản lược, thực tế bạn cần query lại thông tin user để trả về đầy đủ như logic cũ)
        await pool.query("UPDATE users SET session_token = $1 WHERE user_id = $2", [request.temp_token, request.user_id]);
        // Xóa request đã xong
        await pool.query("DELETE FROM login_requests WHERE id = $1", [request_id]);

        return res.json({
            status: 'approved',
            message: "Đăng nhập thành công",
            session_token: request.temp_token,
            // Lưu ý: Máy B cần gọi lại API lấy profile hoặc bạn copy logic lấy profile xuống đây
            user: { id: request.user_id.toString(), phone: phone, role: 'USER' } // Demo dữ liệu
        });
    }

    // 2. Logic đăng nhập bình thường
    if (!phone || !password) return res.status(400).json({ error: "Thiếu thông tin." });

    const userRes = await pool.query("SELECT * FROM users WHERE phone = $1", [phone]);
    if (userRes.rows.length === 0) return res.status(404).json({ error: "SĐT không tồn tại." });

    const user = userRes.rows[0];
    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) return res.status(401).json({ error: "Sai mật khẩu." });

    // 🔥 KIỂM TRA: Đã có máy nào đang đăng nhập chưa?
    if (user.session_token) {
        // Đã có máy A đang dùng -> Tạo yêu cầu phê duyệt
        const tempToken = crypto.randomBytes(32).toString('hex');
        const insertReq = await pool.query(
            "INSERT INTO login_requests (user_id, temp_token) VALUES ($1, $2) RETURNING id",
            [user.user_id, tempToken]
        );

        return res.json({
            require_approval: true,
            request_id: insertReq.rows[0].id,
            message: "Tài khoản đang đăng nhập nơi khác. Vui lòng xác nhận trên thiết bị cũ."
        });
    }

    // Nếu chưa có ai đăng nhập -> Đăng nhập ngay
    const sessionToken = crypto.randomBytes(32).toString('hex');
    await pool.query("UPDATE users SET session_token = $1 WHERE user_id = $2", [sessionToken, user.user_id]);

    // ... (Phần lấy thông tin user trả về giữ nguyên như cũ) ...
    // Để ngắn gọn, tôi trả về json cơ bản, bạn hãy giữ nguyên phần SELECT join bảng của bạn
    return res.json({
        message: "Đăng nhập thành công",
        session_token: sessionToken,
        user: { id: user.user_id.toString(), phone: user.phone, role: 'USER' }
    });

  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Lỗi server." });
  }
});

/* ==========================================================
   🔔 API: Máy A kiểm tra xem có yêu cầu đăng nhập mới không
========================================================== */
router.get("/check_pending_login/:userId", async (req, res) => {
    try {
        const { userId } = req.params;
        const result = await pool.query(
            "SELECT * FROM login_requests WHERE user_id = $1 AND status = 'pending' ORDER BY created_at DESC LIMIT 1",
            [userId]
        );
        res.json(result.rows); // Trả về mảng rỗng hoặc 1 request
    } catch (err) {
        res.status(500).json({ error: "Lỗi server" });
    }
});

/* ==========================================================
   ✅ API: Máy A Duyệt hoặc Từ chối
========================================================== */
router.post("/resolve_login", async (req, res) => {
    try {
        const { request_id, action } = req.body; // action: 'approve' | 'reject'
        await pool.query("UPDATE login_requests SET status = $1 WHERE id = $2", [action, request_id]);

        // Nếu duyệt, Máy A chấp nhận hy sinh session của mình (logout) để máy B vào
        // Hoặc máy A logout ngay tại client
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: "Lỗi server" });
    }
});

// ... (Các API create_admin, reset_password giữ nguyên)

export default router;