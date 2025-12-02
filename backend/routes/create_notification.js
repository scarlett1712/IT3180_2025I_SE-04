import express from "express";
import { pool } from "../db.js";
// 🔥 Import Helper
import { sendMulticastNotification } from "../utils/firebaseHelper.js";

const router = express.Router();

router.post("/", async (req, res) => {
  // Map sender_id từ App thành created_by trong DB
  const { content, title, type, sender_id, expired_date, target_user_ids, send_to_all } = req.body;

  console.log("📢 [NOTI] Creating new notification:", { title, type, send_to_all });

  // Validate
  if (!content || !title || !type || !sender_id) {
    return res.status(400).json({ message: "Thiếu thông tin bắt buộc!" });
  }

  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    // 1️⃣ Insert Notification
    const insertNotification = `
      INSERT INTO notification (title, content, expired_date, type, created_by, created_at)
      VALUES ($1, $2, $3, $4, $5, NOW())
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
    console.log(`✅ [NOTI] Saved to DB. ID: ${notificationId}`);

    // 2️⃣ Tìm người nhận
    let recipients = [];
    if (send_to_all) {
        // Lấy tất cả user trừ Admin (role=2) và người gửi
        const allUsersRes = await client.query(`
            SELECT u.user_id
            FROM users u
            JOIN userrole ur ON u.user_id = ur.user_id
            WHERE ur.role_id != 2
        `);
        recipients = allUsersRes.rows.map(r => r.user_id);
    } else if (Array.isArray(target_user_ids)) {
        recipients = target_user_ids;
    }

    recipients = [...new Set(recipients)]; // Lọc trùng
    console.log(`👥 [NOTI] Recipients found: ${recipients.length}`);

    // 3️⃣ Lưu user_notifications & Gửi Firebase
    if (recipients.length > 0) {
        // Lưu trạng thái chưa đọc
        for (const userId of recipients) {
          await client.query(
              `INSERT INTO user_notifications (user_id, notification_id, is_read)
               VALUES ($1, $2, FALSE) ON CONFLICT DO NOTHING`,
               [userId, notificationId]
          );
        }

        // 🔥 LẤY TOKEN ĐỂ GỬI
        const tokensRes = await client.query(
            `SELECT fcm_token FROM users WHERE user_id = ANY($1) AND fcm_token IS NOT NULL AND fcm_token != ''`,
            [recipients]
        );
        const tokens = tokensRes.rows.map(r => r.fcm_token);

        console.log(`🔑 [NOTI] Valid FCM Tokens found: ${tokens.length}`);

        if (tokens.length > 0) {
            // Gửi thông báo (Có await để bắt lỗi nếu cần debug)
            await sendMulticastNotification(
              tokens,
              title,
              content,
              {
                 type: "notification_detail",
                 id: notificationId.toString()
              }
            );
        } else {
            console.log("⚠️ [NOTI] No tokens found. Users might not have logged in yet.");
        }
    } else {
        console.log("⚠️ [NOTI] No recipients to send to.");
    }

    await client.query("COMMIT");

    res.status(201).json({
      message: "Tạo thông báo thành công",
      notification_id: notificationId,
    });
  } catch (error) {
    await client.query("ROLLBACK");
    console.error("❌ [NOTI ERROR] Failed to create notification:", error);
    res.status(500).json({ message: "Lỗi server khi tạo thông báo!" });
  } finally {
    client.release();
  }
});

export default router;