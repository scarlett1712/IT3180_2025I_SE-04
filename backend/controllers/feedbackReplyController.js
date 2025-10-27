import pool from "../db.js";

// 🔵 Admin gửi phản hồi
export const replyToFeedback = async (req, res) => {
  const { feedback_id } = req.params;
  const { admin_id, reply_content } = req.body;

  try {
    await pool.query("BEGIN");

    // 1️⃣ Tạo bản ghi phản hồi
    const replyResult = await pool.query(
      `INSERT INTO feedback_reply (feedback_id, admin_id, reply_content)
       VALUES ($1, $2, $3)
       RETURNING *`,
      [feedback_id, admin_id, reply_content]
    );

    // 2️⃣ Cập nhật trạng thái feedback thành 'replied'
    await pool.query(
      `UPDATE feedback
       SET status = 'replied'
       WHERE feedback_id = $1`,
      [feedback_id]
    );

    await pool.query("COMMIT");
    res.status(201).json({
      message: "Reply sent successfully",
      reply: replyResult.rows[0],
    });
  } catch (err) {
    await pool.query("ROLLBACK");
    console.error(err);
    res.status(500).json({ message: "Error replying to feedback" });
  }
};

// 🟣 Lấy tất cả phản hồi của một feedback
export const getRepliesByFeedbackId = async (req, res) => {
  const { feedback_id } = req.params;
  try {
    const result = await pool.query(
      `SELECT r.*, a.full_name AS admin_name
       FROM feedback_reply r
       JOIN user_item a ON r.admin_id = a.user_id
       WHERE r.feedback_id = $1
       ORDER BY r.created_at ASC`,
      [feedback_id]
    );
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: "Error fetching replies" });
  }
};
