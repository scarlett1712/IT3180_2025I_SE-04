import express from "express";
import { pool } from "../db.js";
// 🔥 Import Helper để gửi thông báo
import { sendMulticastNotification } from "../utils/firebaseHelper.js";

const router = express.Router();

router.post("/", async (req, res) => {
  // Lưu ý: App cũ gửi 'sender_id', trong DB là 'created_by'. Chúng ta sẽ map lại.
  const { content, title, type, sender_id, expired_date, target_user_ids, send_to_all } = req.body;

  console.log("📩 [SEND_NOTIFICATION] Body:", req.body);

  // Validate cơ bản
  if (!content || !title || !type || !sender_id) {
    return res.status(400).json({ message: "Thiếu thông tin bắt buộc!" });
  }

  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    // 1️⃣ Tạo thông báo mới
    const insertNotification = `
      INSERT INTO notification (title, content, expired_date, type, created_by, created_at)
      VALUES ($1, $2, $3, $4, $5, NOW())
      RETURNING notification_id;
    `;
    // expired_date có thể null
    const result = await client.query(insertNotification, [
      title,
      content,
      expired_date || null,
      type,
      sender_id,
    ]);

    const notificationId = result.rows[0].notification_id;

    // 2️⃣ Xác định danh sách người nhận
    let recipients = [];
    if (send_to_all) {
        // Nếu gửi tất cả: Lấy hết user_id (trừ sender_id/admin)
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

    // Loại bỏ trùng lặp
    recipients = [...new Set(recipients)];

    // 3️⃣ Gán thông báo cho từng user vào bảng user_notifications
    if (recipients.length > 0) {
        const insertUserNotification = `
          INSERT INTO user_notifications (user_id, notification_id, is_read)
          VALUES ($1, $2, FALSE)
          ON CONFLICT DO NOTHING;
        `;

        for (const userId of recipients) {
          await client.query(insertUserNotification, [userId, notificationId]);
        }

        // 4️⃣ 🔥 GỬI PUSH NOTIFICATION QUA FIREBASE 🔥
        // Lấy token của danh sách người nhận
        const tokensRes = await client.query(
            `SELECT fcm_token FROM users WHERE user_id = ANY($1) AND fcm_token IS NOT NULL AND fcm_token != ''`,
            [recipients]
        );
        const tokens = tokensRes.rows.map(r => r.fcm_token);

        console.log(`📲 [SEND_NOTIFICATION] Found ${tokens.length} FCM tokens. Sending push...`);

        if (tokens.length > 0) {
            sendMulticastNotification(
              tokens,
              title,
              content,
              {
                 type: "notification_detail",
                 id: notificationId.toString()
              }
            );
        }
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