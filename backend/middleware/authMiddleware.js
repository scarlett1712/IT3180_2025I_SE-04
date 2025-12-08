import { pool } from "../db.js";

/**
 * Middleware kiểm tra Session Token
 * Yêu cầu Client gửi header: "Authorization: Bearer <session_token>"
 */
export const verifySession = async (req, res, next) => {
  try {
    const authHeader = req.headers['authorization'];

    // Kiểm tra xem có gửi token không
    if (!authHeader) {
        return res.status(401).json({ error: "Chưa đăng nhập (Thiếu token)" });
    }

    const token = authHeader.split(' ')[1]; // Lấy phần token sau chữ Bearer

    // Ở đây chúng ta cần user_id để so sánh.
    // Cách tốt nhất là Client gửi user_id trong body hoặc query,
    // hoặc chúng ta query ngược lại từ token (nhưng query user_id từ body nhanh hơn nếu có).

    // Cách an toàn nhất: Tìm user sở hữu token này trong DB
    const result = await pool.query(
        "SELECT user_id FROM users WHERE session_token = $1",
        [token]
    );

    if (result.rows.length === 0) {
        // Token không tồn tại trong DB => Đã bị đăng nhập đè ở máy khác hoặc fake
        return res.status(401).json({
            error: "Phiên đăng nhập hết hạn hoặc tài khoản đã đăng nhập ở nơi khác.",
            force_logout: true
        });
    }

    // Nếu tìm thấy, gán user_id vào request để các API sau dùng
    req.currentUser = { id: result.rows[0].user_id };
    next();

  } catch (err) {
    console.error("Auth Middleware Error:", err);
    res.status(500).json({ error: "Lỗi xác thực hệ thống" });
  }
};