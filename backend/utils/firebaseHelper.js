import admin from "firebase-admin";
import { createRequire } from "module";
import fs from "fs";

const require = createRequire(import.meta.url);

// 🔥 Tự động tìm file key (Render hoặc Local)
let serviceAccount;
const renderPath = "/etc/secrets/serviceAccountKey.json";
const localPath = "../config/serviceAccountKey.json";

try {
  if (fs.existsSync(renderPath)) {
    serviceAccount = require(renderPath);
  } else {
    serviceAccount = require(localPath);
  }
} catch (error) {
  console.error("❌ [FIREBASE] Critical: Service Account Key not found!");
}

if (!admin.apps.length && serviceAccount) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
  console.log("✅ [FIREBASE] Initialized successfully.");
}

/**
 * Gửi thông báo đến nhiều người
 */
export const sendMulticastNotification = async (tokens, title, body, data = {}) => {
  if (!tokens || tokens.length === 0) return;

  // Cấu hình thông báo chuẩn để hiện Popup trên Android
  const message = {
    notification: { title, body }, // Phần hiển thị
    data: data, // Phần dữ liệu ngầm
    tokens: tokens,
    android: {
        priority: "high",
        notification: {
            channelId: "ENOTI_HIGH_PRIORITY_V2", // Khớp với App Android
            sound: "default",
            priority: "high"
        }
    }
  };

  try {
    const response = await admin.messaging().sendMulticast(message);
    console.log(`🚀 [FIREBASE] Sent: ${response.successCount} success, ${response.failureCount} failed.`);

    if (response.failureCount > 0) {
        const failedTokens = [];
        response.responses.forEach((resp, idx) => {
            if (!resp.success) {
                failedTokens.push(tokens[idx]);
            }
        });
        // console.log('Failed tokens:', failedTokens); // Bỏ comment nếu muốn debug sâu
    }
  } catch (error) {
    console.error("❌ [FIREBASE] Send Error:", error);
  }
};

export const sendNotification = async (token, title, body, data = {}) => {
    if (!token) return;
    const message = {
        token: token,
        notification: { title, body },
        data: data,
        android: { priority: "high" }
    };
    try {
        await admin.messaging().send(message);
        console.log("🚀 [FIREBASE] Single message sent.");
    } catch (error) {
        console.error("❌ [FIREBASE] Single Send Error:", error);
    }
};