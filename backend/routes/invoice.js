import express from "express";
import { pool } from "../db.js";

const router = express.Router();

// Helper query
const query = (text, params) => pool.query(text, params);

// 🔥 CREATE TABLE - No changes to DB
export const createInvoiceTable = async () => {
  console.log("✅ Invoice table already exists (no changes).");
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
    // Get user_finances.id (finance_id in request is actually finances.id)
    const ufResult = await query(
      "SELECT id FROM user_finances WHERE finance_id = $1 AND user_id = $2",
      [finance_id, user_id]
    );

    if (ufResult.rows.length === 0) {
      return res.status(404).json({
        error: "Không tìm thấy khoản thu cho người dùng này"
      });
    }

    const userFinanceId = ufResult.rows[0].id;

    // Check if invoice already exists (using user_finances.id)
    const existing = await query(
      "SELECT invoice_id FROM invoice WHERE finance_id = $1",
      [userFinanceId]
    );

    if (existing.rows.length > 0) {
      return res.status(409).json({
        error: "Invoice đã tồn tại cho khoản thu này",
        invoice: existing.rows[0]
      });
    }

    const result = await query(
      `
      INSERT INTO invoice (finance_id, amount, description, ordercode, currency, paytime)
      VALUES ($1, $2, $3, $4, $5, NOW())
      RETURNING *;
      `,
      [userFinanceId, amount, description, ordercode, currency || "VND"]
    );

    console.log(`✅ Invoice created: ID ${result.rows[0].invoice_id}, UserFinance ${userFinanceId}`);

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
      return res.status(