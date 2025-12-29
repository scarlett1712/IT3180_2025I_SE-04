import express from "express";
import { pool } from "../db.js";

const router = express.Router();

// 🔥 Endpoint để test kết nối database
router.get("/test-connection", async (req, res) => {
  try {
    // Test kết nối cơ bản
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
        pg_version: result.rows[0].pg_version.split(",")[0], // Lấy phiên bản đầu tiên
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

export default router;

