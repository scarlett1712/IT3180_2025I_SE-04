import cron from 'node-cron';
import { pool } from './db.js';
import { sendNotificationToUsers } from './fcm.js'; // Giả định bạn có một module fcm.js riêng

// Tác vụ này sẽ chạy mỗi phút để kiểm tra các thông báo cần gửi
const scheduledNotificationTask = cron.schedule('* * * * *', async () => {
    console.log(`[${new Date().toISOString()}] Scheduler running: Checking for pending notifications...`);

    const client = await pool.connect();
    try {
        // Tìm tất cả thông báo có trạng thái PENDING và thời gian gửi đã đến
        const pendingNotifications = await client.query(
            `SELECT * FROM notification WHERE status = 'PENDING' AND scheduled_at <= NOW()`
        );

        if (pendingNotifications.rows.length === 0) {
            return; // Không có gì để gửi
        }

        console.log(`Found ${pendingNotifications.rows.length} notification(s) to send.`);

        for (const notification of pendingNotifications.rows) {
            try {
                // Lấy danh sách ID người nhận của thông báo này
                const usersResult = await client.query(
                    `SELECT user_id FROM user_notifications WHERE notification_id = $1`,
                    [notification.notification_id]
                );
                const userIds = usersResult.rows.map(row => row.user_id);

                if (userIds.length > 0) {
                    // Gửi thông báo qua FCM
                    await sendNotificationToUsers(userIds, { 
                        title: notification.title, 
                        content: notification.content 
                    });

                    // Nếu gửi thành công, cập nhật trạng thái thành SENT
                    await client.query(
                        `UPDATE notification SET status = 'SENT' WHERE notification_id = $1`,
                        [notification.notification_id]
                    );
                } else {
                    // Nếu không có người nhận, cập nhật thành FAILED để không gửi lại
                    await client.query(
                        `UPDATE notification SET status = 'FAILED' WHERE notification_id = $1`,
                        [notification.notification_id]
                    );
                }

            } catch (sendError) {
                console.error(`Failed to send notification ID: ${notification.notification_id}`, sendError);
                // Nếu gửi lỗi, cập nhật thành FAILED
                await client.query(
                    `UPDATE notification SET status = 'FAILED' WHERE notification_id = $1`,
                    [notification.notification_id]
                );
            }
        }

    } catch (err) {
        console.error('Error in scheduler task:', err);
    } finally {
        client.release();
    }
});

// Hàm để khởi động scheduler
export const startScheduler = () => {
    console.log('✅ Notification scheduler has started. Will check every minute.');
    scheduledNotificationTask.start();
};
