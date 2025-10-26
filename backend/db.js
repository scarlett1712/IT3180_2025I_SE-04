import express from 'express';
import pg from 'pg';
import dotenv from 'dotenv';

const { Pool } = pg;
dotenv.config();

// Create an Express router instead of a full app
const router = express.Router();

// Database connection - Export the pool for other modules
export const pool = new Pool({
  host: process.env.PGHOST,
  port: process.env.PGPORT,
  database: process.env.PGDATABASE,
  user: process.env.PGUSER,
  password: process.env.PGPASSWORD,
  ssl: { rejectUnauthorized: false },
});

// Function to execute queries safely
const query = async (text, params) => {
  const start = Date.now();
  const res = await pool.query(text, params);
  const duration = Date.now() - start;
  console.log('executed query', { text, duration, rows: res.rowCount });
  return res;
};

// --- TABLE CREATION (This should run on main server start) ---
export const createFinanceTables = async () => {
  try {
    await query(`
      CREATE TABLE IF NOT EXISTS finances (
        id SERIAL PRIMARY KEY,
        title VARCHAR(255) NOT NULL,
        content TEXT,
        amount NUMERIC(10, 2) NOT NULL,
        type VARCHAR(50) NOT NULL, -- 'Bắt buộc' or 'Tự nguyện'
        due_date DATE,
        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
      );
    `);

    await query(`
      CREATE TABLE IF NOT EXISTS user_finances (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL,
        finance_id INTEGER NOT NULL REFERENCES finances(id) ON DELETE CASCADE,
        status VARCHAR(50) DEFAULT 'chua_thanh_toan', -- 'chua_thanh_toan', 'da_thanh_toan'
        UNIQUE(user_id, finance_id)
      );
    `);
    console.log('✅ Finance tables created or already exist.');
  } catch (err) {
    console.error('❌ Error creating finance tables:', err);
  }
};

// --- API ENDPOINTS ---

router.post('/api/finance/create', async (req, res) => {
  const { title, content, amount, type, due_date, target_user_ids } = req.body;

  if (!title || !amount || !type || !target_user_ids || !Array.isArray(target_user_ids)) {
    return res.status(400).json({ error: 'Missing required fields or invalid data.' });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const financeQuery = 'INSERT INTO finances (title, content, amount, type, due_date) VALUES ($1, $2, $3, $4, $5) RETURNING id';
    const financeResult = await client.query(financeQuery, [title, content, amount, type, due_date]);
    const newFinanceId = financeResult.rows[0].id;

    const userFinanceQuery = 'INSERT INTO user_finances (user_id, finance_id) VALUES ($1, $2)';
    for (const userId of target_user_ids) {
      await client.query(userFinanceQuery, [userId, newFinanceId]);
    }

    await client.query('COMMIT');
    res.status(201).json({ success: true, message: 'Finance record created successfully.', finance_id: newFinanceId });

  } catch (err) {
    await client.query('ROLLBACK');
    console.error('Error in /api/finance/create:', err);
    res.status(500).json({ error: 'Internal Server Error' });
  } finally {
    client.release();
  }
});

router.get('/api/finance/user/:userId', async (req, res) => {
  const { userId } = req.params;
  try {
    const result = await query(`
      SELECT 
        f.id, f.title, f.content, f.amount, f.type, TO_CHAR(f.due_date, 'DD/MM/YYYY') as due_date, uf.status
      FROM finances f
      JOIN user_finances uf ON f.id = uf.finance_id
      WHERE uf.user_id = $1
      ORDER BY f.due_date DESC;
    `, [userId]);
    res.json(result.rows);
  } catch (err) {
    console.error(`Error in /api/finance/user/${userId}:`, err);
    res.status(500).json({ error: 'Internal Server Error' });
  }
});

router.get('/api/residents', async (req, res) => {
    try {
        const result = await query(`
            SELECT 
                u.id as user_id,
                ui.id as user_item_id,
                ui.full_name,
                ui.gender,
                TO_CHAR(ui.dob, 'DD/MM/YYYY') as dob,
                u.email,
                u.phone,
                f.relationship_with_the_head_of_household,
                f.family_id,
                f.is_living,
                a.apartment_number,
                u.role_id
            FROM users u
            JOIN user_items ui ON u.id = ui.user_id
            JOIN families f ON ui.family_id = f.id
            JOIN apartments a ON f.apartment_id = a.id
            ORDER BY ui.full_name;
        `);
        res.json(result.rows);
    } catch (err) {
        console.error('Error in /api/residents:', err);
        res.status(500).json({ error: 'Internal Server Error' });
    }
});

// Export the router
export default router;
