//user_item.js
import express from "express";
import { pool } from "../db.js";

const router = express.Router();

/* 🧠 Lấy toàn bộ cư dân */
router.get("/", async (req, res) => {
  try {
    const result = await pool.query(
      `SELECT ui.*, u.phone
       FROM user_item ui
       JOIN users u ON ui.user_id = u.user_id
       ORDER BY ui.user_item_id`
    );
    res.json(result.rows);
  } catch (err) {
    console.error("Error fetching residents:", err);
    res.status(500).json({ error: "Server error" });
  }
});

/* 🧠 Thêm cư dân */
router.post("/", async (req, res) => {
  const { user_id, full_name, gender, dob, family_id, relationship, email, is_living } = req.body;

  if (!user_id)
    return res.status(400).json({ error: "Thiếu user_id" });

  try {
    await pool.query(
      `INSERT INTO user_item (user_id, full_name, gender, dob, family_id, relationship, email, is_living)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [user_id, full_name, gender, dob, family_id, relationship, email, is_living]
    );
    res.status(201).json({ message: "Thêm cư dân thành công" });
  } catch (err) {
    console.error("Insert failed:", err);
    res.status(500).json({ error: "Insert failed" });
  }
});

export default router;
