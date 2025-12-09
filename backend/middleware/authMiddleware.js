import { pool } from "../db.js";

/**
 * Middleware kiểm tra Session Token
 * Yêu cầu Client gửi header: "Authorization: Bearer <session_token>"
 */
export const verifySession = async (req, res, next) => {
  try {
    const authHeader = req.headers['authorization'];

    // 1. Kiểm tra header có tồn tại không
    if (!authHeader) {
        return res.status(401).json({ error: "Chưa đăng nhập (Thiếu token)" });
    }

    // 2. Tách chuỗi để lấy token (Xử lý an toàn hơn để tránh crash)
    const parts = authHeader.split(' ');
    if (parts.length !== 2 || parts[0] !== 'Bearer') {
        return res.status(401).json({ error: "Định dạng Token không hợp lệ" });
    }

    const token = parts[1];

    // 3. 🔥 TRUY VẤN DATABASE ĐỂ KIỂM TRA TOKEN
    // Chúng ta lấy luôn cả `role_id` để tiện phân quyền sau này
    const result = await pool.query(`
        SELECT u.user_id, ur.role_id
        FROM users u
        LEFT JOIN userrole ur ON u.user_id = ur.user_id
        WHERE u.session_token = $1
    `, [token]);

    // 4. 🔥 LOGIC CHẶN MÁY CŨ (QUAN TRỌNG NHẤT)
    if (result.rows.length === 0) {
        // Nếu không tìm thấy dòng nào => Token gửi lên KHÁC token trong DB
        // => Chứng tỏ tài khoản đã đăng nhập nơi khác (Token trong DB đã đổi)
        return res.status(401).json({
            error: "Phiên đăng nhập hết hạn hoặc tài khoản đã đăng nhập ở nơi khác.",
            force_logout: true // Cờ hiệu để Android biết mà đá ra màn hình Login
        });
    }

    // 5. Gán thông tin user vào request để các API phía sau sử dụng
    req.currentUser = {
        id: result.rows[0].user_id,
        role: (result.rows[0].role_id === 2) ? 'ADMIN' : 'USER'
    };

    // Cho phép đi tiếp vào hàm xử lý chính
    next();

  } catch (err) {
    console.error("Auth Middleware Error:", err);
    res.status(500).json({ error: "Lỗi xác thực hệ thống" });
  }
};