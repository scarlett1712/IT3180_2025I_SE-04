// ... (existing imports)
import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import admin from "firebase-admin";

dotenv.config();

const app = express();

// ✅ Khởi tạo Firebase Admin SDK
const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

// ✅ Cho phép truy cập static
app.use(express.static("public"));

// ✅ Bật CORS
app.use(cors());

// ✅ Bắt buộc: parse JSON & form-data trước
app.use(express.json({ limit: "10mb" }));
app.use(express.urlencoded({ extended: true, limit: "10mb" }));

// ✅ Middleware log để debug request
app.use((req, res, next) => {
  console.log("=== 🔥 INCOMING REQUEST ===");
  console.log("Method:", req.method);
  console.log("Path:", req.path);
  console.log("Content-Type:", req.headers["content-type"]);
  // console.log("Body:", req.body); // Uncomment for full body debug
  console.log("===============================");
  next();
});

// ✅ Import routes (ADD THIS LINE)
import userRoutes from "./routes/users.js";
import userItemRoutes from "./routes/user_item.js";
import replyRoutes from "./routes/reply.js";
import residentRoutes from "./routes/resident.js";
import avatarRoutes from "./routes/avatar.js";
import notificationRoutes from "./routes/notification.js";
import createNotificationRoutes from "./routes/create_notification.js";
import changePasswordRoutes from "./routes/change_password.js";
import createUserRoutes from "./routes/create_user.js";
import feedbackRoutes from "./routes/feedback.js";
import feedbackReplyRoutes from "./routes/feedbackReply.js";
import financeRoutes, { createFinanceTables } from "./routes/finance.js";

// ✅ Dùng tất cả routes
app.use("/api/users", userRoutes);
app.use("/api/user_item", userItemRoutes);
app.use("/api/feedback", feedbackRoutes);
app.use("/api/replies", replyRoutes);
app.use("/api/residents", residentRoutes);
app.use("/api/avatar", avatarRoutes);
app.use("/api/notification", notificationRoutes);
app.use("/api/create_notification", createNotificationRoutes);
app.use("/api/changepassword", changePasswordRoutes);
app.use("/api/create_user", createUserRoutes);
app.use("/api/feedback", feedbackReplyRoutes);
app.use("/api/finance", financeRoutes);

// ✅ Health check
// ... (rest of the file remains the same)
app.get("/", (req, res) => {
  res.json({
    message: "✅ ENoti backend running!",
    timestamp: new Date().toISOString(),
  });
});

// ✅ Debug endpoint để kiểm tra body
app.post("/api/debug", (req, res) => {
  res.json({
    success: true,
    body: req.body,
    bodyExists: !!req.body,
    bodyType: typeof req.body,
    headers: req.headers,
  });
});

// ✅ Start server
const PORT = process.env.PORT || 5000;
app.listen(PORT, () => {
  console.log(`🚀 Server started on port ${PORT}`);
  console.log(`📍 Health check: http://localhost:${PORT}/`);
  console.log(`📍 Debug endpoint: http://localhost:${PORT}/api/debug`);
});