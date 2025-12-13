import express from "express";
import { pool } from "../db.js";

const router = express.Router();

// Helper query
const query = (text, params) => pool.query(text, params);

// ==================================================================
// 🔥 0. KHỞI TẠO TABLE (Chỉ chạy kiểm tra, không drop)
// ==================================================================
export const createInvoiceTable = async () => {
  try {
    // 1. Tạo bảng cơ bản nếu chưa có
    await query(`
      CREATE TABLE IF NOT EXISTS invoice (
        invoice_id SERIAL PRIMARY KEY,
        finance_id INTEGER NOT NULL,
        amount NUMERIC(12, 2) NOT NULL,
        description TEXT,
        ordercode VARCHAR(255) UNIQUE NOT NULL,
        currency VARCHAR(10) DEFAULT 'VND',
        paytime TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );
    `);

    // 2. Kiểm tra và thêm ràng buộc nếu thiếu (An toàn)
    await query(`
      DO $$
      BEGIN
        -- Thêm FK tới user_finances nếu chưa có
        IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name='invoice_finance_id_fkey') THEN
            ALTER TABLE invoice ADD CONSTRAINT invoice_finance_id_fkey FOREIGN KEY (finance_id) REFERENCES user_finances(id) ON DELETE CASCADE;
        END IF;

        -- Thêm Unique nếu chưa có
        IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name='invoice_finance_id_key') THEN
            ALTER TABLE invoice ADD CONSTRAINT invoice_finance_id_key UNIQUE (finance_id);
        END IF;
      END
      $$;
    `);

    console.log("✅ Invoice table verified.");
  } catch (err) {
    console.error("❌ Error checking invoice table:", err);
  }
};

// ==================================================================
// 🧾 1. TẠO INVOICE (Tự động Map ID)
// ==================================================================
router.post("/store", async (req, res) => {
  const { finance_id, user_id, amount, description, ordercode, currency } = req.body;

  if (!finance_id || !user_id || !amount || !description || !ordercode) {
    return res.status(400).json({ error: "Thiếu thông tin đầu vào" });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Tìm ID của user_finances (Bảng trung gian)
    let ufResult = await client.query(
      "SELECT id FROM user_finances WHERE finance_id = $1 AND user_id = $2",
      [finance_id, user_id]
    );

    let finalUserFinanceId;

    if (ufResult.rows.length > 0) {
      finalUserFinanceId = ufResult.rows[0].id;
    } else {
      // Nếu chưa có (đóng tự nguyện), tự tạo mới
      console.log(`⚠️ Creating new user_finance for User ${user_id} - Finance ${finance_id}`);
      const newUf = await client.query(
        "INSERT INTO user_finances (user_id, finance_id, status) VALUES ($1, $2, 'chua_thanh_toan') RETURNING id",
        [user_id, finance_id]
      );
      finalUserFinanceId = newUf.rows[0].id;
    }

    // 2. Kiểm tra đã có hóa đơn chưa
    const existing = await client.query(
      "SELECT invoice_id FROM invoice WHERE finance_id = $1",
      [finalUserFinanceId]
    );

    if (existing.rows.length > 0) {
      await client.query("ROLLBACK");
      // Trả về 200 kèm invoice cũ để App hiển thị luôn
      return res.status(200).json({
        success: true,
        message: "Hóa đơn đã tồn tại",
        invoice: existing.rows[0]
      });
    }

    // 3. Tạo Invoice
    const invResult = await client.query(
      `INSERT INTO invoice (finance_id, amount, description, ordercode, currency, paytime)
       VALUES ($1, $2, $3, $4, $5, NOW())
       RETURNING *`,
      [finalUserFinanceId, amount, description, ordercode, currency || "VND"]
    );

    // 4. Update trạng thái thanh toán
    await client.query(
      "UPDATE user_finances SET status = 'da_thanh_toan' WHERE id = $1",
      [finalUserFinanceId]
    );

    await client.query("COMMIT");
    res.json({ success: true, invoice: invResult.rows[0] });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("❌ Error creating invoice:", err);
    res.status(500).json({ error: "Lỗi Server" });
  } finally {
    client.release();
  }
});

// ==================================================================
// 🧾 2. LẤY INVOICE THEO ORDERCODE
// ==================================================================
router.get("/:ordercode", async (req, res) => {
  try {
    const result = await query(
      "SELECT *, TO_CHAR(paytime, 'DD/MM/YYYY HH24:MI') as pay_time_formatted FROM invoice WHERE ordercode = $1",
      [req.params.ordercode]
    );
    if (result.rowCount === 0) return res.status(404).json({ error: "Không tìm thấy" });
    res.json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: "Lỗi Server" });
  }
});

// ==================================================================
// 🔥 3. LẤY INVOICE THEO FINANCE_ID + USER_ID (Cho App)
// ==================================================================
router.get("/by-finance/:financeId", async (req, res) => {
  try {
    const { financeId } = req.params;
    const { user_id } = req.query;

    if (!user_id) return res.status(400).json({ error: "Thiếu user_id" });

    // JOIN bảng user_finances để tìm invoice
    const result = await query(
      `SELECT i.*, TO_CHAR(i.paytime, 'DD/MM/YYYY HH24:MI') as pay_time_formatted
       FROM invoice i
       JOIN user_finances uf ON i.finance_id = uf.id
       WHERE uf.finance_id = $1 AND uf.user_id = $2
       LIMIT 1`,
      [financeId, user_id]
    );

    if (result.rowCount === 0) return res.status(404).json({ message: "Invoice not found" });

    res.json(result.rows[0]);

  } catch (error) {
    console.error("Error fetching invoice:", error);
    res.status(500).json({ message: "Lỗi Server" });
  }
});

export default router;