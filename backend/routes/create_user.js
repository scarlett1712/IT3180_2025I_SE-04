import express from "express";
import { pool } from "../db.js";
import bcrypt from "bcryptjs";

const router = express.Router();

/**
 * POST /api/create_user/create
 * Body JSON:
 * {
 *   "phone": "0901234567",
 *   "full_name": "Nguyễn Văn A",
 *   "gender": "Nam",
 *   "dob": "1990-05-01",
 *   "email": "abc@gmail.com",
 *   "room": "A101",
 *   "is_head": true,
 *   "relationship_name": "Con"   // chỉ cần nếu is_head = false
 * }
 */
router.post("/create", async (req, res) => {
  const client = await pool.connect();

  try {
    const {
      phone,
      full_name,
      gender,
      dob,
      email,
      room,
      floor,
      is_head,
      relationship_name,
    } = req.body;

    if (!phone || !full_name || !gender || !dob || !room) {
      return res.status(400).json({ error: "Thiếu thông tin bắt buộc." });
    }

    // ✅ Mật khẩu mặc định
    const defaultPassword = "123456";
    const password_hash = await bcrypt.hash(defaultPassword, 10);

    await client.query("BEGIN");

    // 1️⃣ Thêm vào bảng users
    const insertUser = `
      INSERT INTO users (password_hash, phone, created_at, updated_at)
      VALUES ($1, $2, NOW(), NULL)
      RETURNING user_id
    `;
    const { rows: userRows } = await client.query(insertUser, [
      password_hash,
      phone,
    ]);
    const user_id = userRows[0].user_id;

    // 2️⃣ Thêm vào bảng apartment
    const insertApartment = `
      INSERT INTO apartment (building_id, apartment_number, area, floor, status, start_date, end_date)
      VALUES (1, $1, NULL, $2, 'Occupied', NULL, NULL)
      RETURNING apartment_id
    `;
    const { rows: aptRows } = await client.query(insertApartment, [room, floor]);
    const apartment_id = aptRows[0].apartment_id;

    // 3️⃣ Thêm vào bảng relationship
    const insertRelationship = `
      INSERT INTO relationship (
        apartment_id, is_head_of_household, relationship_with_the_head_of_household,
        start_date, end_date, note
      )
      VALUES ($1, $2, $3, NULL, NULL, NULL)
      RETURNING relationship_id
    `;
    const { rows: relRows } = await client.query(insertRelationship, [
      apartment_id,
      is_head,
      is_head ? null : relationship_name,
    ]);
    const relationship_id = relRows[0].relationship_id;

    // 4️⃣ Thêm vào bảng user_item
    const insertUserItem = `
      INSERT INTO user_item (
        user_id, full_name, gender, dob, family_id, relationship,
        is_living, email, avatar_path
      )
      VALUES ($1, $2, $3, $4, NULL, $5, TRUE, $6, NULL)
    `;
    await client.query(insertUserItem, [
      user_id,
      full_name,
      gender,
      dob,
      relationship_id,
      email,
    ]);

    // 5️⃣ Gán quyền mặc định role_id = 1
    await client.query(
      `INSERT INTO userrole (user_id, role_id) VALUES ($1, 1)`,
      [user_id]
    );

    await client.query("COMMIT");

    res.status(201).json({
      message: "✅ Tạo cư dân thành công!",
      user_id,
      apartment_id,
      relationship_id,
      default_password: defaultPassword, // trả về để admin biết
    });
  } catch (error) {
    await client.query("ROLLBACK");
    console.error("❌ Lỗi khi tạo cư dân:", error);
    res.status(500).json({ error: "Đã xảy ra lỗi khi tạo cư dân." });
  } finally {
    client.release();
  }
});

export default router;