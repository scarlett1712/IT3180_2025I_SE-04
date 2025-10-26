const express = require("express");
const router = express.Router();
const admin = require("firebase-admin");

// Khởi tạo Firebase Admin SDK
const serviceAccount = require("../firebase-service-account.json");
if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
}

router.post("/send-notification", async (req, res) => {
  try {
    // Lấy thêm notification_id từ request body
    const { title, body, fcmToken, notification_id } = req.body;

    if (!fcmToken) {
      return res.status(400).json({ message: "Thiếu FCM token!" });
    }
    if (!notification_id) {
        return res.status(400).json({ message: "Thiếu notification_id!" });
    }

    // Chuyển sang sử dụng "data" payload thay vì "notification" payload
    const message = {
      data: {
        title: title,
        body: body,
        notification_id: String(notification_id) // Đảm bảo ID là chuỗi
      },
      token: fcmToken,
    };

    const response = await admin.messaging().send(message);
    res.status(200).json({ message: "Gửi thông báo thành công!", response });
  } catch (error) {
    console.error("Lỗi gửi thông báo:", error);
    res.status(500).json({ message: "Lỗi khi gửi thông báo!", error: error.message });
  }
});

module.exports = router;
