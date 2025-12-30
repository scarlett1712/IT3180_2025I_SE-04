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
        finance_id INTEGER NOT NULL, -- ID của dòng user_finances người đại diện trả
        amount NUMERIC(12, 2) NOT NULL,
        description TEXT,
        ordercode VARCHAR(255) UNIQUE NOT NULL,
        currency VARCHAR(10) DEFAULT 'VND',
        paytime TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );
    `);
    console.log("✅ Invoice table verified.");
  } catch (err) {
    console.error("❌ Error checking invoice table:", err);
  }
};

// ==================================================================
// 🧾 1. TẠO INVOICE VÀ GẠCH NỢ CHO CẢ PHÒNG
// ==================================================================
router.post("/store", async (req, res) => {
  const { finance_id, user_id, amount, description, ordercode, currency } = req.body;

  if (!finance_id || !user_id || !amount || !description || !ordercode) {
    return res.status(400).json({ error: "Thiếu thông tin đầu vào" });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Tìm hoặc tạo dòng user_finances cho người trả tiền (Payer)
    let ufResult = await client.query(
      "SELECT id FROM user_finances WHERE finance_id = $1 AND user_id = $2",
      [finance_id, user_id]
    );

    let payerUserFinanceId;
    if (ufResult.rows.length > 0) {
      payerUserFinanceId = ufResult.rows[0].id;
    } else {
      const newUf = await client.query(
        "INSERT INTO user_finances (user_id, finance_id, status) VALUES ($1, $2, 'chua_thanh_toan') RETURNING id",
        [user_id, finance_id]
      );
      payerUserFinanceId = newUf.rows[0].id;
    }

    // 2. Kiểm tra xem đã có hóa đơn nào cho nhóm này chưa (tránh trùng lặp)
    const existing = await client.query(
      "SELECT i.invoice_id FROM invoice i JOIN user_finances uf ON i.finance_id = uf.id WHERE uf.finance_id = $1 AND uf.user_id = $2",
      [finance_id, user_id]
    );

    if (existing.rows.length > 0) {
      await client.query("ROLLBACK");
      return res.status(200).json({ success: true, message: "Hóa đơn đã tồn tại", invoice: existing.rows[0] });
    }

    // 3. Lưu hóa đơn
    const invResult = await client.query(
      `INSERT INTO invoice (finance_id, amount, description, ordercode, currency, paytime)
       VALUES ($1, $2, $3, $4, $5, NOW())
       RETURNING *`,
      [payerUserFinanceId, amount, description, ordercode, currency || "VND"]
    );

    // 4. CẬP NHẬT TRẠNG THÁI CHO CẢ PHÒNG (Gạch nợ đồng bộ)
    await client.query(`
      UPDATE user_finances
      SET status = 'da_thanh_toan'
      WHERE finance_id = $1
      AND user_id IN (
        SELECT ui_member.user_id
        FROM user_item ui_payer
        JOIN relationship r_payer ON ui_payer.relationship = r_payer.relationship_id
        JOIN relationship r_member ON r_payer.apartment_id = r_member.apartment_id
        JOIN user_item ui_member ON r_member.relationship_id = ui_member.relationship
        WHERE ui_payer.user_id = $2
      )
    `, [finance_id, user_id]);

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
      `SELECT i.*, TO_CHAR(i.paytime, 'DD/MM/YYYY HH24:MI') as pay_time_formatted, ui.full_name as paid_by_name
       FROM invoice i
       JOIN user_finances uf ON i.finance_id = uf.id
       JOIN user_item ui ON uf.user_id = ui.user_id
       WHERE i.ordercode = $1`,
      [req.params.ordercode]
    );
    if (result.rowCount === 0) return res.status(404).json({ error: "Không tìm thấy" });
    res.json(result.rows[0]);
  } catch (err) { res.status(500).json({ error: "Lỗi Server" }); }
});

// ==================================================================
// 🔥 3. LẤY INVOICE THEO FINANCE_ID (Tìm thông minh cho cả phòng)
// ==================================================================
router.get("/by-finance/:financeId", async (req, res) => {
  try {
    const { financeId } = req.params;
    const { user_id } = req.query;

    if (!user_id) return res.status(400).json({ error: "Thiếu user_id" });

    // Bước 1: Tìm hóa đơn chính chủ trước
    let queryStr = `
       SELECT i.*, TO_CHAR(i.paytime, 'DD/MM/YYYY HH24:MI') as pay_time_formatted, ui.full_name as paid_by_name
       FROM invoice i
       JOIN user_finances uf ON i.finance_id = uf.id
       JOIN user_item ui ON uf.user_id = ui.user_id
       WHERE uf.finance_id = $1 AND uf.user_id = $2
       LIMIT 1
    `;
    let result = await query(queryStr, [financeId, user_id]);

    // Bước 2: Nếu không thấy, tìm hóa đơn của bất kỳ ai trong phòng
    if (result.rowCount === 0) {
        const roomRes = await query(`
            SELECT r.apartment_id
            FROM user_item ui
            JOIN relationship r ON ui.relationship = r.relationship_id
            WHERE ui.user_id = $1
        `, [user_id]);

        if (roomRes.rows.length > 0) {
            const apartmentId = roomRes.rows[0].apartment_id;

            result = await query(`
               SELECT i.*, TO_CHAR(i.paytime, 'DD/MM/YYYY HH24:MI') as pay_time_formatted, ui.full_name as paid_by_name
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