import express from "express";
import { pool } from "../db.js";
import bcrypt from "bcryptjs";

const router = express.Router();

/**
 * POST /api/create_user/create
 * - Chỉ cho phép thêm người vào các phòng ĐÃ CÓ SẴN trong Database.
 * - Tuyệt đối KHÔNG tạo phòng mới.
 */
router.post("/create", async (req, res) => {
  const {
    phone,
    full_name,
    gender,
    dob,
    job,
    email,
    room,           // Số phòng (VD: "101")
    is_head,        // true/false
    relationship_name, // Quan hệ với chủ hộ (nếu is_head = false)
    identity_card,
    home_town
    // Lưu ý: Biến 'floor' gửi lên sẽ bị bỏ qua, vì ta lấy tầng từ DB có sẵn
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
    // --- ✅ 2. Check SĐT trùng ---
    const existingUser = await client.query("SELECT user_id FROM users WHERE phone = $1", [phone]);
    if (existingUser.rows.length > 0) {
      return res.status(409).json({ error: "Số điện thoại này đã được đăng ký." });
    }

    // 🔥 BẮT ĐẦU TRANSACTION 🔥
    await client.query("BEGIN");

    // --- ✅ 3. KIỂM TRA PHÒNG CÓ TỒN TẠI KHÔNG? (QUAN TRỌNG NHẤT) ---
    // Chúng ta tìm phòng dựa trên số phòng (room)
    const existingApt = await client.query(
        "SELECT apartment_id, status FROM apartment WHERE apartment_number = $1",
        [room]
    );

    // ❌ NẾU PHÒNG KHÔNG TỒN TẠI -> BÁO LỖI NGAY
    if (existingApt.rows.length === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({
            error: `Phòng ${room} không có trong hệ thống. Vui lòng liên hệ Admin để tạo phòng trước.`
        });
    }

    const apartment_id = existingApt.rows[0].apartment_id;

    // --- ✅ 4. Kiểm tra Logic Chủ hộ / Thành viên ---
    if (is_head) {
        // Nếu muốn làm Chủ hộ -> Phải chắc chắn phòng chưa có ai cầm cờ chủ hộ
        const checkHead = await client.query(
            `SELECT relationship_id FROM relationship
             WHERE apartment_id = $1 AND is_head_of_household = TRUE`,
            [apartment_id]
        );

        if (checkHead.rows.length > 0) {
            await client.query("ROLLBACK");
            return res.status(409).json({
                error: `Phòng ${room} đã có Chủ hộ rồi! Không thể thêm chủ hộ mới.`
            });
        }
    }

    // --- ✅ 5. Tạo User (Bảng users) ---
    const defaultPassword = "123456";
    const password_hash = await bcrypt.hash(defaultPassword, 10);

    const userRes = await client.query(
      `INSERT INTO users (password_hash, phone, created_at)
       VALUES ($1, $2, NOW()) RETURNING user_id`,
      [password_hash, phone]
    );
    const user_id = userRes.rows[0].user_id;

    // Gán quyền Resident (role_id = 1)
    await client.query(
      `INSERT INTO userrole (user_id, role_id) VALUES ($1, 1)`,
      [user_id]
    );

    // --- ✅ 6. Tạo Relationship (Gắn vào phòng đã tìm thấy ở bước 3) ---
    const relRes = await client.query(
      `INSERT INTO relationship (apartment_id, is_head_of_household, relationship_with_the_head_of_household)
       VALUES ($1, $2, $3) RETURNING relationship_id`,
      [
          apartment_id,
          is_head, // true hoặc false
          is_head ? 'Bản thân' : relationship_name // Nếu là chủ hộ thì là 'Bản thân', không thì lấy tên quan hệ
      ]
    );
    const relationship_id = relRes.rows[0].relationship_id;

    // --- ✅ 7. Lưu thông tin chi tiết (Bảng user_item) ---
    await client.query(
      `INSERT INTO user_item
       (user_id, full_name, gender, dob, job, relationship, is_living, email, identity_card, home_town)
       VALUES ($1, $2, $3, $4, $5, $6, TRUE, $7, $8, $9)`,
      [
          user_id,
          full_name,
          gender,
          dob,
          job || null,
          relationship_id,
          email,
          identity_card || null,
          home_town || null
      ]
    );

    // Cập nhật trạng thái phòng thành "Occupied" nếu chưa (cho chắc chắn)
    await client.query("UPDATE apartment SET status = 'Occupied' WHERE apartment_id = $1", [apartment_id]);

    // 🔥 KẾT THÚC TRANSACTION 🔥
    await client.query("COMMIT");

    res.status(201).json({
      success: true,
      message: `✅ Thêm cư dân vào phòng ${room} thành công!`,
      user_id,
      apartment_id
    });

  } catch (error) {
    await client.query("ROLLBACK");
    console.error("❌ Lỗi khi tạo cư dân:", error);

    if (error.code === '23505') {
        return res.status(409).json({ error: 'Dữ liệu bị trùng lặp (SĐT hoặc CCCD).' });
    }

    res.status(500).json({ error: "Lỗi Server: " + error.message });
  } finally {
    client.release();
  }
});

export default router;