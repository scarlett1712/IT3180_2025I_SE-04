import express from "express";
import { pool } from "../db.js";
import { sendMulticastNotification } from "../utils/firebaseHelper.js";
// 🔥 Import Helper vừa tạo (Giữ nguyên của bạn)
import { uploadToCloudinary } from "../utils/cloudinaryHelper.js";

const router = express.Router();

router.post("/", async (req, res) => {
  const {
      content, title, type, sender_id,
      expired_date,     // Ngày hiển thị "Hạn: ..."
      scheduled_time,   // MỚI: Thời gian hẹn giờ gửi từ Android
      target_user_ids, send_to_all,
      file_base64, file_name
  } = req.body;

  console.log("[NOTI] Creating new notification:", { title, type, hasFile: !!file_base64 });

  if (!content || !title || !type || !sender_id) {
    return res.status(400).json({ message: "Thiếu thông tin bắt buộc!" });
  }

  // ----------------------------------------------------------------
  // 📸 BƯỚC 1: GỌI HELPER ĐỂ UPLOAD (Giữ nguyên của bạn)
  // ----------------------------------------------------------------
  let finalFileUrl = null;
  let finalFileType = null;

  if (file_base64) {
      console.log("[NOTI] Uploading file...");
      // Lưu ý: Nếu helper trả về object {url, type} thì code này đúng
      const uploadResult = await uploadToCloudinary(file_base64, "enoti_notifications", file_name);

      if (uploadResult) {
          finalFileUrl = uploadResult.url;
          finalFileType = uploadResult.type; // 'image', 'video', 'raw' (pdf/docx)
          console.log(`✅ Uploaded: ${finalFileType} - ${finalFileUrl}`);
      }
  }

  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    // ----------------------------------------------------------------
    // ⏰ BƯỚC 2: XỬ LÝ LOGIC HẸN GIỜ (Logic Mới - Quan Trọng)
    // ----------------------------------------------------------------
    let scheduledAtDate = null;
    if (scheduled_time) {
        scheduledAtDate = new Date(scheduled_time);
    }

    // Nếu không có giờ hẹn HOẶC giờ hẹn <= hiện tại -> Gửi ngay (SENT)
    const isInstant = !scheduledAtDate || scheduledAtDate <= new Date();

    const initialStatus = isInstant ? 'SENT' : 'PENDING';
    const finalScheduledAt = isInstant ? new Date() : scheduledAtDate;

    // ----------------------------------------------------------------
    // 💾 BƯỚC 3: INSERT DATABASE (Đã cập nhật thêm status & scheduled_at)
    // ----------------------------------------------------------------
    const insertNotification = `
      INSERT INTO notification (
          title, content, expired_date, type, created_by,
          file_url, file_type, created_at,
          scheduled_at, status  -- Thêm 2 cột này để Cron Job chạy được
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), $8, $9)
      RETURNING notification_id;
    `;

    // expired_date: Ngày hết hạn hiển thị trên UI
    // finalScheduledAt: Ngày thực sự gửi thông báo
    const result = await client.query(insertNotification, [
      title,
      content,
      expired_date || null,
      type,
      sender_id,
      finalFileUrl,
      finalFileType,
      finalScheduledAt, // $8
      initialStatus     // $9
    ]);

    const notificationId = result.rows[0].notification_id;

    // ----------------------------------------------------------------
    // 👥 BƯỚC 4: XỬ LÝ NGƯỜI NHẬN (Giữ nguyên logic của bạn)
    // ----------------------------------------------------------------
    let recipients = [];
    if (send_to_all) {
        // Lấy tất cả user trừ role admin (giả sử role_id 2 là admin)
        // Bạn có thể chỉnh lại query này tùy theo logic business
        const allUsersRes = await client.query(`SELECT u.user_id FROM users u JOIN userrole ur ON u.user_id = ur.user_id WHERE ur.role_id != 2`);
        recipients = allUsersRes.rows.map(r => r.user_id);
    } else if (Array.isArray(target_user_ids)) {
        recipients = target_user_ids;
    }

    // Loại bỏ trùng lặp
    recipients = [...new Set(recipients)];

    if (recipients.length > 0) {
        // Insert vào bảng trung gian
        for (const userId of recipients) {
            await client.query(
                `INSERT INTO user_notifications (user_id, notification_id, is_read) VALUES ($1, $2, FALSE) ON CONFLICT DO NOTHING`,
                [userId, notificationId]
            );
        }

        // ----------------------------------------------------------------
        // 🚀 BƯỚC 5: GỬI FIREBASE (Chỉ gửi nếu isInstant = true)
        // ----------------------------------------------------------------
        if (isInstant) {
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

                // Gửi ngay lập tức
                await sendMulticastNotification(tokens, title, content, dataPayload);
                console.log(`Đã gửi ngay thông báo ID ${notificationId} tới ${tokens.length} thiết bị.`);
            }
        } else {
            console.log(`⏳ Đã LÊN LỊCH gửi thông báo ID ${notificationId} vào lúc ${finalScheduledAt}`);
        }
    } else {
        console.warn("Không có người nhận nào được chọn.");
    }

    await client.query("COMMIT");

    // Trả về kết quả
    res.status(201).json({
        message: isInstant ? "Đã gửi thành công" : "Đã lên lịch thành công",
        notification_id: notificationId,
        file_url: finalFileUrl
    });

  } catch (error) {
    await client.query("ROLLBACK");
    console.error("❌ Error create_notification:", error);
    res.status(500).json({ message: "Lỗi server: " + error.message });
  } finally {
    client.release();
  }
});

export default router;