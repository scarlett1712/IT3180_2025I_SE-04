import admin from "firebase-admin";
import { createRequire } from "module";
import fs from "fs"; // Import thêm fs để kiểm tra file tồn tại

const require = createRequire(import.meta.url);

// 🔥 LOGIC TÌM FILE KEY THÔNG MINH
let serviceAccount;

// 1. Đường dẫn trên Render (Secret Files luôn nằm ở đây)
const renderPath = "/etc/secrets/serviceAccountKey.json";

// 2. Đường dẫn trên máy Local (Dev)
const localPath = "../config/serviceAccountKey.json";

try {
  if (fs.existsSync(renderPath)) {
    console.log("🔑 Loading Firebase Key from Render Secrets...");
    serviceAccount = require(renderPath);
  } else {
    console.log("💻 Loading Firebase Key from Local Config...");
    serviceAccount = require(localPath);
  }
} catch (error) {
  console.error("❌ CRITICAL: Could not load Firebase Service Account Key!");
  console.error("Please check if 'serviceAccountKey.json' exists in '/etc/secrets/' (Render) or 'backend/config/' (Local).");
  console.error(error);
}

if (!admin.apps.length && serviceAccount) {
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