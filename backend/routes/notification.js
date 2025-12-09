import express from "express";
import { pool } from "../db.js";
import { sendNotification } from "../utils/firebaseHelper.js"; // 🔥 Import hàm gửi

const router = express.Router();

/**
 * ==================================================================
 * 📝 API: TẠO THÔNG BÁO MỚI (QUAN TRỌNG NHẤT)
 * ==================================================================
 */
router.post("/create", async (req, res) => {
  const { title, content, type, target_type, target_ids, scheduled_at } = req.body;
  // target_type: 'all' | 'role' | 'specific'
  // target_ids: Mảng ID (nếu specific) hoặc ID role (nếu role)

  if (!title || !content) return res.status(400).json({ error: "Thiếu tiêu đề hoặc nội dung." });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Kiểm tra xem là Gửi ngay hay Hẹn giờ
    // Nếu không có scheduled_at hoặc thời gian <= hiện tại => Gửi ngay
    const isInstant = !scheduled_at || new Date(scheduled_at) <= new Date();

    // Nếu gửi ngay thì status là SENT (sau khi gửi xong), hẹn giờ thì là PENDING
    const initialStatus = isInstant ? 'SENT' : 'PENDING';
    const finalScheduledAt = scheduled_at || new Date(); // Nếu null thì lấy giờ hiện tại

    // 2. Tạo thông báo trong bảng chính
    const insertRes = await client.query(
      `INSERT INTO notification (title, content, type, created_by, created_at, scheduled_at, status)
       VALUES ($1, $2, $3, 1, NOW(), $4, $5) -- Giả sử admin ID = 1
       RETURNING notification_id`,
      [title, content, type || 'general', finalScheduledAt, initialStatus]
    );
    const notificationId = insertRes.rows[0].notification_id;

    // 3. Xác định danh sách người nhận (user_ids)
    let recipientIds = [];

    if (target_type === 'all') {
        // Gửi cho tất cả User active
        const usersRes = await client.query("SELECT user_id FROM user_item WHERE is_living = TRUE");
        recipientIds = usersRes.rows.map(r => r.user_id);
    }
    else if (target_type === 'role') {
        // Gửi theo Role (VD: Chỉ gửi cho chủ hộ)
        // target_ids ở đây là mảng role_id
        // Cần join bảng userrole hoặc logic tùy DB của bạn. Ví dụ đơn giản:
        // SELECT user_id FROM userrole WHERE role_id = ANY($1)
    }
    else if (target_type === 'specific') {
        // Gửi cho danh sách cụ thể
        recipientIds = target_ids || [];
    }

    if (recipientIds.length === 0) {
        await client.query("ROLLBACK");
        return res.status(400).json({ error: "Không tìm thấy người nhận phù hợp." });
    }

    // 4. Lưu vào bảng user_notifications (Để user thấy trong App)
    for (const userId of recipientIds) {
        await client.query(
            `INSERT INTO user_notifications (user_id, notification_id, is_read) VALUES ($1, $2, FALSE)`,
            [userId, notificationId]
        );
    }

    // 5. 🔥 LOGIC GỬI PUSH NOTIFICATION
    if (isInstant) {
        // == TRƯỜNG HỢP GỬI NGAY ==
        // Lấy token của những người nhận
        const tokensRes = await client.query(
            `SELECT fcm_token FROM users WHERE user_id = ANY($1::int[]) AND fcm_token IS NOT NULL`,
            [recipientIds]
        );

        // Gửi loop từng người
        for (const row of tokensRes.rows) {
            // Không await để trả response nhanh cho Admin, việc gửi cứ chạy ngầm
            sendNotification(row.fcm_token, title, content, { type: type || 'general' })
                .catch(e => console.error("Lỗi gửi push lẻ:", e.message));
        }
    }
    else {
        // == TRƯỜNG HỢP HẸN GIỜ ==
        // KHÔNG LÀM GÌ CẢ. Scheduler sẽ lo việc này.
        console.log(`⏳ Đã lên lịch gửi thông báo ID ${notificationId} vào lúc ${finalScheduledAt}`);
    }

    await client.query("COMMIT");

    res.json({
        success: true,
        message: isInstant ? "Đã gửi thông báo thành công." : "Đã lên lịch gửi thông báo."
    });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Create Notification Error:", err);
    res.status(500).json({ error: "Lỗi server." });
  } finally {
    client.release();
  }
});

/**
 * ==================================================================
 * ✏️ API: CẬP NHẬT THÔNG BÁO
 * ==================================================================
 */
router.put("/update/:id", async (req, res) => {
  const { id } = req.params;
  const { title, content, type } = req.body;

  if (!id || !title) return res.status(400).json({ error: "Thiếu ID hoặc tiêu đề." });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Cập nhật nội dung
    const result = await client.query(
      `UPDATE notification
       SET title = $1, content = $2, type = $3, created_at = NOW()
       WHERE notification_id = $4 RETURNING *`,
      [title, content, type, id]
    );

    if (result.rowCount === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Không tìm thấy thông báo." });
    }

    // 2. Reset trạng thái "Chưa đọc" cho user
    await client.query(
      `UPDATE user_notifications SET is_read = FALSE WHERE notification_id = $1`,
      [id]
    );

    // 3. 🔥 GỬI PUSH BÁO CẬP NHẬT (Logic bổ sung)
    // Để cư dân biết thông báo đã thay đổi nội dung
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

    // Xóa bảng phụ trước
    await client.query("DELETE FROM user_notifications WHERE notification_id = $1", [id]);
    // Xóa bảng chính
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
 * 📋 CÁC API GET (Lấy danh sách)
 * ==================================================================
 */

// 1. Cho Admin: Lấy danh sách đã gửi
router.get("/sent", async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT n.*,
             TO_CHAR(n.created_at, 'DD/MM/YYYY HH24:MI') as date_fmt,
             n.status -- Admin cần xem status (PENDING/SENT)
      FROM notification n
      ORDER BY n.created_at DESC
    `);
    res.json(result.rows);
  } catch (error) {
    res.status(500).json({ message: "Lỗi server" });
  }
});

// 2. Cho Cư dân: Lấy danh sách của mình
router.get("/:userId", async (req, res) => {
  try {
    const { userId } = req.params;
    const result = await pool.query(`
      SELECT n.notification_id, n.title, n.content, n.type,
             TO_CHAR(n.created_at, 'DD/MM/YYYY HH24:MI') as date_fmt,
             un.is_read
      FROM notification n
      JOIN user_notifications un ON n.notification_id = un.notification_id
      WHERE un.user_id = $1
      AND n.status = 'SENT' -- 🔥 Quan trọng: User chỉ thấy cái nào đã SENT
      ORDER BY n.created_at DESC
    `, [userId]);
    res.json(result.rows);
  } catch (error) {
    res.status(500).json({ message: "Lỗi server" });
  }
});

// 3. Đánh dấu đã đọc
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
    res.status(500).json({ message: "Lỗi server" });
  }
});

export default router;