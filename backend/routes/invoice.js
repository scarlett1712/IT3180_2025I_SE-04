import express from "express";
import { pool } from "../db.js";

const router = express.Router();

// Helper query
const query = (text, params) => pool.query(text, params);

// ==================================================================
// 🔥 0. KHỞI TẠO TABLE
// ==================================================================
export const createInvoiceTable = async () => {
  try {
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
    // Bỏ qua các lệnh check constraint phức tạp để tránh lỗi khi deploy lại
    console.log("✅ Invoice table verified.");
  } catch (err) {
    console.error("❌ Error checking invoice table:", err);
  }
};

// ==================================================================
// 🧾 1. TẠO INVOICE
// ==================================================================
router.post("/store", async (req, res) => {
  const { finance_id, user_id, amount, description, ordercode, currency } = req.body;

  if (!finance_id || !user_id || !amount || !description || !ordercode) {
    return res.status(400).json({ error: "Thiếu thông tin đầu vào" });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // Tìm ID của user_finances
    let ufResult = await client.query(
      "SELECT id FROM user_finances WHERE finance_id = $1 AND user_id = $2",
      [finance_id, user_id]
    );

    let finalUserFinanceId;

    if (ufResult.rows.length > 0) {
      finalUserFinanceId = ufResult.rows[0].id;
    } else {
      const newUf = await client.query(
        "INSERT INTO user_finances (user_id, finance_id, status) VALUES ($1, $2, 'chua_thanh_toan') RETURNING id",
        [user_id, finance_id]
      );
      finalUserFinanceId = newUf.rows[0].id;
    }

    const existing = await client.query("SELECT invoice_id FROM invoice WHERE finance_id = $1", [finalUserFinanceId]);

    if (existing.rows.length > 0) {
      await client.query("ROLLBACK");
      return res.status(200).json({ success: true, message: "Hóa đơn đã tồn tại", invoice: existing.rows[0] });
    }

    const invResult = await client.query(
      `INSERT INTO invoice (finance_id, amount, description, ordercode, currency, paytime)
       VALUES ($1, $2, $3, $4, $5, NOW() + INTERVAL '7 hours')
       RETURNING *`,
      [finalUserFinanceId, amount, description, ordercode, currency || "VND"]
    );

    await client.query("UPDATE user_finances SET status = 'da_thanh_toan' WHERE id = $1", [finalUserFinanceId]);

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
  } catch (err) { res.status(500).json({ error: "Lỗi Server" }); }
});

// ==================================================================
// 🔥 3. LẤY INVOICE THEO FINANCE_ID (Fix Lỗi 404 cho Admin)
// ==================================================================
router.get("/by-finance/:financeId", async (req, res) => {
  try {
    const { financeId } = req.params; // ID khoản thu chung
    const { user_id } = req.query;    // ID người dùng

    if (!user_id) return res.status(400).json({ error: "Thiếu user_id" });

    // 🔥 FIX: Logic tìm kiếm thông minh hơn
    // 1. Thử tìm chính xác theo User ID trước
    let queryStr = `
       SELECT i.*, TO_CHAR(i.paytime, 'DD/MM/YYYY HH24:MI') as pay_time_formatted
       FROM invoice i
       JOIN user_finances uf ON i.finance_id = uf.id
       WHERE uf.finance_id = $1 AND uf.user_id = $2
       LIMIT 1
    `;
    let result = await query(queryStr, [financeId, user_id]);

    // 2. Nếu không tìm thấy (do Admin tick chọn nhưng hóa đơn lại gắn vào User ID khác trong cùng phòng)
    // -> Tìm hóa đơn của BẤT KỲ ai trong cùng phòng (dựa vào phòng của user_id hiện tại)
    if (result.rowCount === 0) {
        console.log(`⚠️ Invoice not found for User ${user_id}. Searching room-mate...`);

        // Tìm phòng của user này
        const roomRes = await query(`
            SELECT a.apartment_id
            FROM user_item ui
            JOIN relationship r ON ui.relationship = r.relationship_id
            JOIN apartment a ON r.apartment_id = a.apartment_id
            WHERE ui.user_id = $1
        `, [user_id]);

        if (roomRes.rows.length > 0) {
            const apartmentId = roomRes.rows[0].apartment_id;

            // Tìm hóa đơn của bất kỳ user nào thuộc phòng này và finance này
            result = await query(`
               SELECT i.*, TO_CHAR(i.paytime, 'DD/MM/YYYY HH24:MI') as pay_time_formatted
               FROM invoice i
               JOIN user_finances uf ON i.finance_id = uf.id
               JOIN user_item ui ON uf.user_id = ui.user_id
               JOIN relationship r ON ui.relationship = r.relationship_id
               WHERE uf.finance_id = $1 AND r.apartment_id = $2
               LIMIT 1
            `, [financeId, apartmentId]);
        }
    }

    if (result.rowCount === 0) {
        return res.status(404).json({ message: "Invoice not found" });
    }

    res.json(result.rows[0]);

  } catch (error) {
    console.error("Error fetching invoice:", error);
    res.status(500).json({ message: "Lỗi Server" });
  }
});

export default router;