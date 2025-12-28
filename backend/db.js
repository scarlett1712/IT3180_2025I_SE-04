import pg from 'pg';
import dotenv from 'dotenv';
import { URL } from 'url';

dotenv.config();

const { Pool } = pg;

// 🔥 Hỗ trợ cả DATABASE_URL (connection string) và các biến riêng lẻ
let poolConfig;

if (process.env.DATABASE_URL) {
  // Parse connection string và build config object riêng để control SSL tốt hơn
  try {
    const dbUrl = new URL(process.env.DATABASE_URL);
    
    // Parse các thành phần từ URL
    const database = dbUrl.pathname.slice(1); // Bỏ dấu / đầu tiên
    const host = dbUrl.hostname;
    const port = dbUrl.port || 5432;
    const user = decodeURIComponent(dbUrl.username);
    const password = decodeURIComponent(dbUrl.password);
    
    // Build config object không dùng connectionString để đảm bảo SSL config được áp dụng
    poolConfig = {
      host: host,
      port: parseInt(port),
      database: database,
      user: user,
      password: password,
      ssl: {
        rejectUnauthorized: false // Bỏ qua verification cho self-signed certificates (Aiven)
      }
    };
    console.log('✅ Using DATABASE_URL (parsed into config with SSL)');
  } catch (err) {
    console.error('❌ Error parsing DATABASE_URL, trying regex fallback:', err);
    // Fallback: parse thủ công bằng regex
    const urlPattern = /postgres:\/\/([^:]+):([^@]+)@([^:]+):(\d+)\/(.+?)(?:\?|$)/;
    const match = process.env.DATABASE_URL.match(urlPattern);
    if (match) {
      poolConfig = {
        host: match[3],
        port: parseInt(match[4]),
        database: match[5],
        user: decodeURIComponent(match[1]),
        password: decodeURIComponent(match[2]),
        ssl: { rejectUnauthorized: false }
      };
      console.log('✅ Using regex-parsed DATABASE_URL config');
    } else {
      console.error('❌ Failed to parse DATABASE_URL, using connectionString with SSL');
      // Last resort: dùng connectionString nhưng vẫn set SSL
      poolConfig = {
        connectionString: process.env.DATABASE_URL,
        ssl: { rejectUnauthorized: false }
      };
    }
  }
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