import express from "express";
import { pool } from "../db.js";
// 🔥 Import Helper thông báo
import { sendMulticastNotification, sendNotification } from "../utils/firebaseHelper.js";

const router = express.Router();
const query = (text, params) => pool.query(text, params);

// 🛠️ 1. Cập nhật bảng profile_requests
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
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
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

// 📤 2. [USER] Gửi yêu cầu -> Báo cho Admin
router.post("/create", async (req, res) => {
  const {
    user_id, full_name, phone, email, gender, dob,
    relationship, is_head
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

    await query(
      `INSERT INTO profile_requests
       (user_id, new_full_name, new_phone, new_email, new_gender, new_dob, new_relationship, new_is_head)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [user_id, full_name, phone, email, gender, dob, relationship, is_head]
    );

    // 🔥 GỬI THÔNG BÁO CHO ADMIN
    // 1. Tìm token của tất cả ADMIN (role_id = 2)
    const adminTokensRes = await query(`
        SELECT u.fcm_token
        FROM users u
        JOIN userrole ur ON u.user_id = ur.user_id
        WHERE ur.role_id = 2 AND u.fcm_token IS NOT NULL AND u.fcm_token != ''
    `);

    // 2. Lấy danh sách token
    const adminTokens = adminTokensRes.rows.map(row => row.fcm_token);

    // 3. Gửi thông báo (Fire-and-forget, không cần await để User không phải chờ)
    if (adminTokens.length > 0) {
        sendMulticastNotification(
            adminTokens,
            "📋 Yêu cầu thay đổi thông tin",
            `Cư dân ${full_name} vừa gửi yêu cầu cập nhật hồ sơ. Vui lòng kiểm tra.`
        );
    }

    res.json({ success: true, message: "Đã gửi yêu cầu thay đổi thông tin." });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Lỗi server" });
  }
});

// 📋 3. [ADMIN] Lấy danh sách yêu cầu
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

// ✅ 4. [ADMIN] Duyệt/Từ chối -> Báo cho Cư dân
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

    // 🔥 GỬI THÔNG BÁO CHO CƯ DÂN
    // 1. Lấy token của người gửi yêu cầu
    const userTokenRes = await query("SELECT fcm_token FROM users WHERE user_id = $1", [request.user_id]);

    if (userTokenRes.rows.length > 0) {
        const userToken = userTokenRes.rows[0].fcm_token;
        if (userToken) {
            const statusMsg = action === 'approve' ? "đã được CHẤP THUẬN ✅" : "đã bị TỪ CHỐI ❌";
            const msg = `Yêu cầu thay đổi thông tin hồ sơ của bạn ${statusMsg}.`;

            sendNotification(userToken, "Kết quả cập nhật hồ sơ", msg);
        }
    }

    res.json({ success: true, message: "Đã xử lý yêu cầu." });

  } catch (err) {
    await client.query("ROLLBACK");
    res.status(500).json({ error: err.message });
  } finally {
    client.release();
  }
});

export default router;