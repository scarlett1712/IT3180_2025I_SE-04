import pg from 'pg';
import dotenv from 'dotenv';

dotenv.config();

const { Pool } = pg;

// 🔥 Hỗ trợ cả DATABASE_URL (connection string) và các biến riêng lẻ
let poolConfig;

if (process.env.DATABASE_URL) {
  // Sử dụng connection string nếu có (ưu tiên)
  poolConfig = {
    connectionString: process.env.DATABASE_URL,
    ssl: { rejectUnauthorized: false },
  };
  console.log('✅ Using DATABASE_URL connection string');
} else {
  // Fallback: sử dụng các biến môi trường riêng lẻ
  poolConfig = {
    host: process.env.PGHOST,
    port: process.env.PGPORT ? parseInt(process.env.PGPORT) : 5432,
    database: process.env.PGDATABASE,
    user: process.env.PGUSER,
    password: process.env.PGPASSWORD,
    ssl: { rejectUnauthorized: false },
  };
  console.log('✅ Using individual environment variables');
}

// Create and export the database connection pool.
// This will be imported by other files (like routes/finance.js) to interact with the database.
export const pool = new Pool(poolConfig);

// Test connection on startup
pool.on('connect', () => {
  console.log('✅ Database connected successfully');
});

pool.on('error', (err) => {
  console.error('❌ Unexpected database error:', err);
});

console.log('✅ Database pool configured and ready.');

export default pool;