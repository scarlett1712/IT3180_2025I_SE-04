import express from "express";
import cors from "cors";
import dotenv from "dotenv";

dotenv.config();

const app = express();

app.use(express.static('public'));

// ✅ CORS đơn giản
app.use(cors());

// ✅ MIDDLEWARE QUAN TRỌNG: Xử lý raw body trước
app.use((req, res, next) => {
  console.log("=== 🔥 RAW REQUEST ===");
  console.log("Method:", req.method);
  console.log("Path:", req.path);
  console.log("Content-Type:", req.headers['content-type']);

  if (req.method === 'POST' || req.method === 'PUT') {
    let data = '';
    req.on('data', chunk => {
      data += chunk;
    });
    req.on('end', () => {
      console.log("🔥 Raw body data:", data);
      try {
        if (data && req.headers['content-type']?.includes('application/json')) {
          req.body = JSON.parse(data);
        } else {
          req.body = data || {};
        }
        console.log("🔥 Parsed body:", req.body);
        next();
      } catch (e) {
        console.log("🔥 Parse error:", e.message);
        req.body = {};
        next();
      }
    });
  } else {
    next();
  }
});

// ✅ JSON parser thông thường (sẽ không chạy nếu body đã được xử lý ở trên)
app.use(express.json({
  limit: "10mb",
  verify: (req, res, buf) => {
    if (req.body === undefined) {
      console.log("📦 express.json() is processing body");
    }
  }
}));

app.use(express.urlencoded({
  extended: true,
  limit: "10mb"
}));

// ✅ Debug middleware sau khi parse
app.use((req, res, next) => {
  console.log("=== ✅ FINAL BODY ===");
  console.log("Body:", req.body);
  console.log("Body type:", typeof req.body);
  console.log("=== ✅ END DEBUG ===");
  next();
});

// Import routes
import { pool } from "./db.js";
import userRoutes from "./routes/users.js";
import userItemRoutes from "./routes/user_item.js";
import feedbackRoutes from "./routes/feedback.js";
import replyRoutes from "./routes/reply.js";
import residentRoutes from "./routes/resident.js";
import avatarRoutes from "./routes/avatar.js";

// Routes
app.use("/api/users", userRoutes);
app.use("/api/user_item", userItemRoutes);
app.use("/api/feedback", feedbackRoutes);
app.use("/api/replies", replyRoutes);
app.use("/api/residents", residentRoutes);
app.use("/api/avatar", avatarRoutes);

// Health check
app.get("/", (req, res) => {
  res.json({
    message: "✅ ENoti backend running!",
    timestamp: new Date().toISOString()
  });
});

// Test endpoint đặc biệt
app.post("/api/debug", (req, res) => {
  res.json({
    success: true,
    body: req.body,
    bodyExists: !!req.body,
    bodyType: typeof req.body,
    headers: req.headers
  });
});

const PORT = process.env.PORT || 5000;
app.listen(PORT, () => {
  console.log(`🚀 Server started on port ${PORT}`);
  console.log(`📍 Health check: http://localhost:${PORT}/`);
  console.log(`📍 Debug endpoint: http://localhost:${PORT}/api/debug`);
});