import express from "express";
import { pool } from "../db.js";

const router = express.Router();

// Helper query
const query = (text, params) => pool.query(text, params);

// ==================================================================
// 🔥 0. TẠO TABLE (Cập nhật logic theo finance.js)
// ==================================================================
export const createInvoiceTable = async () => {
  try {
    await query(`
      CREATE TABLE IF NOT EXISTS invoice (
        invoice_id SERIAL PRIMARY KEY, -- Đổi thành invoice_id để khớp với finance.js line 331
        finance_id INTEGER NOT NULL REFERENCES user_finances(id) ON DELETE CASCADE, -- 🔥 Trỏ tới user_finances
        amount NUMERIC(12, 2) NOT NULL,
        description TEXT,
        ordercode VARCHAR(255) UNIQUE NOT NULL,
        currency VARCHAR(10) DEFAULT 'VND',
        paytime TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(finance_id) -- Một user_finance chỉ có 1 invoice
      );
    `);
    console.log("✅ Invoice table verified (Linked to user_finances).");
  } catch (err) {
    console.error("❌ Error creating invoice table:", err);
  }
};

// ==================================================================
// 🧾 1. TẠO INVOICE KHI THANH TOÁN THÀNH CÔNG (Webhook/App Payment)
// ==================================================================
router.post("/store", async (req, res) => {
  // Client gửi lên finance_id (ID khoản thu chung) và user_id
  const { finance_id, user_id, amount, description, ordercode, currency } = req.body;

  if (!finance_id || !user_id || !amount || !description || !ordercode) {
    return res.status(400).json({
      error: "Thiếu thông tin (finance_id, user_id, amount...)",
    });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Tìm user_finances ID tương ứng
    // (Vì invoice phải link vào user_finances chứ không phải finances gốc)
    const ufResult = await client.query(
      "SELECT id, status FROM user_finances WHERE finance_id = $1 AND user_id = $2",
      [finance_id, user_id]
    );

    if (ufResult.rows.length === 0) {
      await client.query("ROLLBACK");
      return res.status(404).json({ error: "Không tìm thấy khoản thu cho người dùng này" });
    }

    const userFinanceId = ufResult.rows[0].id;

    // 2. Kiểm tra xem Invoice đã tồn tại chưa
    const existing = await client.query(
      "SELECT invoice_id FROM invoice WHERE finance_id = $1",
      [userFinanceId]
    );

    if (existing.rows.length > 0) {
      await client.query("ROLLBACK");
      return res.status(409).json({
        error: "Hóa đơn đã tồn tại",
        invoice: existing.rows[0]
      });
    }

    // 3. Tạo Invoice (finance_id ở đây lưu userFinanceId)
    const result = await client.query(
      `
      INSERT INTO invoice (finance_id, amount, description, ordercode, currency, paytime)
      VALUES ($1, $2, $3, $4, $5, NOW())
      RETURNING *;
      `,
      [userFinanceId, amount, description, ordercode, currency || "VND"]
    );

    // 4. 🔥 QUAN TRỌNG: Cập nhật trạng thái trong user_finances thành 'da_thanh_toan'
    // Để đồng bộ với logic bên finance.js
    await client.query(
      "UPDATE user_finances SET status = 'da_thanh_toan' WHERE id = $1",
      [userFinanceId]
    );

    await client.query("COMMIT");

    console.log(`✅ Invoice created & Status updated: Order ${ordercode}`);
    res.json({
      success: true,
      invoice: result.rows[0],
    });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("❌ Error creating invoice:", err);
    res.status(500).json({ error: "Lỗi Server khi tạo Invoice." });
  } finally {
    client.release();
  }
});

// ==================================================================
// 🧾 2. LẤY INVOICE THEO ORDERCODE
// ==================================================================
router.get("/:ordercode", async (req, res) => {
  const { ordercode } = req.params;

  try {
    const result = await query(
      `
      SELECT *, TO_CHAR(paytime, 'DD/MM/YYYY HH24:MI') as pay_time_formatted
      FROM invoice
      WHERE ordercode = $1
      LIMIT 1
      `,
      [ordercode]
    );

    if (result.rowCount === 0) {
      return res.status(404).json({ error: "Không tìm thấy hóa đơn." });
    }

    res.json(result.rows[0]);
  } catch (err) {
    console.error("❌ Error fetching invoice:", err);
    res.status(500).json({ error: "Lỗi Server." });
  }
});

// ==================================================================
// 🔥 3. LẤY INVOICE THEO KHOẢN THU VÀ USER (Cho App hiển thị)
// ==================================================================
router.get("/by-finance/:financeId", async (req, res) => {
  try {
    const { financeId } = req.params; // Đây là ID khoản thu chung (finances.id)
    const { user_id } = req.query;

    if (!user_id) {
      return res.status(400).json({ error: "Thiếu user_id" });
    }

    // Chúng ta phải JOIN để tìm từ finances.id -> user_finances.id -> invoice
    const result = await query(
      `
      SELECT
        i.*,
        TO_CHAR(i.paytime, 'DD/MM/YYYY HH24:MI') as pay_time_formatted
      FROM invoice i
      JOIN user_finances uf ON i.finance_id = uf.id
      WHERE uf.finance_id = $1 AND uf.user_id = $2
      LIMIT 1
      `,
      [financeId, user_id]
    );

    if (result.rowCount === 0) {
      return res.status(404).json({
        message: "Invoice not found",
        detail: "Chưa có hóa đơn thanh toán"
      });
    }

    res.json(result.rows[0]);

  } catch (error) {
    console.error("❌ Error fetching invoice by financeId:", error);
    res.status(500).json({ message: "Lỗi Server" });
  }
});

export default router;