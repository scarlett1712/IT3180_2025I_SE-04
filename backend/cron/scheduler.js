import cron from "node-cron";
import { pool } from "../db.js";
import { sendNotification } from "../utils/firebaseHelper.js";

/**
 * Hàm kiểm tra và gửi thông báo nhắc nợ
 */
const checkAndRemindPayments = async () => {
  console.log("⏰ [CRON] Checking for payments due in 3 days...");

  try {
    // 1. Tìm các khoản thu sẽ hết hạn sau đúng 3 ngày nữa
    // Và người dùng chưa thanh toán, và có fcm_token
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
        uf.status != 'da_thanh_toan' -- Chưa đóng
        AND f.due_date = CURRENT_DATE + INTERVAL '3 days' -- Hạn là 3 ngày tới
        AND u.fcm_token IS NOT NULL
        AND u.fcm_token != ''
    `;

    const result = await pool.query(queryText);

    if (result.rows.length === 0) {
      console.log("✅ [CRON] No payments due in 3 days found.");
      return;
    }

    console.log(`📢 [CRON] Found ${result.rows.length} reminders to send.`);

    // 2. Gửi thông báo cho từng người
    // (Có thể tối ưu bằng multicast nếu gom nhóm, nhưng loop đơn giản cũng ổn với quy mô nhỏ)
    for (const row of result.rows) {
      const title = "🎗 Nhắc hạn đóng phí";
      const body = `Khoản thu "${row.title}" sẽ hết hạn vào ngày ${row.due_date_fmt}. Vui lòng thanh toán sớm.`;

      // Gửi thông báo (Không cần await để chạy song song cho nhanh)
      sendNotification(row.fcm_token, title, body, { type: "finance" });
    }

  } catch (err) {
    console.error("❌ [CRON ERROR]", err);
  }
};

/**
 * Khởi động Scheduler
 */
export const startScheduler = () => {
  // Cấu hình chạy vào 08:00 sáng mỗi ngày
  // Cú pháp Cron: Phút Giờ Ngày Tháng Thứ
  cron.schedule("0 8 * * *", () => {
    console.log("🌞 [CRON] Running daily payment reminder task...");
    checkAndRemindPayments();
  }, {
    timezone: "Asia/Ho_Chi_Minh" // Đảm bảo chạy đúng giờ Việt Nam
  });

  console.log("✅ Payment Reminder Scheduler started (Runs daily at 08:00 VN).");
};

// Export hàm check để có thể gọi thủ công (Test)
export const manualCheck = checkAndRemindPayments;