import express from "express";
import { pool } from "../db.js";

const router = express.Router();

// (Đã xóa API POST /create ở đây để dùng bên create_notification.js)

// ==================================================================
// 🔥 API: XÓA THÔNG BÁO
// ==================================================================
router.delete("/delete/:id", async (req, res) => {
  const { id } = req.params;
  if (!id) return res.status(400).json({ error: "Thiếu ID thông báo." });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // Xóa liên kết user_notifications trước
    await client.query("DELETE FROM user_notifications WHERE notification_id = $1", [id]);

    // Xóa thông báo chính
    const result = await client.query("DELETE FROM notification WHERE notification_id = $1 RETURNING *", [id]);

    if (result.rowCount === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Không tìm thấy thông báo để xóa." });
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Đã xóa thông báo thành công." });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("❌ Error deleting notification:", err);
    res.status(500).json({ error: "Lỗi server khi xóa thông báo." });
  } finally {
    client.release();
  }
});

// ==================================================================
// 🔥 API: CẬP NHẬT THÔNG BÁO (Sửa & Gửi lại)
// ==================================================================
router.put("/update/:id", async (req, res) => {
  const { id } = req.params;
  const { title, content, type } = req.body;

  if (!id || !title) return res.status(400).json({ error: "Thiếu ID hoặc tiêu đề." });

  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    // 1. Cập nhật thông báo và set lại created_at = NOW() để nó nổi lên đầu danh sách
    const result = await client.query(
      `UPDATE notification
       SET title = $1, content = $2, type = $3, created_at = NOW()
       WHERE notification_id = $4
       RETURNING *`,
      [title, content, type, id]
    );

    if (result.rowCount === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Không tìm thấy thông báo để sửa." });
    }

    // 2. 🔥 QUAN TRỌNG: Reset trạng thái "Đã đọc" thành "Chưa đọc" cho tất cả user
    // Để cư dân thấy lại thông báo này như mới
    await client.query(
      `UPDATE user_notifications
       SET is_read = FALSE
       WHERE notification_id = $1`,
      [id]
    );

    await client.query("COMMIT");

    res.json({
        success: true,
        message: "Đã cập nhật và gửi lại thông báo cho cư dân.",
        data: result.rows[0]
    });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("❌ Error updating notification:", err);
    res.status(500).json({ error: "Lỗi server khi cập nhật thông báo." });
  } finally {
    client.release();
  }
});

// ==================================================================
// 👇 CÁC ROUTE GET DỮ LIỆU
// ==================================================================

// ✅ Route 1: Lấy tất cả thông báo do admin tạo (Cho màn hình Admin)
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
      WHERE ur.role_id = 2
      ORDER BY n.created_at DESC;
    `);

    res.json(result.rows);
  } catch (error) {
    console.error("Error fetching admin notifications:", error);
    res.status(500).json({ message: "Lỗi server" });
  }
});

// Lấy chi tiết thông báo theo ID
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
      res.json(result.rows[0]);
    } else {
      res.status(404).json({ error: "Notification not found" });
    }
  } catch (err) {
    console.error(`Error fetching notification ${id}:`, err);
    res.status(500).json({ error: "Server error" });
  }
});

// ✅ Route 2: Lấy tất cả thông báo của 1 user (Cho màn hình Cư dân)
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