import admin from "firebase-admin";
import { createRequire } from "module";
const require = createRequire(import.meta.url);

// 🔥 Đảm bảo bạn đã tải file này từ Firebase Console
const serviceAccount = require("/etc/secrets/serviceAccountKey.json");

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
}

/**
 * Gửi thông báo đến danh sách token
 * @param {Array<string>} tokens - Mảng chứa FCM Tokens
 * @param {string} title
 * @param {string} body
 * @param {Object} data - Dữ liệu kèm theo (tùy chọn)
 */
export const sendMulticastNotification = async (tokens, title, body, data = {}) => {
  if (!tokens || tokens.length === 0) return;

  // Lọc các token null/undefined
  const validTokens = tokens.filter(t => t);
  if (validTokens.length === 0) return;

  const message = {
    notification: { title, body },
    data: data,
    tokens: validTokens,
  };

  try {
    const response = await admin.messaging().sendMulticast(message);
    console.log(`🔔 Sent: ${response.successCount} success, ${response.failureCount} failed.`);
  } catch (error) {
    console.error("❌ Error sending notification:", error);
  }
};

/**
 * Gửi thông báo đến 1 token
 */
export const sendNotification = async (token, title, body, data = {}) => {
    if (!token) return;
    try {
        await admin.messaging().send({
            token: token,
            notification: { title, body },
            data: data
        });
    } catch (error) {
        console.error("❌ Error sending single notification:", error);
    }
};