import { pool } from "../db.js";

// 🟢 Người dùng gửi feedback
export const createFeedback = async (req, res) => {
  const { notification_id, user_id, content, file_url } = req.body;
  try {
    const result = await pool.query(
      `INSERT INTO feedback (notification_id, user_id, content, file_url, status)
       VALUES ($1, $2, $3, $4, 'sent')
       RETURNING *`,
      [notification_id, user_id, content, file_url]
    );
    res.status(201).json(result.rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: "Error creating feedback" });
  }
};

// 🟡 Admin lấy danh sách tất cả feedback
export const getAllFeedback = async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT f.*, u.full_name AS user_name, n.title AS notification_title
      FROM feedback f
      JOIN user_item u ON f.user_id = u.user_id
      JOIN notification n ON f.notification_id = n.notification_id
      ORDER BY f.created_at DESC
    `);
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: "Error fetching feedbacks" });
  }
};

// 🟠 Khi admin đọc feedback → đổi trạng thái thành 'read'
export const markAsRead = async (req, res) => {
  const { feedback_id } = req.params;
  try {
    const result = await pool.query(
      `UPDATE feedback
       SET status = 'read'
       WHERE feedback_id = $1 AND status IN ('pending', 'sent')
       RETURNING *`,
      [feedback_id]
    );
    if (result.rowCount === 0)
      return res.status(404).json({ message: "Feedback not found or already read" });

    res.json(result.rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: "Error updating status" });
  }
};
