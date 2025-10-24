import express from "express";
import { pool } from "../db.js";

const router = express.Router();

// ✅ Lấy tất cả thông báo của 1 user (có trạng thái đã đọc)
router.get("/:userId", async (req, res) => {
  try {
    const { userId } = req.params;

    const result = await pool.query(
      `
      SELECT n.notification_id,
             n.content,
             n.type,
             n.created_at,
             ui.full_name AS sender,
             un.is_read
      FROM notification n
      JOIN user_notification un ON n.notification_id = un.notification_id
      JOIN user_item ui ON n.created_by = ui.user_id
      WHERE un.user_id = $1
      ORDER BY n.created_at DESC
      `,
      [userId]
    );

    res.json(result.rows);
  } catch (error) {
    console.error("❌ Error fetching notifications:", error);
    res.status(500).json({ message: "Lỗi server" });
  }
});

// ✅ Đánh dấu là đã đọc
router.put("/:notificationId/read", async (req, res) => {
  try {
    const { notificationId } = req.params;

    await pool.query(
      `
      UPDATE user_notification
      SET is_read = TRUE
      WHERE notification_id = $1
      `,
      [notificationId]
    );

    res.json({ message: "Đã đánh dấu là đã đọc" });
  } catch (error) {
    console.error("❌ Error updating notification:", error);
    res.status(500).json({ message: "Lỗi server" });
  }
});

export default router;