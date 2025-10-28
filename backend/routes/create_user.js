import express from "express";
import { pool } from "../db.js";
import bcrypt from "bcryptjs";

const router = express.Router();

/**
 * POST /api/create_user/create
 * Tạo một cư dân mới.
 * - Nếu is_head = true: Tạo một chủ hộ và một căn hộ mới.
 * - Nếu is_head = false: Thêm một thành viên vào một căn hộ đã tồn tại.
 */
router.post("/create", async (req, res) => {
  const {
    phone,
    full_name,
    gender,
    dob,
    email,
    room, // Tên căn hộ, ví dụ "A101"
    floor,
    is_head,
    relationship_name, // Bắt buộc nếu is_head = false
  } = req.body;

  // --- ✅ 1. Validate Input ---
  if (!phone || !full_name || !gender || !dob || !room || is_head === undefined) {
    return res.status(400).json({ error: "Thiếu thông tin bắt buộc." });
  }
  if (!is_head && !relationship_name) {
    return res.status(400).json({ error: "Phải cung cấp tên quan hệ cho thành viên." });
  }

  const client = await pool.connect();
  try {
    // --- ✅ 2. Kiểm tra dữ liệu đã tồn tại chưa ---
    const existingUser = await client.query("SELECT user_id FROM users WHERE phone = $1", [phone]);
    if (existingUser.rows.length > 0) {
      return res.status(409).json({ error: "Số điện thoại đã được đăng ký." });
    }

    // Bắt đầu transaction
    await client.query("BEGIN");

    // --- ✅ 3. Tạo tài khoản người dùng chung ---
    const defaultPassword = "123456";
    const password_hash = await bcrypt.hash(defaultPassword, 10);
    const userRes = await client.query(
      `INSERT INTO users (password_hash, phone, created_at)
       VALUES ($1, $2, NOW()) RETURNING user_id`,
      [password_hash, phone]
    );
    const user_id = userRes.rows[0].user_id;

    // Gán quyền mặc định (ví dụ role_id = 1 là 'USER')
    await client.query(
      `INSERT INTO userrole (user_id, role_id) VALUES ($1, 1)`,
      [user_id]
    );

    let apartment_id;
    let relationship_id;

    // --- ✅ 4. Phân nhánh logic dựa trên is_head ---
    if (is_head) {
      // --- LOGIC TẠO CHỦ HỘ VÀ CĂN HỘ MỚI ---

      // Kiểm tra xem căn hộ đã tồn tại và có chủ hộ chưa
      const existingApt = await client.query("SELECT apartment_id FROM apartment WHERE apartment_number = $1", [room]);
      if (existingApt.rows.length > 0) {
        await client.query("ROLLBACK"); // Hoàn tác việc tạo user
        return res.status(409).json({ error: `Căn hộ ${room} đã tồn tại.` });
      }

      // a. Tạo căn hộ mới
      const aptRes = await client.query(
        `INSERT INTO apartment (building_id, apartment_number, floor, status)
         VALUES (1, $1, $2, 'Occupied') RETURNING apartment_id`,
        [room, floor]
      );
      apartment_id = aptRes.rows[0].apartment_id;

      // b. Tạo relationship cho chủ hộ (quan hệ là NULL)
      const relRes = await client.query(
        `INSERT INTO relationship (apartment_id, is_head_of_household, relationship_with_the_head_of_household)
         VALUES ($1, TRUE, NULL) RETURNING relationship_id`,
        [apartment_id]
      );
      relationship_id = relRes.rows[0].relationship_id;

    } else {
      // --- LOGIC THÊM THÀNH VIÊN VÀO CĂN HỘ ĐÃ CÓ ---

      // a. Tìm căn hộ đã tồn tại
      const existingApt = await client.query("SELECT apartment_id FROM apartment WHERE apartment_number = $1", [room]);
      if (existingApt.rows.length === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: `Căn hộ ${room} không tồn tại.` });
      }
      apartment_id = existingApt.rows[0].apartment_id;

      // b. Tạo relationship cho thành viên mới
      const relRes = await client.query(
        `INSERT INTO relationship (apartment_id, is_head_of_household, relationship_with_the_head_of_household)
         VALUES ($1, FALSE, $2) RETURNING relationship_id`,
        [apartment_id, relationship_name]
      );
      relationship_id = relRes.rows[0].relationship_id;
    }

    // --- ✅ 5. Tạo user_item (thông tin chi tiết của người dùng) ---
    await client.query(
      `INSERT INTO user_item (user_id, full_name, gender, dob, relationship, is_living, email)
       VALUES ($1, $2, $3, $4, $5, TRUE, $6)`,
      [user_id, full_name, gender, dob, relationship_id, email]
    );

    // Kết thúc transaction
    await client.query("COMMIT");

    res.status(201).json({
      message: `✅ Tạo cư dân "${full_name}" thành công!`,
      user_id,
      apartment_id,
      default_password: defaultPassword,
    });

  } catch (error) {
    await client.query("ROLLBACK");
    console.error("❌ Lỗi khi tạo cư dân:", error);
    // Trả về lỗi cụ thể hơn nếu có thể
    if (error.code === '23505') { // Lỗi unique_violation
        return res.status(409).json({ error: 'Dữ liệu bị trùng lặp. Vui lòng kiểm tra lại.' });
    }
    res.status(500).json({ error: "Đã xảy ra lỗi phía server." });
  } finally {
    client.release();
  }
});

export default router;
