import express from "express";
import { pool } from "../db.js";
// 🔥 Import Helper để gửi thông báo
import { sendMulticastNotification } from "../utils/firebaseHelper.js";

const router = express.Router();

// ==================================================================
// 🚀 API: Tạo thông báo mới & Gửi Push Notification (Cho Admin)
// ==================================================================
router.post("/create", async (req, res) => {
  const { title, content, type, created_by, target_user_ids, send_to_all } = req.body;

  // Validate
  if (!title || !created_by) {
    return res.status(400).json({ error: "Thiếu tiêu đề hoặc người tạo." });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1️⃣ Tạo thông báo trong bảng 'notification'
    const notiRes = await client.query(
      `INSERT INTO notification (title, content, type, created_by, created_at, expired_date)
       VALUES ($1, $2, $3, $4, NOW(), NOW() + INTERVAL '30 days')
       RETURNING notification_id`,
      [title, content, type || 'Hành chính', created_by]
    );
    const notificationId = notiRes.rows[0].notification_id;

    // 2️⃣ Xác định danh sách người nhận (User IDs)
    let recipients = [];
    if (send_to_all) {
      // Lấy tất cả cư dân (trừ Admin và người tạo)
      const allUsersRes = await client.query(`
        SELECT u.user_id
        FROM users u
        JOIN userrole ur ON u.user_id = ur.user_id
        WHERE ur.role_id != 2 -- Không gửi cho Admin khác
      `);
      recipients = allUsersRes.rows.map(r => r.user_id);
    } else if (Array.isArray(target_user_ids) && target_user_ids.length > 0) {
      recipients = target_user_ids;
    }

    // 3️⃣ Lưu vào bảng 'user_notifications' (Để hiển thị trong App)
    if (recipients.length > 0) {
      // Dùng vòng lặp hoặc unnest để insert hàng loạt
      for (const userId of recipients) {
        await client.query(
          `INSERT INTO user_notifications (notification_id, user_id, is_read)
           VALUES ($1, $2, FALSE)
           ON CONFLICT DO NOTHING`,
          [notificationId, userId]
        );
      }

      // 4️⃣ Gửi Push Notification qua Firebase (FCM)
      // Lấy Token của những người nhận
      const tokensRes = await client.query(
        `SELECT fcm_token FROM users WHERE user_id = ANY($1) AND fcm_token IS NOT NULL`,
        [recipients]
      );
      const tokens = tokensRes.rows.map(r => r.fcm_token);

      if (tokens.length > 0) {
        // Gửi thông báo (Fire-and-forget)
        sendMulticastNotification(
          tokens,
          title, // Tiêu đề thông báo
          content, // Nội dung
          {
             type: "notification_detail",
             id: notificationId.toString()
          } // Data kèm theo để mở đúng màn hình khi bấm vào
        );
      }
    }

    await client.query("COMMIT");
    res.json({
      success: true,
      message: `Đã gửi thông báo tới ${recipients.length} cư dân.`
    });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Error creating notification:", err);
    res.status(500).json({ error: "Lỗi server khi tạo thông báo." });
  } finally {
    client.release();
  }
});

// ==================================================================
// 👇 CÁC ROUTE CŨ (GIỮ NGUYÊN)
// ==================================================================

// ✅ Route 1: Lấy tất cả thông báo do admin tạo
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

// Lấy thông báo theo ID
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

// ✅ Route 2: Lấy tất cả thông báo của 1 user
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