import express from "express";
import cors from "cors";
import dotenv from "dotenv";

// ✅ Import routes
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
import financeRoutes from "./routes/finance.js";
import invoiceRoute from "./routes/invoice.js";
import profileRequestRoutes from "./routes/profileRequests.js";
import maintenanceRoutes from "./routes/maintenance.js";
import reportsRoutes from "./routes/reports.js";
import authorityRoutes from "./routes/authority.js";
import apartmentRoutes from "./routes/apartment.js";
import appUpdateRoutes from "./routes/app_update.js";
import testDbRoutes from "./routes/test_db.js";
import { pool } from "./db.js";

import { startScheduler } from "./cron/scheduler.js";

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

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
  console.log("===============================");
  next();
});

// ✅ Dùng tất cả routes
app.use("/api/users", userRoutes);
app.use("/api/user_item", userItemRoutes);
app.use("/api/replies", replyRoutes);
app.use("/api/residents", residentRoutes);
app.use("/api/avatar", avatarRoutes);
app.use("/api/notification", notificationRoutes);
app.use("/api/create_notification", createNotificationRoutes);
app.use("/api/changepassword", changePasswordRoutes);
app.use("/api/create_user", createUserRoutes);
app.use("/api/feedback", feedbackRoutes);
app.use("/api/feedback", feedbackReplyRoutes);
app.use("/api/finance", financeRoutes);
app.use("/api/invoice", invoiceRoute);
app.use("/api/profile-requests", profileRequestRoutes);
app.use("/api/maintenance", maintenanceRoutes);
app.use("/api/reports", reportsRoutes);
app.use("/api/authority", authorityRoutes);
app.use("/api/apartments", apartmentRoutes);

// 🔥 Route cập nhật app (Mới)
app.use("/api/app-update", appUpdateRoutes);

// 🔥 Route test database (Để kiểm tra kết nối)
app.use("/api/test", testDbRoutes);

// ✅ Health check
app.get("/", (req, res) => {
  res.json({
    message: "✅ ENoti backend running!",
    timestamp: new Date().toISOString(),
  });
});

// 🔥 Test database connection - endpoint đơn giản
app.get("/api/test-db", async (req, res) => {
  try {
    const result = await pool.query("SELECT NOW() as current_time, version() as pg_version");
    
    // Đếm số bảng
    const tablesResult = await pool.query(`
      SELECT table_name 
      FROM information_schema.tables 
      WHERE table_schema = 'public'
      ORDER BY table_name
    `);
    
    // Đếm số users
    let usersCount = 0;
    try {
      const usersResult = await pool.query("SELECT COUNT(*) as count FROM users");
      usersCount = parseInt(usersResult.rows[0].count);
    } catch (e) {
      console.log("Table users chưa tồn tại hoặc lỗi:", e.message);
    }
    
    res.json({
      success: true,
      message: "✅ Database kết nối thành công!",
      database_info: {
        current_time: result.rows[0].current_time,
        pg_version: result.rows[0].pg_version.split(",")[0],
        tables_count: tablesResult.rows.length,
        tables: tablesResult.rows.map(r => r.table_name),
        users_count: usersCount
      },
      connection_config: {
        host: process.env.PGHOST || "NOT SET",
        port: process.env.PGPORT || "NOT SET",
        database: process.env.PGDATABASE || "NOT SET",
        user: process.env.PGUSER || "NOT SET",
        password: process.env.PGPASSWORD ? "***SET***" : "NOT SET",
        has_database_url: !!process.env.DATABASE_URL
      }
    });
  } catch (err) {
    console.error("❌ Database connection error:", err);
    res.status(500).json({
      success: false,
      error: err.message,
      code: err.code,
      connection_config: {
        host: process.env.PGHOST || "NOT SET",
        port: process.env.PGPORT || "NOT SET",
        database: process.env.PGDATABASE || "NOT SET",
        user: process.env.PGUSER || "NOT SET",
        password: process.env.PGPASSWORD ? "***SET***" : "NOT SET",
        has_database_url: !!process.env.DATABASE_URL
      }
    });
  }
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

// 🔥 Initialize database tables on startup
const initializeDatabase = async () => {
  console.log("🔧 Initializing database tables...");
  try {
    await createFinanceTables();
    await createInvoiceTable();
    // START: Call new table creation function
    await createAuthorityMessagesTable(); 
    // END: Call
    console.log("✅ All tables initialized successfully");
  } catch (error) {
    console.error("❌ Error initializing database:", error);
  }
};

// ✅ Start server
app.listen(PORT, async () => {
  console.log(`🚀 Server started on port ${PORT}`);
  console.log(`📍 Health check: OK`);

  // 🔥 Initialize database tables
  await initializeDatabase();

  // Start scheduler
  startScheduler();
});