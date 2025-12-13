import express from "express";
import { pool } from "../db.js";

const router = express.Router();

// Helper query
const query = (text, params) => pool.query(text, params);

// 🔥 CREATE TABLE with user_id column
export const createInvoiceTable = async () => {
  try {
    await query(`
      CREATE TABLE IF NOT EXISTS invoice (
        id SERIAL PRIMARY KEY,
        finance_id INTEGER NOT NULL REFERENCES finances(id) ON DELETE CASCADE,
        user_id INTEGER NOT NULL,
        amount NUMERIC(12, 2) NOT NULL,
        description TEXT,
        ordercode VARCHAR(255) UNIQUE NOT NULL,
        currency VARCHAR(10) DEFAULT 'VND',
        paytime TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(finance_id, user_id)
      );
    `);
    console.log("✅ Invoice table verified.");
  } catch (err) {
    console.error("❌ Error creating invoice table:", err);
  }
};

// ==================================================================
// 🧾 TẠO INVOICE KHI THANH TOÁN THÀNH CÔNG
// ==================================================================
router.post("/store", async (req, res) => {
  const { finance_id, user_id, amount, description, ordercode, currency } = req.body;

  if (!finance_id || !user_id || !amount || !description || !ordercode) {
    return res.status(400).json({
      error: "Thiếu finance_id, user_id, amount, description hoặc ordercode.",
    });
  }

  try {
    // Check if invoice already exists
    const existing = await query(
      "SELECT id FROM invoice WHERE finance_id = $1 AND user_id = $2",
      [finance_id, user_id]
    );

    if (existing.rows.length > 0) {
      return res.status(409).json({
        error: "Invoice đã tồn tại cho khoản thu này",
        invoice: existing.rows[0]
      });
    }

    const result = await query(
      `
      INSERT INTO invoice (finance_id, user_id, amount, description, ordercode, currency, paytime)
      VALUES ($1, $2, $3, $4, $5, $6, NOW())
      RETURNING *;
      `,
      [finance_id, user_id, amount, description, ordercode, currency || "VND"]
    );

    console.log(`✅ Invoice created: ID ${result.rows[0].id}, User ${user_id}, Finance ${finance_id}`);

    res.json({
      success: true,
      invoice: result.rows[0],
    });
  } catch (err) {
    console.error("❌ Error creating invoice:", err);
    res.status(500).json({ error: "Server error creating invoice." });
  }
});

// ==================================================================
// 🧾 LẤY INVOICE THEO ORDERCODE
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
      return res.status(404).json({ error: "Không tìm thấy invoice." });
    }

    res.json(result.rows[0]);
  } catch (err) {
    console.error("❌ Error fetching invoice:", err);
    res.status(500).json({ error: "Server error fetching invoice." });
  }
});

// ==================================================================
// 🔥 LẤY INVOICE THEO FINANCE_ID VÀ USER_ID (UPDATED)
// ==================================================================
router.get("/by-finance/:financeId", async (req, res) => {
  try {
    const { financeId } = req.params;
    const { user_id } = req.query;

    console.log(`🔍 Fetching invoice for finance ${financeId}, user ${user_id}`);

    if (!user_id) {
      return res.status(400).json({ error: "Missing user_id parameter" });
    }

    // 🔥 Filter by both finance_id and user_id
    const result = await query(
      `
      SELECT *, TO_CHAR(paytime, 'DD/MM/YYYY HH24:MI') as pay_time_formatted
      FROM invoice
      WHERE finance_id = $1 AND user_id = $2
      LIMIT 1
      `,
      [financeId, user_id]
    );

    if (result.rowCount === 0) {
      console.log(`⚠️ No invoice found for finance ${financeId}, user ${user_id}`);
      return res.status(404).json({
        message: "Invoice not found",
        detail: "Chưa có hóa đơn thanh toán cho khoản thu này"
      });
    }

    console.log(`✅ Found invoice: ${result.rows[0].ordercode}`);
    return res.json(result.rows[0]);

  } catch (error) {
    console.error("❌ Error fetching invoice by financeId:", error);
    res.status(500).json({ message: "Server error" });
  }
});

export default router;