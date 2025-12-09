import cron from "node-cron";
import { pool } from "../db.js";
import { sendNotification } from "../utils/firebaseHelper.js";

/**
 * ==================================================================
 * 1. 📬 GỬI THÔNG BÁO HẸN GIỜ (QUAN TRỌNG NHẤT)
 * - Chạy: Mỗi phút một lần (* * * * *)
 * - Nhiệm vụ: Quét các thông báo trạng thái 'PENDING' đã đến giờ gửi.
 * ==================================================================
 */
const checkScheduledNotifications = async () => {
    const client = await pool.connect();
    try {
        // 1. Tìm thông báo PENDING mà thời gian gửi (scheduled_at) <= thời gian hiện tại
        const pendingNotifications = await client.query(
            `SELECT * FROM notification
             WHERE status = 'PENDING'
             AND scheduled_at <= NOW()`
        );

        // Nếu không có gì để gửi thì dừng luôn cho nhẹ server
        if (pendingNotifications.rows.length === 0) return;

        console.log(`🚀 [CRON] Bắt đầu gửi ${pendingNotifications.rows.length} thông báo hẹn giờ...`);

        for (const notification of pendingNotifications.rows) {
            try {
                // 2. Lấy danh sách token của những người cần nhận thông báo này
                // (Join bảng user_notifications và users)
                const usersResult = await client.query(`
                    SELECT u.fcm_token
                    FROM user_notifications un
                    JOIN users u ON un.user_id = u.user_id
                    WHERE un.notification_id = $1
                    AND u.fcm_token IS NOT NULL
                    AND u.fcm_token != ''
                `, [notification.notification_id]);

                const tokens = usersResult.rows.map(row => row.fcm_token);

                if (tokens.length > 0) {
                    console.log(`--> Đang gửi ID ${notification.notification_id} tới ${tokens.length} thiết bị.`);

                    // 3. Gửi thông báo qua Firebase (Loop để gửi từng người đảm bảo an toàn)
                    for (const token of tokens) {
                        await sendNotification(
                            token,
                            notification.title,
                            notification.content,
                            { type: notification.type || "general" }
                        );
                    }

                    // 4. Cập nhật trạng thái thành SENT (Đã gửi)
                    await client.query(
                        `UPDATE notification SET status = 'SENT' WHERE notification_id = $1`,
                        [notification.notification_id]
                    );
                    console.log(`✅ Đã gửi xong ID: ${notification.notification_id}`);

                } else {
                    // Trường hợp thông báo không có người nhận (hoặc user chưa có token)
                    // Vẫn đánh dấu là SENT hoặc FAILED để Cron không quét lại lần sau
                    await client.query(
                        `UPDATE notification SET status = 'FAILED' WHERE notification_id = $1`,
                        [notification.notification_id]
                    );
                    console.log(`⚠️ ID: ${notification.notification_id} không có token người nhận hợp lệ.`);
                }

            } catch (sendError) {
                console.error(`❌ Lỗi khi xử lý thông báo ID: ${notification.notification_id}`, sendError);
                // Nếu lỗi, đánh dấu FAILED để không bị kẹt loop
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
 * 2. 🎗 NHẮC NỢ PHÍ (TỰ ĐỘNG)
 * - Chạy: 08:00 sáng hàng ngày
 * ==================================================================
 */
const checkAndRemindPayments = async () => {
  console.log("⏰ [CRON] Đang kiểm tra các khoản phí sắp hết hạn...");

  try {
    // Tìm các khoản phí chưa thanh toán và sẽ hết hạn trong đúng 3 ngày tới
    const queryText = `
      SELECT
        f.title,
        TO_CHAR(f.due_date, 'DD/MM/YYYY') as due_date_fmt,
        u.fcm_token
      FROM finances f
      JOIN user_finances uf ON f.id = uf.finance_id
      JOIN users u ON uf.user_id = u.user_id
      WHERE
        uf.status != 'da_thanh_toan'
        AND f.due_date = CURRENT_DATE + INTERVAL '3 days' -- Nhắc trước 3 ngày
        AND u.fcm_token IS NOT NULL
        AND u.fcm_token != ''
    `;

    const result = await pool.query(queryText);

    if (result.rows.length === 0) {
      console.log("✅ Không có khoản phí nào sắp hết hạn.");
      return;
    }

    console.log(`📢 Tìm thấy ${result.rows.length} người cần nhắc phí.`);

    for (const row of result.rows) {
      const title = "🎗 Nhắc hạn đóng phí";
      const body = `Khoản thu "${row.title}" sẽ hết hạn vào ngày ${row.due_date_fmt}. Vui lòng thanh toán sớm.`;

      // Gửi thông báo
      await sendNotification(row.fcm_token, title, body, { type: "finance" });
    }

  } catch (err) {
    console.error("❌ [CRON ERROR - Reminder]", err);
  }
};

/**
 * ==================================================================
 * 3. 🧹 DỌN DẸP THÔNG BÁO CŨ (Maintenance)
 * - Chạy: 00:00 đêm hàng ngày
 * ==================================================================
 */
const cleanUpOldNotifications = async () => {
  console.log("🧹 [CRON] Bắt đầu dọn dẹp thông báo cũ...");

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // Tìm các thông báo đã hết hạn quá 60 ngày
    const findQuery = `
        SELECT notification_id FROM notification
        WHERE expired_date < NOW() - INTERVAL '60 days'
    `;
    const oldNotifications = await client.query(findQuery);

    if (oldNotifications.rows.length === 0) {
        console.log("✨ Hệ thống sạch sẽ, không có gì để xóa.");
        await client.query("ROLLBACK");
        return;
    }

    const idsToDelete = oldNotifications.rows.map(r => r.notification_id);

    // Xóa dữ liệu liên quan trong bảng phụ trước
    await client.query("DELETE FROM user_notifications WHERE notification_id = ANY($1)", [idsToDelete]);
    // Xóa bảng chính
    await client.query("DELETE FROM notification WHERE notification_id = ANY($1)", [idsToDelete]);

    await client.query("COMMIT");
    console.log(`✅ [CRON] Đã xóa vĩnh viễn ${idsToDelete.length} thông báo cũ.`);

  } catch (dbErr) {
    await client.query("ROLLBACK");
    console.error("❌ [CRON ERROR - Cleanup]", dbErr);
  } finally {
    client.release();
  }
};

/**
 * ==================================================================
 * KHỞI ĐỘNG TẤT CẢ SCHEDULER
 * ==================================================================
 */
export const startScheduler = () => {
  // Cấu hình múi giờ Việt Nam để cron chạy đúng giờ
  const options = { timezone: "Asia/Ho_Chi_Minh" };

  // 1. Gửi thông báo hẹn giờ: Chạy mỗi phút
  cron.schedule("* * * * *", checkScheduledNotifications, options);

  // 2. Nhắc nợ: Chạy vào 08:00 sáng mỗi ngày
  cron.schedule("0 8 * * *", checkAndRemindPayments, options);

  // 3. Dọn dẹp: Chạy vào 00:00 đêm mỗi ngày
  cron.schedule("0 0 * * *", cleanUpOldNotifications, options);

  console.log("✅ Scheduler Service đã khởi động:");
  console.log("   - Scheduled Notifications: Mỗi phút");
  console.log("   - Payment Reminders: 08:00 Sáng hàng ngày");
  console.log("   - Cleanup Task: 00:00 Đêm hàng ngày");
};

// Export các hàm để test thủ công nếu cần (ví dụ gọi từ API /test-cron)
export const manualRunScheduled = checkScheduledNotifications;
export const manualCheckReminder = checkAndRemindPayments;
export const manualCleanup = cleanUpOldNotifications;