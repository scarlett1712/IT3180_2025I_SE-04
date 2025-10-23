import express from "express";
import { pool } from "../db.js";

const router = express.Router();

router.get("/", async (req, res) => {
  try {
    const query = `
      SELECT
        ui.user_id AS user_id,
        ui.full_name,
        ui.email,
        u.phone,
        TO_CHAR(ui.dob, 'YYYY-MM-DD') AS dob,
        ui.gender,
        ur.role_id,
        r.relationship_id,
        r.apartment_id,
        a.apartment_number,
        a.floor,
        a.area,
        r.relationship_with_the_head_of_household
      FROM user_item ui
      LEFT JOIN users u ON ui.user_id = u.user_id
      LEFT JOIN userrole ur ON ui.user_id = ur.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE ur.role_id = 1
      ORDER BY ui.full_name;
    `;

    const result = await pool.query(query);
    res.json(result.rows);
  } catch (err) {
    console.error("💥 Error fetching residents:", err);
    res.status(500).json({ error: err.message });
  }
});

export default router;