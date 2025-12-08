import { pool } from "../db.js";
// 🔥 Import helper gửi thông báo
import { sendNotification } from "../utils/firebaseHelper.js";

// 🟢 Người dùng gửi feedback -> Báo cho Admin
export const createFeedback = async (req, res) => {
  const { notification_id, user_id, content, file_url } = req.body;
  try {
    // 1. Insert Feedback
    const result = await pool.query(
      `INSERT INTO feedback (notification_id, user_id, content, file_url, status)
       VALUES ($1, $2, $3, $4, 'sent')
       RETURNING
         feedback_id,
         notification_id,
         user_id,
         content,
         file_url,
         status,
         TO_CHAR(created_at, 'DD-MM-YYYY HH24:MI') AS created_at`,
      [notification_id, user_id, content, file_url]
    );

    // 2. 🔥 GỬI THÔNG BÁO CHO TOÀN BỘ ADMIN
    // Tìm tất cả user có role_id = 2 (Admin) và có fcm_token
    const adminRes = await pool.query(`
        SELECT u.fcm_token
        FROM users u
        JOIN userrole ur ON u.user_id = ur.user_id
        WHERE ur.role_id = 2 AND u.fcm_token IS NOT NULL
    `);

    // Gửi loop cho tất cả admin
    for (const row of adminRes.rows) {
        if (row.fcm_token) {
            sendNotification(
                row.fcm_token,
                "📩 Góp ý mới từ cư dân",
                `Nội dung: "${content}". Vui lòng kiểm tra.`,
                { type: "feedback", id: result.rows[0].feedback_id.toString() }
            );
        }
    }

    res.status(201).json(result.rows[0]);
  } catch (err) {
    console.error("💥 Error creating feedback:", err);
    res.status(500).json({ message: "Error creating feedback" });
  }
};

// 🟡 Admin lấy tất cả feedback
export const getAllFeedback = async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT
        f.feedback_id,
        f.notification_id,
        f.user_id,
        f.content,
        f.file_url,
        f.status,
        TO_CHAR(f.created_at, 'DD-MM-YYYY HH24:MI') AS created_at,
        ui.full_name AS user_name,
        n.title AS notification_title
      FROM feedback f
      JOIN user_item ui ON f.user_id = ui.user_id
      JOIN notification n ON f.notification_id = n.notification_id
      ORDER BY f.created_at DESC
    `);
    res.json(result.rows);
  } catch (err) {
    console.error("💥 Error fetching all feedback:", err);
    res.status(500).json({ message: "Error fetching feedbacks" });
  }
};

// 🟢 Người dùng lấy feedback của mình
export const getFeedbackByUser = async (req, res) => {
  const { userId } = req.params;
  try {
    const result = await pool.query(
      `
      SELECT
        f.feedback_id,
        f.notification_id,
        f.user_id,
        f.content,
        f.file_url,
        f.status,
        TO_CHAR(f.created_at, 'DD-MM-YYYY HH24:MI') AS created_at,
        n.title AS notification_title
      FROM feedback f
      JOIN notification n ON f.notification_id = n.notification_id
      WHERE f.user_id = $1
      ORDER BY f.created_at DESC
      `,
      [userId]
    );
    res.json(result.rows);
  } catch (err) {
    console.error("💥 Error fetching user's feedback:", err);
    res.status(500).json({ message: "Error fetching user's feedback" });
  }
};

// 🟠 Admin đánh dấu feedback đã đọc
export const markAsRead = async (req, res) => {
  const { feedback_id } = req.params;
  try {
    const result = await pool.query(
      `UPDATE feedback
       SET status = 'read'
       WHERE feedback_id = $1 AND status IN ('pending', 'sent')
       RETURNING
         feedback_id,
         notification_id,
         user_id,
         content,
         file_url,
         status,
         TO_CHAR(created_at, 'DD-MM-YYYY HH24:MI') AS created_at`,
      [feedback_id]
    );

    if (result.rowCount === 0)
      return res.status(404).json({ message: "Feedback not found or already read" });

    res.json(result.rows[0]);
  } catch (err) {
    console.error("💥 Error updating feedback status:", err);
    res.status(500).json({ message: "Error updating status" });
  }
};

// 🟣 Admin lấy danh sách feedback theo notification_id
export const getFeedbackByNotification = async (req, res) => {
  const { notification_id } = req.params;
  try {
    const result = await pool.query(
      `
      SELECT
        f.feedback_id,
        f.notification_id,
        f.user_id,
        f.content,
        f.file_url,
        f.status,
        TO_CHAR(f.created_at, 'DD-MM-YYYY HH24:MI') AS created_at,
        ui.full_name
      FROM feedback f
      LEFT JOIN user_item ui ON f.user_id = ui.user_id
      WHERE f.notification_id = $1
      ORDER BY f.created_at DESC
      `,
      [notification_id]
    );
    res.json(result.rows);
  } catch (err) {
    console.error("💥 Error fetching feedback by notification:", err);
    res.status(500).json({ message: "Lỗi server khi lấy danh sách phản hồi" });
  }
};