import { pool } from "../db.js"; // Đã sửa lại import { pool } cho đồng bộ
// 🔥 Import helper gửi thông báo
import { sendNotification } from "../utils/firebaseHelper.js";

// 🔵 Admin gửi phản hồi -> Báo cho User
export const replyToFeedback = async (req, res) => {
  const { feedback_id } = req.params;
  const { admin_id, reply_content, content } = req.body; // nhận cả 2 key
  const finalContent = reply_content || content; // linh hoạt: Android có thể gửi "content"

  if (!admin_id || !finalContent) {
    return res.status(400).json({ message: "Thiếu admin_id hoặc nội dung phản hồi" });
  }

  try {
    await pool.query("BEGIN");

    // 1️⃣ Tạo bản ghi phản hồi
    const replyResult = await pool.query(
      `
      INSERT INTO feedback_reply (feedback_id, admin_id, reply_content)
      VALUES ($1, $2, $3)
      RETURNING *
      `,
      [feedback_id, admin_id, finalContent]
    );

    // 2️⃣ Cập nhật trạng thái feedback thành 'replied'
    const updateResult = await pool.query(
      `
      UPDATE feedback
      SET status = 'replied'
      WHERE feedback_id = $1
      RETURNING *
      `,
      [feedback_id]
    );

    // 3️⃣ 🔥 GỬI THÔNG BÁO CHO USER (Người tạo Feedback)
    // Lấy fcm_token của user dựa vào feedback_id
    const userRes = await pool.query(`
        SELECT u.fcm_token
        FROM feedback f
        JOIN users u ON f.user_id = u.user_id
        WHERE f.feedback_id = $1
    `, [feedback_id]);

    if (userRes.rows.length > 0 && userRes.rows[0].fcm_token) {
        sendNotification(
            userRes.rows[0].fcm_token,
            "💬 Admin đã trả lời góp ý",
            `Ban quản lý vừa trả lời góp ý của bạn: "${finalContent}"`,
            { type: "feedback_reply", id: feedback_id.toString() }
        );
    }

    await pool.query("COMMIT");

    res.status(201).json({
      message: "Phản hồi đã được gửi và feedback đã cập nhật trạng thái!",
      reply: replyResult.rows[0],
      updated_feedback: updateResult.rows[0],
    });
  } catch (err) {
    await pool.query("ROLLBACK");
    console.error("❌ Lỗi replyToFeedback:", err);
    res.status(500).json({ message: "Error replying to feedback", error: err.message });
  }
};

// 🟣 Lấy tất cả phản hồi của một feedback
export const getRepliesByFeedbackId = async (req, res) => {
  const { feedback_id } = req.params;
  try {
    const result = await pool.query(
      `
      SELECT r.*, a.full_name AS admin_name
      FROM feedback_reply r
      JOIN user_item a ON r.admin_id = a.user_id
      WHERE r.feedback_id = $1
      ORDER BY r.created_at ASC
      `,
      [feedback_id]
    );
    res.json(result.rows);
  } catch (err) {
    console.error("❌ Lỗi getRepliesByFeedbackId:", err);
    res.status(500).json({ message: "Error fetching replies", error: err.message });
  }
};