import express from 'express';
import { pool } from '../db.js';

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

    const query = `
        INSERT INTO authority_messages 
        (title, content, category, priority, sender_name, attachment_url, receiver_admin_id)
        VALUES ($1, $2, $3, $4, $5, $6, $7) 
        RETURNING *;
    `;

    try {
        const result = await pool.query(query, [title, content, category, priority, sender_name, attachment_url, receiver_admin_id]);
        res.status(201).json({
            status: 'success',
            message: 'Message created successfully',
            data: result.rows[0]
        });
    } catch (error) {
        console.error('Error creating authority message:', error);
        res.status(500).json({ message: 'Internal server error' });
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
