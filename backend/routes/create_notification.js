import express from "express";
import { pool } from "../db.js";
// 🔥 Import Helper cũ của bạn
import { sendMulticastNotification } from "../utils/firebaseHelper.js";
// 🔥 Import Cloudinary để xử lý file
import { v2 as cloudinary } from "cloudinary";

const router = express.Router();

// ==================================================================
// ☁️ CẤU HÌNH CLOUDINARY
// Bạn hãy điền thông tin lấy từ dashboard cloudinary.com vào đây
// ==================================================================
cloudinary.config({
  cloud_name: 'process.env.CLOUDNAME',
  api_key: 'process.env.CLOUDKEY',
  api_secret: 'process.env.CLOUDSECRET'
});

// ==================================================================
// 🚀 API TẠO THÔNG BÁO (Hỗ trợ text + file đính kèm)
// ==================================================================
router.post("/", async (req, res) => {
  // Nhận dữ liệu từ App (Bao gồm cả file_base64 và file_name mới thêm)
  const {
      content,
      title,
      type,
      sender_id,
      expired_date,
      target_user_ids,
      send_to_all,
      file_base64, // Chuỗi Base64 của file
      file_name    // Tên file (tùy chọn)
  } = req.body;

  console.log("📢 [NOTI] Creating new notification:", { title, type, send_to_all, hasFile: !!file_base64 });

  // Validate thông tin bắt buộc
  if (!content || !title || !type || !sender_id) {
    return res.status(400).json({ message: "Thiếu thông tin bắt buộc!" });
  }

  let finalFileUrl = null;
  let finalFileType = null; // 'image', 'video', 'pdf', ...

  // ----------------------------------------------------------------
  // 📸 BƯỚC 1: UPLOAD FILE LÊN CLOUDINARY (NẾU CÓ)
  // ----------------------------------------------------------------
  if (file_base64) {
    try {
      console.log("📂 [NOTI] Uploading file to Cloudinary...");

      // resource_type: 'auto' giúp Cloudinary tự nhận diện là Ảnh, Video hay PDF (raw)
      const uploadRes = await cloudinary.uploader.upload(file_base64, {
        folder: "enoti_files",
        resource_type: "auto",
        public_id: file_name ? file_name.split('.')[0] : undefined
      });

      finalFileUrl = uploadRes.secure_url;
      finalFileType = uploadRes.resource_type; // Trả về 'image', 'video' hoặc 'raw'

      // Nếu là file PDF/Doc (dạng raw), ta gán cứng loại file để App dễ xử lý
      if (finalFileUrl.endsWith(".pdf")) {
          finalFileType = "pdf";
      }

      console.log(`✅ [NOTI] Uploaded: ${finalFileType} - ${finalFileUrl}`);
    } catch (upErr) {
      console.error("❌ [NOTI] Cloudinary upload failed:", upErr);
      // Không return lỗi để vẫn cho phép tạo thông báo dù lỗi ảnh (tùy logic dự án)
    }
  }

  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    // ----------------------------------------------------------------
    // 💾 BƯỚC 2: INSERT VÀO DATABASE
    // (Đã thêm cột file_url và file_type)
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
      title,
      content,
      expired_date || null,
      type,
      sender_id,
      finalFileUrl, // Link file (hoặc null)
      finalFileType // Loại file (hoặc null)
    ]);

    const notificationId = result.rows[0].notification_id;
    console.log(`✅ [NOTI] Saved to DB. ID: ${notificationId}`);

    // ----------------------------------------------------------------
    // 👥 BƯỚC 3: XÁC ĐỊNH NGƯỜI NHẬN (LOGIC CŨ CỦA BẠN)
    // ----------------------------------------------------------------
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

    recipients = [...new Set(recipients)]; // Lọc trùng ID
    console.log(`👥 [NOTI] Recipients found: ${recipients.length}`);

    // ----------------------------------------------------------------
    // 🔔 BƯỚC 4: LƯU TRẠNG THÁI & GỬI FIREBASE (FCM)
    // ----------------------------------------------------------------
    if (recipients.length > 0) {
        // 4.1. Lưu vào bảng user_notifications (đánh dấu chưa đọc)
        for (const userId of recipients) {
          await client.query(
              `INSERT INTO user_notifications (user_id, notification_id, is_read)
               VALUES ($1, $2, FALSE) ON CONFLICT DO NOTHING`,
               [userId, notificationId]
          );
        }

        // 4.2. Lấy Token FCM để bắn thông báo
        const tokensRes = await client.query(
            `SELECT fcm_token FROM users WHERE user_id = ANY($1) AND fcm_token IS NOT NULL AND fcm_token != ''`,
            [recipients]
        );
        const tokens = tokensRes.rows.map(r => r.fcm_token);

        console.log(`🔑 [NOTI] Valid FCM Tokens found: ${tokens.length}`);

        if (tokens.length > 0) {
            // Chuẩn bị dữ liệu đi kèm (Payload)
            const dataPayload = {
                 type: "notification_detail",
                 id: notificationId.toString()
            };

            // Nếu có file, gửi kèm link trong payload để App xử lý nhanh nếu cần
            if (finalFileUrl) {
                dataPayload.file_url = finalFileUrl;
                dataPayload.file_type = finalFileType || "file";
            }

            // Gửi Multicast (1 lần cho nhiều token)
            await sendMulticastNotification(
              tokens,
              title,
              content,
              dataPayload
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
      file_url: finalFileUrl // Trả về link file để App cập nhật UI nếu cần
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