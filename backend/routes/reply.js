import express from "express";
import { pool } from "../db.js";
import { sendNotification } from "../utils/firebaseHelper.js";
const router = express.Router();

// Lấy danh sách phản hồi theo feedback_id
router.get("/:feedback_id", async (req, res) => {
  const { feedback_id } = req.params;
  try {
    const result = await pool.query(
      "SELECT * FROM replies WHERE feedback_id = $1 ORDER BY created_at",
      [feedback_id]
    );
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Server error" });
  }
});

// Admin gửi phản hồi
router.post("/", async (req, res) => {
  const { feedback_id, admin_id, content } = req.body;

  try {
    // 1. Lưu phản hồi vào DB
    await pool.query(
      `INSERT INTO replies (feedback_id, admin_id, content, created_at)
       VALUES ($1, $2, $3, NOW())`,
      [feedback_id, admin_id, content]
    );

    // 2. 🔥 GỬI THÔNG BÁO CHO USER (Người tạo Feedback)
    // Lấy thông tin user từ bảng feedback
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
            `Ban quản lý vừa trả lời góp ý của bạn: "${content}"`,
            { type: "feedback", id: feedback_id.toString() }
        );
    }

    res.status(201).json({ message: "Reply sent and notification pushed." });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Insert failed" });
  }
});

export default router;
