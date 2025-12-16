import express from 'express';
import { pool } from '../db.js';
// 🔥 1. Import hàm gửi thông báo Firebase
import { sendNotification } from '../utils/firebaseHelper.js';

// START: Add function to auto-create table
export const createAuthorityMessagesTable = async () => {
  const query = `
    CREATE TABLE IF NOT EXISTS authority_messages (
      id SERIAL PRIMARY KEY,
      title VARCHAR(255) NOT NULL,
      content TEXT NOT NULL,
      category VARCHAR(100) NOT NULL,
      priority VARCHAR(20) DEFAULT 'Normal',
      sender_name VARCHAR(100),
      attachment_url TEXT,
      receiver_admin_id INT DEFAULT 0,
      created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
      is_read SMALLINT DEFAULT 0
    );
  `;
  try {
    await pool.query(query);
    console.log('✅ "authority_messages" table checked/created successfully.');
  } catch (error) {
    console.error('❌ Error creating "authority_messages" table:', error);
    throw error;
  }
};
// END: Add function

const router = express.Router();

// Endpoint to create a new authority message
router.post('/messages', async (req, res) => {
    const {
        title,
        content,
        category,
        sender_name,
        priority = 'Normal',
        attachment_url = null,
        receiver_admin_id = 0
    } = req.body;

    if (!title || !content || !category || !sender_name) {
        return res.status(400).json({ message: 'Required fields are missing' });
    }

    const client = await pool.connect(); // Dùng client để có thể transaction nếu cần

    try {
        // 1. Lưu tin nhắn vào Database
        const query = `
            INSERT INTO authority_messages
            (title, content, category, priority, sender_name, attachment_url, receiver_admin_id)
            VALUES ($1, $2, $3, $4, $5, $6, $7)
            RETURNING *;
        `;
        const result = await client.query(query, [title, content, category, priority, sender_name, attachment_url, receiver_admin_id]);
        const savedMessage = result.rows[0];

        // ------------------------------------------------------------------
        // 🔥 2. GỬI PUSH NOTIFICATION CHO ADMIN
        // ------------------------------------------------------------------

        // Tìm Token của Admin để gửi thông báo
        // Nếu receiver_admin_id = 0, gửi cho TẤT CẢ Admin (Role ID = 2)
        // Nếu receiver_admin_id > 0, gửi cho Admin cụ thể đó

        let tokenQuery = "";
        let tokenParams = [];

        if (receiver_admin_id > 0) {
            // Gửi cho 1 người cụ thể
            tokenQuery = `SELECT fcm_token FROM users WHERE user_id = $1 AND fcm_token IS NOT NULL AND fcm_token != ''`;
            tokenParams = [receiver_admin_id];
        } else {
            // Gửi cho tất cả Admin (Giả sử Admin có role_id = 2 trong bảng userrole)
            tokenQuery = `
                SELECT u.fcm_token
                FROM users u
                JOIN userrole ur ON u.user_id = ur.user_id
                WHERE ur.role_id = 2 -- 2 là Role Admin
                AND u.fcm_token IS NOT NULL
                AND u.fcm_token != ''
            `;
        }

        const tokenRes = await client.query(tokenQuery, tokenParams);
        const tokens = tokenRes.rows.map(r => r.fcm_token);

        if (tokens.length > 0) {
            console.log(`📢 Đang gửi thông báo CQCN đến ${tokens.length} thiết bị Admin...`);

            // Nội dung hiển thị trên thanh thông báo
            const notifTitle = `🔔 Tin mới từ CQCN: ${category}`;
            const notifBody = `${sender_name}: ${title}`; // Ví dụ: "Công an Phường: Kiểm tra PCCC"

            // Data payload để khi bấm vào mở đúng màn hình
            const dataPayload = {
                type: "authority_message",
                id: savedMessage.id.toString()
            };

            // Gửi lần lượt cho từng token
            for (const token of tokens) {
                await sendNotification(token, notifTitle, notifBody, dataPayload)
                    .catch(err => console.error("Lỗi gửi push lẻ:", err.message));
            }
        } else {
            console.log("⚠️ Không tìm thấy Token Admin nào để gửi thông báo.");
        }
        // ------------------------------------------------------------------

        res.status(201).json({
            status: 'success',
            message: 'Message created successfully',
            data: savedMessage
        });

    } catch (error) {
        console.error('Error creating authority message:', error);
        res.status(500).json({ message: 'Internal server error' });
    } finally {
        client.release();
    }
});

// Endpoint to get all messages for the admin
router.get('/messages', async (req, res) => {
    const query = `
        SELECT id, title, content, category, priority, sender_name, attachment_url, receiver_admin_id, created_at, is_read
        FROM authority_messages
        ORDER BY created_at DESC;
    `;

    try {
        const result = await pool.query(query);
        const messages = result.rows.map(row => ({
            ...row,
            id: parseInt(row.id, 10),
            receiver_admin_id: parseInt(row.receiver_admin_id, 10),
            is_read: parseInt(row.is_read, 10)
        }));
        res.status(200).json(messages);
    } catch (error) {
        console.error('Error fetching authority messages:', error);
        res.status(500).json({ message: 'Internal server error' });
    }
});

export default router;