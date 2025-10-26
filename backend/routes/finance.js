import express from 'express';
import { pool } from '../db.js';

const router = express.Router();

// A helper function for querying the database
const query = (text, params) => pool.query(text, params);

// This function will be exported and called from index.js on server startup
export const createFinanceTables = async () => {
    try {
        await query(`
            CREATE TABLE IF NOT EXISTS finances (
                id SERIAL PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                content TEXT,
                amount NUMERIC(12, 2) NOT NULL,
                type VARCHAR(50) NOT NULL,
                due_date DATE,
                created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
            );
        `);
        await query(`
            CREATE TABLE IF NOT EXISTS user_finances (
                id SERIAL PRIMARY KEY,
                user_id INTEGER NOT NULL,
                finance_id INTEGER NOT NULL REFERENCES finances(id) ON DELETE CASCADE,
                status VARCHAR(50) DEFAULT 'chua_thanh_toan',
                UNIQUE(user_id, finance_id)
            );
        `);
        console.log('✅ Finance tables structure checked/created.');
    } catch (err) {
        console.error('❌ Error creating finance tables:', err);
    }
};

// --- API Endpoints ---

// [FOR ADMIN] Get all finance items for management
router.get('/all', async (req, res) => {
    try {
        const result = await query(`SELECT id, title, content, amount, type, TO_CHAR(due_date, 'DD/MM/YYYY') as due_date FROM finances ORDER BY created_at DESC`);
        res.json(result.rows);
    } catch (err) {
        res.status(500).json({ error: 'Internal Server Error' });
    }
});

// [FOR USER] Get finance items for a specific user
router.get('/user/:userId', async (req, res) => {
    const { userId } = req.params;
    try {
        const result = await query(`
            SELECT f.id, f.title, f.content, f.amount, f.type, TO_CHAR(f.due_date, 'DD/MM/YYYY') as due_date, uf.status
            FROM finances f JOIN user_finances uf ON f.id = uf.finance_id
            WHERE uf.user_id = $1 ORDER BY f.created_at DESC;
        `, [userId]);
        res.json(result.rows);
    } catch (err) {
        res.status(500).json({ error: 'Internal Server Error' });
    }
});

// [FOR ADMIN] Create a new finance item
router.post('/create', async (req, res) => {
    const { title, content, amount, type, due_date, target_user_ids } = req.body;
    if (!title || !amount || !type || !target_user_ids || !Array.isArray(target_user_ids)) {
        return res.status(400).json({ error: 'Missing required fields.' });
    }
    const finalDate = (due_date && due_date.trim() !== '') ? due_date : null;
    const client = await pool.connect();
    try {
        await client.query('BEGIN');
        const financeQuery = `INSERT INTO finances (title, content, amount, type, due_date) VALUES ($1, $2, $3, $4, TO_DATE($5, 'DD/MM/YYYY')) RETURNING id`;
        const financeResult = await client.query(financeQuery, [title, content, amount, type, finalDate]);
        const newFinanceId = financeResult.rows[0].id;
        const userFinanceQuery = 'INSERT INTO user_finances (user_id, finance_id) VALUES ($1, $2)';
        for (const userId of target_user_ids) {
            await client.query(userFinanceQuery, [userId, newFinanceId]);
        }
        await client.query('COMMIT');
        res.status(201).json({ success: true, finance_id: newFinanceId });
    } catch (err) {
        await client.query('ROLLBACK');
        res.status(500).json({ error: 'Internal Server Error' });
    } finally {
        client.release();
    }
});

export default router;
