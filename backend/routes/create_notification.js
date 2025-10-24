import express from "express";
import { pool } from "../db.js";

const router = express.Router();

/**
 * 📢 API: Tạo thông báo mới
 * Body JSON:
 * {
 *   "title": "Cúp điện khu A",
 *   "content": "Cúp điện từ 8h đến 10h sáng mai",
 *   "type": "Hệ thống",
 *   "sender_id": 1,
 *   "expired_date": "2025-11-01",
 *   "target_user_ids": [2, 3, 4]
 * }
 */
router.post("/", async (req, res) => {
  const { content, title, type, sender_id, expired_date, target_user_ids } = req.body;

  console.log("📩 [SEND_NOTIFICATION] Body:", req.body);

  if (!content || !title || !type || !sender_id || !Array.isArray(target_user_ids)) {
    return res.status(400).json({ message: "Thiếu thông tin bắt buộc!" });
  }

  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    // 1️⃣ Tạo thông báo mới
    const insertNotification = `
      INSERT INTO notification (title, content, expired_date, type, created_by)
      VALUES ($1, $2, $3, $4, $5)
      RETURNING notification_id;
    `;
    const result = await client.query(insertNotification, [
      title,
      content,
      expired_date || null,
      type,
      sender_id,
    ]);

    const notificationId = result.rows[0].notification_id;

    // 2️⃣ Gán thông báo cho từng user
    const insertUserNotification = `
      INSERT INTO user_notifications (user_id, notification_id, is_read)
      VALUES ($1, $2, FALSE);
    `;

    for (const userId of target_user_ids) {
      await client.query(insertUserNotification, [userId, notificationId]);
    }

    await client.query("COMMIT");

    console.log(`✅ [SEND_NOTIFICATION] Created notification_id=${notificationId}`);
    res.status(201).json({
      message: "Tạo thông báo thành công",
      notification_id: notificationId,
    });
  } catch (error) {
    await client.query("ROLLBACK");
    console.error("❌ [SEND_NOTIFICATION] Lỗi khi tạo thông báo:", error);
    res.status(500).json({ message: "Lỗi server khi tạo thông báo!" });
  } finally {
    client.release();
  }
});

export default router;