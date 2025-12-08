import cron from "node-cron";
import { pool } from "../db.js";
// ✅ SỬA: Chỉ import từ firebaseHelper, bỏ fcm.js
import { sendNotification } from "../utils/firebaseHelper.js";

/**
 * ==================================================================
 * 1. 📬 GỬI THÔNG BÁO HẸN GIỜ (Chạy mỗi phút)
 * ==================================================================
 */
const checkScheduledNotifications = async () => {
    const client = await pool.connect();
    try {
        // Tìm thông báo PENDING đã đến giờ gửi
        const pendingNotifications = await client.query(
            `SELECT * FROM notification
             WHERE status = 'PENDING'
             AND scheduled_at <= NOW()`
        );

        if (pendingNotifications.rows.length === 0) return;

        console.log(`🚀 [CRON] Found ${pendingNotifications.rows.length} scheduled notification(s) to send.`);

        for (const notification of pendingNotifications.rows) {
            try {
                // Lấy danh sách token người nhận
                const usersResult = await client.query(`
                    SELECT u.fcm_token
                    FROM user_notifications un
                    JOIN users u ON un.user_id = u.user_id
                    WHERE un.notification_id = $1 AND u.fcm_token IS NOT NULL
                `, [notification.notification_id]);

                const tokens = usersResult.rows.map(row => row.fcm_token).filter(t => t);

                if (tokens.length > 0) {
                    // ✅ SỬA: Dùng vòng lặp gửi từng người qua firebaseHelper
                    // (Thay vì dùng sendNotificationToUsers của fcm.js không tồn tại)
                    for (const token of tokens) {
                        // Lưu ý: sendNotification của bạn nhận tham số (token, title, body, data)
                        // Bạn có thể thêm object data nếu cần, ví dụ { type: notification.type }
                        sendNotification(token, notification.title, notification.content, { type: notification.type || "general" });
                    }

                    // Cập nhật trạng thái SENT
                    await client.query(
                        `UPDATE notification SET status = 'SENT' WHERE notification_id = $1`,
                        [notification.notification_id]
                    );
                    console.log(`✅ Sent notification ID: ${notification.notification_id}`);
                } else {
                    // Không có token nào -> FAILED
                    await client.query(
                        `UPDATE notification SET status = 'FAILED' WHERE notification_id = $1`,
                        [notification.notification_id]
                    );
                    console.log(`⚠️ Notification ID: ${notification.notification_id} has no valid tokens.`);
                }

            } catch (sendError) {
                console.error(`❌ Failed to send notification ID: ${notification.notification_id}`, sendError);
                await client.query(
                    `UPDATE notification SET status = 'FAILED' WHERE notification_id = $1`,
                    [notification.notification_id]
                );
            }
        }
    } catch (err) {
        console.error('❌ [CRON ERROR - Scheduled]', err);
    } finally {
        client.release();
    }
};

/**
 * ==================================================================
 * 2. 🎗 NHẮC NỢ PHÍ (Chạy 08:00 sáng mỗi ngày)
 * ==================================================================
 */
const checkAndRemindPayments = async () => {
  console.log("⏰ [CRON] Checking for payments due in 3 days...");

  try {
    const queryText = `
      SELECT
        f.title,
        f.amount,
        TO_CHAR(f.due_date, 'DD/MM/YYYY') as due_date_fmt,
        u.user_id,
        u.fcm_token
      FROM finances f
      JOIN user_finances uf ON f.id = uf.finance_id
      JOIN users u ON uf.user_id = u.user_id
      WHERE
        uf.status != 'da_thanh_toan'
        AND f.due_date = CURRENT_DATE + INTERVAL '3 days'
        AND u.fcm_token IS NOT NULL
        AND u.fcm_token != ''
    `;

    const result = await pool.query(queryText);

    if (result.rows.length === 0) {
      console.log("✅ [CRON] No payments due in 3 days found.");
      return;
    }

    console.log(`📢 [CRON] Found ${result.rows.length} payment reminders.`);

    for (const row of result.rows) {
      const title = "🎗 Nhắc hạn đóng phí";
      const body = `Khoản thu "${row.title}" sẽ hết hạn vào ngày ${row.due_date_fmt}. Vui lòng thanh toán sớm.`;
      sendNotification(row.fcm_token, title, body, { type: "finance" });
    }

  } catch (err) {
    console.error("❌ [CRON ERROR - Reminder]", err);
  }
};

/**
 * ==================================================================
 * 3. 🧹 DỌN DẸP THÔNG BÁO CŨ (Chạy 00:00 đêm mỗi ngày)
 * ==================================================================
 */
const cleanUpOldNotifications = async () => {
  console.log("🧹 [CRON] Starting cleanup of old notifications...");

  try {
    const client = await pool.connect();
    try {
        await client.query("BEGIN");

        // Tìm các thông báo hết hạn quá 60 ngày
        const findQuery = `
            SELECT notification_id FROM notification
            WHERE expired_date < NOW() - INTERVAL '60 days'
        `;
        const oldNotifications = await client.query(findQuery);

        if (oldNotifications.rows.length === 0) {
            console.log("✨ [CRON] No old notifications to delete today.");
            await client.query("ROLLBACK");
            return;
        }

        const idsToDelete = oldNotifications.rows.map(r => r.notification_id);

        // Xóa dữ liệu liên quan
        await client.query("DELETE FROM user_notifications WHERE notification_id = ANY($1)", [idsToDelete]);
        await client.query("DELETE FROM notification WHERE notification_id = ANY($1)", [idsToDelete]);

        await client.query("COMMIT");
        console.log(`✅ [CRON] Deleted ${idsToDelete.length} expired notifications.`);

    } catch (dbErr) {
        await client.query("ROLLBACK");
        throw dbErr;
    } finally {
        client.release();
    }

  } catch (err) {
    console.error("❌ [CRON ERROR - Cleanup]", err);
  }
};

/**
 * ==================================================================
 * KHỞI ĐỘNG TẤT CẢ SCHEDULER
 * ==================================================================
 */
export const startScheduler = () => {
  const timezone = { timezone: "Asia/Ho_Chi_Minh" };

  // 1. Gửi thông báo hẹn giờ: Chạy mỗi phút (* * * * *)
  cron.schedule("* * * * *", checkScheduledNotifications, timezone);

  // 2. Nhắc nợ: Chạy vào 08:00 sáng mỗi ngày
  cron.schedule("0 8 * * *", checkAndRemindPayments, timezone);

  // 3. Dọn dẹp: Chạy vào 00:00 đêm mỗi ngày
  cron.schedule("0 0 * * *", cleanUpOldNotifications, timezone);

  console.log("✅ Scheduler Service Started:");
  console.log("   - Scheduled Notifications: Every minute");
  console.log("   - Payment Reminders: Daily at 08:00");
  console.log("   - Cleanup Task: Daily at 00:00");
};

// Export để test thủ công nếu cần
export const manualRunScheduled = checkScheduledNotifications;
export const manualCheckReminder = checkAndRemindPayments;
export const manualCleanup = cleanUpOldNotifications;