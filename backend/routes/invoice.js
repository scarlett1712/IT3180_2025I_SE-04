import express from "express";
import { pool } from "../db.js";

const router = express.Router();

// Helper query
const query = (text, params) => pool.query(text, params);

// 🧾 Tạo invoice khi thanh toán thành công
router.post("/store", async (req, res) => {
  const { finance_id, amount, description, ordercode, currency } = req.body;

  if (!finance_id || !amount || !description || !ordercode) {
    return res.status(400).json({
      error: "Thiếu finance_id, amount, description hoặc ordercode.",
    });
  }

  try {
    const result = await query(
      `
      INSERT INTO invoice (finance_id, amount, description, ordercode, currency)
      VALUES ($1, $2, $3, $4, $5)
      RETURNING *;
      `,
      [finance_id, amount, description, ordercode, currency || "VND"]
    );

    res.json({
      success: true,
      invoice: result.rows[0],
    });
  } catch (err) {
    console.error("❌ Error creating invoice:", err);
    res.status(500).json({ error: "Server error creating invoice." });
  }
});

// 🧾 Lấy invoice theo ordercode
router.get("/:ordercode", async (req, res) => {
  const { ordercode } = req.params;

  try {
    const result = await query(
      `
      SELECT *
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

// 🔥 FIX: VIẾT LẠI ROUTE NÀY ĐỂ DÙNG `query` HELPER
router.get("/by-finance/:financeId", async (req, res) => {
  try {
    const { financeId } = req.params;

    // Sử dụng helper 'query' thay vì biến 'db' không tồn tại
    const result = await query(
      `
      SELECT * FROM invoice
      WHERE finance_id = $1
      LIMIT 1
      `,
      [financeId]
    );

    if (result.rowCount === 0) {
      return res.status(404).json({ message: "Invoice not found" });
    }

    // Trả về dòng đầu tiên tìm thấy
    return res.json(result.rows[0]);

  } catch (error) {
    console.error("❌ Error fetching invoice by financeId:", error);
    res.status(500).json({ message: "Server error" });
  }
});

export default router;