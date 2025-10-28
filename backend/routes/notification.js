import express from "express";
import { pool } from "../db.js";

const router = express.Router();

// ✅ Route 1 (mới): Lấy tất cả thông báo do admin tạo
router.get("/sent", async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT
        n.notification_id,
        n.title,
        n.content,
        n.type,
        n.created_at,
        TO_CHAR(n.expired_date, 'DD-MM-YYYY') AS expired_date,
        COALESCE(ui.full_name, 'Hệ thống') AS sender
      FROM notification n
      LEFT JOIN user_item ui ON n.created_by = ui.user_id
      LEFT JOIN userrole ur ON ui.user_id = ur.user_id
      LEFT JOIN role r ON ur.role_id = r.role_id
      WHERE ur.role_id = 2  -- 👈 chỉ lấy thông báo do admin tạo
      ORDER BY n.created_at DESC;
    `);

    res.json(result.rows);
  } catch (error) {
    console.error("Error fetching admin notifications:", error);
    res.status(500).json({ message: "Lỗi server" });
  }
});

// Lấy thông báo theo ID (dùng cho Android fetchNotificationTitle)
router.get("/detail/:id", async (req, res) => {
  const { id } = req.params;
  try {
    const result = await pool.query(
      `SELECT notification_id, title, content, type, created_at,
              TO_CHAR(expired_date, 'DD-MM-YYYY') AS expired_date,
              COALESCE(ui.full_name, 'Hệ thống') AS sender
       FROM notification n
       LEFT JOIN user_item ui ON n.created_by = ui.user_id
       WHERE n.notification_id = $1`,
      [id]
    );

    if (result.rows.length > 0) {
      res.json(result.rows[0]); // trả về object
    } else {
      res.status(404).json({ error: "Notification not found" });
    }
  } catch (err) {
    console.error(`Error fetching notification ${id}:`, err);
    res.status(500).json({ error: "Server error" });
  }
});

// ✅ Route 2: Lấy tất cả thông báo của 1 user (có trạng thái đã đọc)
router.get("/:userId", async (req, res) => {
  try {
    const { userId } = req.params;

    const result = await pool.query(
      `
      SELECT n.notification_id,
             n.title,
             n.content,
             n.type,
             n.created_at,
             COALESCE(ui.full_name, 'Hệ thống') AS sender,
             TO_CHAR(n.expired_date, 'DD-MM-YYYY') AS expired_date,
             un.is_read
      FROM notification n
      JOIN user_notifications un ON n.notification_id = un.notification_id
      LEFT JOIN user_item ui ON n.created_by = ui.user_id
      WHERE un.user_id = $1
      ORDER BY n.created_at DESC
      `,
      [userId]
    );

    res.json(result.rows);
  } catch (error) {
    console.error("Error fetching notifications:", error);
    res.status(500).json({ message: "Lỗi server" });
  }
});

// ✅ Route 3: Đánh dấu là đã đọc
router.put("/:notificationId/read", async (req, res) => {
  try {
    const { notificationId } = req.params;
    const { user_id } = req.body;

    if (!user_id) {
      return res.status(400).json({ message: "Thiếu user_id" });
    }

    const result = await pool.query(
      `
      UPDATE user_notifications
      SET is_read = TRUE
      WHERE notification_id = $1 AND user_id = $2
      RETURNING *;
      `,
      [notificationId, user_id]
    );

    if (result.rowCount === 0) {
      return res.status(404).json({ message: "Không tìm thấy thông báo của user này" });
    }

    res.json({ message: "Đã đánh dấu là đã đọc", updated: result.rows[0] });
  } catch (error) {
    console.error("Error updating notification:", error);
    res.status(500).json({ message: "Lỗi server" });
  }
});



export default router;
