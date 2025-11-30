import express from "express";
import { pool } from "../db.js";

const router = express.Router();
const query = (text, params) => pool.query(text, params);

// 🛠️ 1. Tạo bảng profile_requests (Giữ nguyên cấu trúc để tương thích dữ liệu cũ)
const createTableQuery = `
  CREATE TABLE IF NOT EXISTS profile_requests (
    request_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    new_full_name VARCHAR(255),
    new_phone VARCHAR(20),
    new_email VARCHAR(255),
    new_gender VARCHAR(50),
    new_dob DATE,
    new_room VARCHAR(50),
    new_floor VARCHAR(50),
    new_relationship VARCHAR(100),
    new_is_head BOOLEAN,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user_item(user_id) ON DELETE CASCADE
  );
`;

(async () => {
  try {
    await query(createTableQuery);
    console.log("✅ Table 'profile_requests' ready.");
  } catch (err) {
    console.error("Error creating profile_requests table", err);
  }
})();

// 📤 2. [USER] Gửi yêu cầu (🔥 FIX: Bỏ room và floor khỏi insert)
router.post("/create", async (req, res) => {
  const {
    user_id, full_name, phone, email, gender, dob,
    relationship, is_head
    // room, floor bị loại bỏ vì không cho phép cập nhật
  } = req.body;

  if (!user_id) return res.status(400).json({ error: "Thiếu user_id" });

  try {
    const checkPending = await query(
      "SELECT * FROM profile_requests WHERE user_id = $1 AND status = 'pending'",
      [user_id]
    );

    if (checkPending.rowCount > 0) {
      return res.status(400).json({ error: "Bạn đang có yêu cầu chờ duyệt." });
    }

    // Không insert new_room và new_floor (để null)
    await query(
      `INSERT INTO profile_requests
       (user_id, new_full_name, new_phone, new_email, new_gender, new_dob, new_relationship, new_is_head)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [user_id, full_name, phone, email, gender, dob, relationship, is_head]
    );

    res.json({ success: true, message: "Đã gửi yêu cầu thay đổi thông tin." });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Lỗi server" });
  }
});

// 📋 3. [ADMIN] Lấy danh sách yêu cầu (Giữ nguyên JOIN chuẩn)
router.get("/pending", async (req, res) => {
  try {
    const result = await query(`
      SELECT pr.*,
             ui.full_name as current_name,
             u.phone as current_phone,
             ui.email as current_email,
             ui.gender as current_gender,
             TO_CHAR(ui.dob, 'YYYY-MM-DD') as current_dob,
             a.apartment_number as current_room,
             r.relationship_with_the_head_of_household as current_relationship

      FROM profile_requests pr
      JOIN user_item ui ON pr.user_id = ui.user_id
      LEFT JOIN users u ON ui.user_id = u.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id

      WHERE pr.status = 'pending'
      ORDER BY pr.created_at ASC
    `);
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Lỗi server" });
  }
});

// ✅ 4. [ADMIN] Duyệt/Từ chối
router.post("/resolve", async (req, res) => {
  const { request_id, action } = req.body;

  if (!['approve', 'reject'].includes(action)) return res.status(400).json({ error: "Action không hợp lệ" });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    const reqResult = await client.query("SELECT * FROM profile_requests WHERE request_id = $1", [request_id]);
    if (reqResult.rowCount === 0) throw new Error("Không tìm thấy yêu cầu");
    const request = reqResult.rows[0];

    if (action === 'approve') {
      // Cập nhật thông tin cơ bản (Không cập nhật room/floor)
      // Lưu ý: Cập nhật 'relationship' cần logic phức tạp hơn nếu bảng 'relationship'
      // liên kết chặt với apartment_id. Ở đây tạm thời update text nếu có thể hoặc bỏ qua.
      // Dưới đây chỉ update thông tin cá nhân an toàn.

      await client.query(
        `UPDATE user_item
         SET full_name = COALESCE($1, full_name),
             email = COALESCE($2, email),
             gender = COALESCE($3, gender),
             dob = COALESCE($4, dob)
         WHERE user_id = $5`,
        [request.new_full_name, request.new_email, request.new_gender, request.new_dob, request.user_id]
      );

      if (request.new_phone) {
        await client.query(`UPDATE users SET phone = $1 WHERE user_id = $2`, [request.new_phone, request.user_id]);
      }
    }

    await client.query("UPDATE profile_requests SET status = $1 WHERE request_id = $2",
      [action === 'approve' ? 'approved' : 'rejected', request_id]);

    await client.query("COMMIT");
    res.json({ success: true, message: "Đã xử lý yêu cầu." });

  } catch (err) {
    await client.query("ROLLBACK");
    res.status(500).json({ error: err.message });
  } finally {
    client.release();
  }
});

export default router;