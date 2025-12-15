import express from "express";
import { pool } from "../db.js";
import { sendNotification } from "../utils/firebaseHelper.js";

const router = express.Router();

/**
 * ==================================================================
 * 📝 API: TẠO THÔNG BÁO MỚI (Logic: Scheduler + Status)
 * (Đã cập nhật để Insert thêm file_url/file_type nếu có)
 * ==================================================================
 */
router.post("/create", async (req, res) => {
  // 🔥 Nhận thêm file_url, file_type (nếu logic của bạn có truyền ở đây)
  const { title, content, type, target_type, target_ids, scheduled_at, file_url, file_type } = req.body;

  if (!title || !content) return res.status(400).json({ error: "Thiếu tiêu đề hoặc nội dung." });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 🔥 LOGIC: Xác định trạng thái
    // Nếu không chọn giờ hoặc giờ chọn < hiện tại -> Là gửi ngay -> Status = 'SENT'
    const isInstant = !scheduled_at || new Date(scheduled_at) <= new Date();
    const initialStatus = isInstant ? 'SENT' : 'PENDING';
    const finalScheduledAt = scheduled_at || new Date();

    // 🔥 CẬP NHẬT: Insert thêm file_url và file_type
    const insertRes = await client.query(
      `INSERT INTO notification (
          title, content, type, created_by, created_at, scheduled_at, status,
          file_url, file_type
       )
       VALUES ($1, $2, $3, 1, NOW(), $4, $5, $6, $7)
       RETURNING notification_id`,
      [
        title,
        content,
        type || 'general',
        finalScheduledAt,
        initialStatus,
        file_url || null,  // 🔥 Lưu link file (nếu có)
        file_type || null  // 🔥 Lưu loại file (nếu có)
      ]
    );

    // Lấy ID vừa tạo (hỗ trợ cả trường hợp DB trả về id hoặc notification_id)
    const notificationId = insertRes.rows[0].notification_id || insertRes.rows[0].id;

    // --- XỬ LÝ NGƯỜI NHẬN (Giữ nguyên logic cũ) ---
    let recipientIds = [];

    if (target_type === 'all') {
        // Lấy tất cả user đang sinh sống
        const usersRes = await client.query("SELECT user_id FROM user_item WHERE is_living = TRUE");
        recipientIds = usersRes.rows.map(r => r.user_id);
    }
    else if (target_type === 'role') {
        // Logic lấy theo role (giữ nguyên placeholder nếu bạn chưa implement)
    }
    else if (target_type === 'specific') {
        recipientIds = target_ids || [];
    }

    if (recipientIds.length === 0) {
        await client.query("ROLLBACK");
        return res.status(400).json({ error: "Không tìm thấy người nhận phù hợp." });
    }

    // Insert vào bảng trung gian user_notifications
    for (const userId of recipientIds) {
        await client.query(
            `INSERT INTO user_notifications (user_id, notification_id, is_read) VALUES ($1, $2, FALSE)`,
            [userId, notificationId]
        );
    }

    // Gửi Firebase Notification (Chỉ gửi nếu là gửi ngay)
    if (isInstant) {
        const tokensRes = await client.query(
            `SELECT fcm_token FROM users WHERE user_id = ANY($1::int[]) AND fcm_token IS NOT NULL`,
            [recipientIds]
        );

        for (const row of tokensRes.rows) {
            // 🔥 Gửi kèm file_url trong data payload nếu cần
            const dataPayload = { type: type || 'general' };
            if (file_url) {
                dataPayload.file_url = file_url;
                dataPayload.file_type = file_type;
            }

            sendNotification(row.fcm_token, title, content, dataPayload)
                .catch(e => console.error("Lỗi gửi push lẻ:", e.message));
        }
    } else {
        console.log(`⏳ Đã lên lịch gửi thông báo ID ${notificationId} vào lúc ${finalScheduledAt}`);
    }

    await client.query("COMMIT");

    res.json({
        success: true,
        message: isInstant ? "Đã gửi thông báo thành công." : "Đã lên lịch gửi thông báo.",
        notificationId: notificationId
    });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Create Notification Error:", err);
    res.status(500).json({ error: "Lỗi server: " + err.message });
  } finally {
    client.release();
  }
});

/**
 * ==================================================================
 * ✏️ API: CẬP NHẬT THÔNG BÁO (Logic: Auto set status = 'SENT')
 * ==================================================================
 */
router.put("/update/:id", async (req, res) => {
  const { id } = req.params;
  const { title, content, type } = req.body;

  if (!id || !title) return res.status(400).json({ error: "Thiếu ID hoặc tiêu đề." });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // Cập nhật thông báo và set status = 'SENT'
    const result = await client.query(
      `UPDATE notification
       SET title = $1, content = $2, type = $3, status = 'SENT', created_at = NOW()
       WHERE notification_id = $4 RETURNING *`,
      [title, content, type, id]
    );

    if (result.rowCount === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Không tìm thấy thông báo." });
    }

    // Đánh dấu lại là chưa đọc để user chú ý
    await client.query(
      `UPDATE user_notifications SET is_read = FALSE WHERE notification_id = $1`,
      [id]
    );

    // Gửi lại thông báo đẩy (FCM)
    (async () => {
        try {
            const usersResult = await pool.query(`
                SELECT u.fcm_token
                FROM user_notifications un
                JOIN users u ON un.user_id = u.user_id
                WHERE un.notification_id = $1 AND u.fcm_token IS NOT NULL
            `, [id]);

            for (const row of usersResult.rows) {
                sendNotification(row.fcm_token, "Cập nhật: " + title, "Nội dung thông báo đã thay đổi.", { type: "update" });
            }
        } catch (e) { console.error("Lỗi gửi push update:", e); }
    })();

    await client.query("COMMIT");
    res.json({ success: true, message: "Đã cập nhật và gửi lại thông báo." });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Update Notification Error:", err);
    res.status(500).json({ error: "Lỗi server." });
  } finally {
    client.release();
  }
});

/**
 * ==================================================================
 * 🗑️ API: XÓA THÔNG BÁO
 * ==================================================================
 */
router.delete("/delete/:id", async (req, res) => {
  const { id } = req.params;
  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    await client.query("DELETE FROM user_notifications WHERE notification_id = $1", [id]);
    const result = await client.query("DELETE FROM notification WHERE notification_id = $1", [id]);

    if (result.rowCount === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Không tìm thấy thông báo." });
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Đã xóa thông báo." });
  } catch (err) {
    await client.query("ROLLBACK");
    res.status(500).json({ error: "Lỗi server." });
  } finally {
    client.release();
  }
});

/**
 * ==================================================================
 * 📋 API: LẤY DANH SÁCH THÔNG BÁO (ADMIN)
 * ==================================================================
 */
router.get("/sent", async (req, res) => {
  try {
    // 🔥 CẬP NHẬT: Thêm n.file_url và n.file_type
    const result = await pool.query(`
      SELECT n.*,
             n.notification_id,
             n.title,
             n.content,
             n.type,
             n.file_url,
             n.file_type,
             TO_CHAR(n.created_at, 'DD/MM/YYYY HH24:MI') as created_at,
             TO_CHAR(n.scheduled_at, 'DD/MM/YYYY HH24:MI') as expired_date,
             'Ban Quản Lý' as sender,
             n.status,
             FALSE as is_read
      FROM notification n
      ORDER BY n.created_at DESC
    `);
    res.json(result.rows);
  } catch (error) {
    console.error("Get sent notifications error:", error);
    res.status(500).json({ message: "Lỗi server" });
  }
});

/**
 * ==================================================================
 * 📋 API: LẤY DANH SÁCH THÔNG BÁO CỦA USER
 * ==================================================================
 */
router.get("/:userId", async (req, res) => {
  try {
    const { userId } = req.params;

    console.log("📡 Fetching notifications for user:", userId);

    // 🔥 CẬP NHẬT: Thêm n.file_url và n.file_type
    const result = await pool.query(`
      SELECT
             n.notification_id,
             n.title,
             n.content,
             n.type,
             n.file_url,
             n.file_type,
             TO_CHAR(n.created_at, 'DD/MM/YYYY HH24:MI') as created_at,
             TO_CHAR(n.scheduled_at, 'DD/MM/YYYY HH24:MI') as expired_date,
             'Ban Quản Lý' as sender,
             un.is_read
      FROM notification n
      JOIN user_notifications un ON n.notification_id = un.notification_id
      WHERE un.user_id = $1
      AND n.status = 'SENT'
      ORDER BY n.created_at DESC
    `, [userId]);

    console.log("✅ Found", result.rows.length, "notifications for user", userId);

    res.json(result.rows);
  } catch (error) {
    console.error("Get user notifications error:", error);
    res.status(500).json({ message: "Lỗi server" });
  }
});

/**
 * ==================================================================
 * 📌 API: LẤY CHI TIẾT 1 THÔNG BÁO
 * ==================================================================
 */
router.get("/detail/:id", async (req, res) => {
  try {
    const { id } = req.params;
    const { user_id } = req.query;

    // 🔥 CẬP NHẬT: Thêm n.file_url và n.file_type
    const result = await pool.query(`
      SELECT
             n.notification_id,
             n.title,
             n.content,
             n.type,
             n.file_url,
             n.file_type,
             TO_CHAR(n.created_at, 'DD/MM/YYYY HH24:MI') as created_at,
             TO_CHAR(n.scheduled_at, 'DD/MM/YYYY HH24:MI') as expired_date,
             'Ban Quản Lý' as sender,
             COALESCE(un.is_read, FALSE) as is_read
      FROM notification n
      LEFT JOIN user_notifications un ON n.notification_id = un.notification_id AND un.user_id = $2
      WHERE n.notification_id = $1
    `, [id, user_id || 0]);

    if (result.rows.length === 0) {
      return res.status(404).json({ error: "Không tìm thấy thông báo" });
    }

    res.json(result.rows[0]);
  } catch (error) {
    console.error("Get notification detail error:", error);
    res.status(500).json({ message: "Lỗi server" });
  }
});

/**
 * ==================================================================
 * ✅ API: ĐÁNH DẤU ĐÃ ĐỌC
 * ==================================================================
 */
router.put("/:notificationId/read", async (req, res) => {
  try {
    const { notificationId } = req.params;
    const { user_id } = req.body;

    await pool.query(
      `UPDATE user_notifications SET is_read = TRUE WHERE notification_id = $1 AND user_id = $2`,
      [notificationId, user_id]
    );

    res.json({ success: true });
  } catch (error) {
    console.error("Mark as read error:", error);
    res.status(500).json({ message: "Lỗi server" });
  }
});

export default router;