import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import admin from "firebase-admin";

// Import all your existing routes first
import userRoutes from "./routes/users.js";
import userItemRoutes from "./routes/user_item.js";
import feedbackRoutes from "./routes/feedback.js";
import replyRoutes from "./routes/reply.js";
import residentRoutes from "./routes/resident.js";
import avatarRoutes from "./routes/avatar.js";
import notificationRoutes from "./routes/notification.js";
import createNotificationRoutes from "./routes/create_notification.js";
import changePasswordRoutes from "./routes/change_password.js";
import createUserRoutes from "./routes/create_user.js";

// [NEW] Now, import the finance router and its table creation function
import financeRoutes, { createFinanceTables } from "./routes/finance.js";

dotenv.config();
const app = express();

// --- Firebase Admin SDK Initialization (if you use it) ---
// const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
// admin.initializeApp({
//   credential: admin.credential.cert(serviceAccount),
// });

// --- Standard Middlewares ---
app.use(cors());
app.use(express.static('public'));

// IMPORTANT: Standard body parsers. This is the correct and only way needed to parse JSON.
app.use(express.json({ limit: "10mb" }));
app.use(express.urlencoded({ extended: true, limit: "10mb" }));


// --- Route Definitions ---
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

// [NEW] Use the finance routes with a base path
app.use("/api/finance", financeRoutes);


// --- Server Health Check ---
app.get("/", (req, res) => {
  res.json({ message: "✅ ENoti backend is up and running!" });
});


// --- Server Initialization ---
const PORT = process.env.PORT || 5000;
app.listen(PORT, () => {
  console.log(`🚀 Server started on port ${PORT}`);
  
  // [NEW] Initialize the finance tables on server start
  createFinanceTables();
});
