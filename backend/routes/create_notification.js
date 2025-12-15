import express from "express";
import { pool } from "../db.js";
import { sendMulticastNotification } from "../utils/firebaseHelper.js";
// 🔥 Import Helper vừa tạo
import { uploadToCloudinary } from "../utils/cloudinaryHelper.js";

const router = express.Router();

router.post("/", async (req, res) => {
  const {
      content, title, type, sender_id, expired_date,
      target_user_ids, send_to_all,
      file_base64, file_name
  } = req.body;

  console.log("📢 [NOTI] Creating new notification:", { title, type, hasFile: !!file_base64 });

  if (!content || !title || !type || !sender_id) {
    return res.status(400).json({ message: "Thiếu thông tin bắt buộc!" });
  }

  // ----------------------------------------------------------------
  // 📸 BƯỚC 1: GỌI HELPER ĐỂ UPLOAD (Rất gọn)
  // ----------------------------------------------------------------
  let finalFileUrl = null;
  let finalFileType = null;

  if (file_base64) {
      console.log("📂 [NOTI] Uploading file...");
      const uploadResult = await uploadToCloudinary(file_base64, "enoti_notifications", file_name);

      if (uploadResult) {
          finalFileUrl = uploadResult.url;
          finalFileType = uploadResult.type;
          console.log(`✅ Uploaded: ${finalFileType} - ${finalFileUrl}`);
      }
  }

  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    // ----------------------------------------------------------------
    // 💾 BƯỚC 2: INSERT DATABASE (Giữ nguyên)
    // ----------------------------------------------------------------
    const insertNotification = `
      INSERT INTO notification (
          title, content, expired_date, type, created_by,
          file_url, file_type, created_at
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW())
      RETURNING notification_id;
    `;

    const result = await client.query(insertNotification, [
      title, content, expired_date || null, type, sender_id,
      finalFileUrl, finalFileType
    ]);

    const notificationId = result.rows[0].notification_id;

    // ----------------------------------------------------------------
    // 👥 BƯỚC 3 & 4: XỬ LÝ NGƯỜI NHẬN & GỬI FIREBASE (Giữ nguyên logic cũ)
    // ----------------------------------------------------------------
    // ... (Phần code tìm recipients giữ nguyên như cũ) ...
    let recipients = [];
    if (send_to_all) {
        const allUsersRes = await client.query(`SELECT u.user_id FROM users u JOIN userrole ur ON u.user_id = ur.user_id WHERE ur.role_id != 2`);
        recipients = allUsersRes.rows.map(r => r.user_id);
    } else if (Array.isArray(target_user_ids)) {
        recipients = target_user_ids;
    }
    recipients = [...new Set(recipients)];

    if (recipients.length > 0) {
        for (const userId of recipients) {
            await client.query(
                `INSERT INTO user_notifications (user_id, notification_id, is_read) VALUES ($1, $2, FALSE) ON CONFLICT DO NOTHING`,
                [userId, notificationId]
            );
        }

        const tokensRes = await client.query(
            `SELECT fcm_token FROM users WHERE user_id = ANY($1) AND fcm_token IS NOT NULL AND fcm_token != ''`,
            [recipients]
        );
        const tokens = tokensRes.rows.map(r => r.fcm_token);

        if (tokens.length > 0) {
            const dataPayload = { type: "notification_detail", id: notificationId.toString() };
            if (finalFileUrl) {
                dataPayload.file_url = finalFileUrl;
                dataPayload.file_type = finalFileType || "file";
            }
            await sendMulticastNotification(tokens, title, content, dataPayload);
        }
    }

    await client.query("COMMIT");
    res.status(201).json({ message: "Thành công", notification_id: notificationId, file_url: finalFileUrl });

  } catch (error) {
    await client.query("ROLLBACK");
    console.error("❌ Error:", error);
    res.status(500).json({ message: "Lỗi server" });
  } finally {
    client.release();
  }
});

export default router;