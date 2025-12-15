import express from "express";
import { pool } from "../db.js";
import { sendMulticastNotification } from "../utils/firebaseHelper.js";
import { v2 as cloudinary } from "cloudinary";

const router = express.Router();

// CẤU HÌNH CLOUDINARY (Giữ nguyên)
cloudinary.config({
  cloud_name: 'process.env.CLOUDNAME',
  api_key: 'process.env.CLOUDKEY',
  api_secret: 'process.env.CLOUDSECRET'
});

router.post("/", async (req, res) => {
  // Nhận file_base64 và file_type từ App
  // file_type: 'image', 'video', hoặc 'raw' (cho PDF, DOC)
  const { content, title, type, sender_id, expired_date, target_user_ids, send_to_all, file_base64, file_name } = req.body;

  let finalFileUrl = null;
  let finalFileType = null; // 'image', 'video', 'application' (pdf)

  // 🔥 XỬ LÝ UPLOAD FILE ĐA NĂNG
  if (file_base64) {
    try {
      console.log("📂 [NOTI] Uploading file to Cloudinary...");

      // Xác định resource_type dựa trên nội dung Base64 hoặc extension
      // Mặc định Cloudinary dùng:
      // - 'image': ảnh
      // - 'video': video, audio
      // - 'raw': pdf, doc, zip...
      let resourceType = 'auto'; // Để Cloudinary tự đoán

      // Mẹo: Nếu upload PDF, nên set resource_type là 'auto' hoặc 'raw'
      // Để an toàn, ta để 'auto' cho ảnh/video, và xử lý riêng cho PDF nếu cần.

      const uploadRes = await cloudinary.uploader.upload(file_base64, {
        folder: "enoti_files",
        resource_type: "auto", // 🔥 QUAN TRỌNG: Tự động nhận diện Video/Ảnh/PDF
        public_id: file_name ? file_name.split('.')[0] : undefined // Giữ tên file (tùy chọn)
      });

      finalFileUrl = uploadRes.secure_url;
      finalFileType = uploadRes.resource_type; // Cloudinary trả về: 'image', 'video', hoặc 'raw'

      // Nếu Cloudinary trả về 'raw' (cho PDF), ta có thể lưu cụ thể hơn
      if (finalFileUrl.endsWith(".pdf")) finalFileType = "pdf";

      console.log(`✅ Uploaded: ${finalFileType} - ${finalFileUrl}`);

    } catch (upErr) {
      console.error("❌ Cloudinary upload failed:", upErr);
      // return res.status(500).json({ message: "Lỗi upload file" });
    }
  }

  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    // INSERT với file_url và file_type
    const insertNotification = `
      INSERT INTO notification (title, content, expired_date, type, created_by, file_url, file_type, created_at)
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW())
      RETURNING notification_id;
    `;

    const result = await client.query(insertNotification, [
      title,
      content,
      expired_date || null,
      type,
      sender_id,
      finalFileUrl,
      finalFileType // Lưu loại file để App biết cách mở
    ]);

    const notificationId = result.rows[0].notification_id;

    // ... (Phần code tìm người nhận và Insert user_notifications GIỮ NGUYÊN) ...
    // ... Copy đoạn code tìm recipients và insert user_notifications từ file cũ vào đây ...

    // Gửi Firebase (Kèm link file)
    // ... (Code lấy token giữ nguyên) ...
    /*
    if (tokens.length > 0) {
        const dataPayload = {
             type: "notification_detail",
             id: notificationId.toString()
        };
        if (finalFileUrl) {
            dataPayload.file_url = finalFileUrl;
            dataPayload.file_type = finalFileType;
        }
        await sendMulticastNotification(tokens, title, content, dataPayload);
    }
    */

    await client.query("COMMIT");
    res.status(201).json({ message: "Thành công", notification_id: notificationId });

  } catch (error) {
    await client.query("ROLLBACK");
    console.error("Error:", error);
    res.status(500).json({ message: "Lỗi server" });
  } finally {
    client.release();
  }
});

export default router;