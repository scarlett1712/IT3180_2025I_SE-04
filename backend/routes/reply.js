import express from "express";
import { pool } from "../db.js";
const router = express.Router();

// Lấy danh sách phản hồi theo feedback_id
router.get("/:feedback_id", async (req, res) => {
  const { feedback_id } = req.params;
  try {
    const result = await pool.query(
      "SELECT * FROM replies WHERE feedback_id = $1 ORDER BY created_at",
      [feedback_id]
    );
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Server error" });
  }
});

// Admin gửi phản hồi
router.post("/", async (req, res) => {
  const { feedback_id, admin_id, content } = req.body;
  try {
    await pool.query(
      `INSERT INTO replies (feedback_id, admin_id, content, created_at)
       VALUES ($1, $2, $3, NOW())`,
      [feedback_id, admin_id, content]
    );
    res.status(201).json({ message: "Reply sent" });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Insert failed" });
  }
});

export default router;
