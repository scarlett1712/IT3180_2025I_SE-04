import express from "express";
import { pool } from "../db.js";
const router = express.Router();

// Lấy danh sách feedback
router.get("/", async (req, res) => {
  try {
    const result = await pool.query("SELECT * FROM feedback ORDER BY created_at DESC");
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Server error" });
  }
});

// Gửi feedback
router.post("/", async (req, res) => {
  const { user_id, title, content } = req.body;
  try {
    await pool.query(
      `INSERT INTO feedback (user_id, title, content, created_at)
       VALUES ($1, $2, $3, NOW())`,
      [user_id, title, content]
    );
    res.status(201).json({ message: "Feedback submitted" });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Insert failed" });
  }
});

export default router;
