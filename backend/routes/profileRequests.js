import express from "express";
import { pool } from "../db.js";
// 🔥 Import Helper thông báo (đảm bảo file này tồn tại)
import { sendMulticastNotification, sendNotification } from "../utils/firebaseHelper.js";

const router = express.Router();
const query = (text, params) => pool.query(text, params);

// 🛠️ 1. KHỞI TẠO DATABASE
const createTableQuery = `
  CREATE TABLE IF NOT EXISTS profile_requests (
    request_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    new_full_name VARCHAR(255),
    new_phone VARCHAR(20),
    new_email VARCHAR(255),
    new_gender VARCHAR(50),
    new_dob DATE,
    new_identity_card VARCHAR(50),
    new_home_town VARCHAR(255),
    new_relationship VARCHAR(100), -- Lưu chuỗi text (VD: "Vợ")
    new_is_head BOOLEAN,
    new_is_living BOOLEAN,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
  );
`;

(async () => {
  try {
    await query(createTableQuery);

    // Đảm bảo các cột mới tồn tại trong bảng request
    await query(`ALTER TABLE profile_requests ADD COLUMN IF NOT EXISTS new_identity_card VARCHAR(50);`);
    await query(`ALTER TABLE profile_requests ADD COLUMN IF NOT EXISTS new_home_town VARCHAR(255);`);
    await query(`ALTER TABLE profile_requests ADD COLUMN IF NOT EXISTS new_is_living BOOLEAN;`);

    // Đảm bảo các cột mới tồn tại trong bảng chính user_item (như file create_user)
    await query(`ALTER TABLE user_item ADD COLUMN IF NOT EXISTS identity_card VARCHAR(50);`);
    await query(`ALTER TABLE user_item ADD COLUMN IF NOT EXISTS home_town VARCHAR(255);`);
    await query(`ALTER TABLE user_item ADD COLUMN IF NOT EXISTS is_living BOOLEAN DEFAULT TRUE;`);

    console.log("✅ Database schema verified (profile_requests).");
  } catch (err) {
    console.error("Error initializing profile requests schema:", err);
  }
})();

// 📤 2. [USER] Gửi yêu cầu thay đổi thông tin
router.post("/create", async (req, res) => {
  const {
    user_id, full_name, phone, email, gender, dob,
    identity_card, home_town, // 🔥 Nhận thêm 2 trường này
    relationship, is_head,    // relationship ở đây là TEXT (VD: "Con")
    is_living
  } = req.body;

  if (!user_id) return res.status(400).json({ error: "Thiếu user_id" });

  try {
    // Kiểm tra xem có yêu cầu nào đang chờ không
    const checkPending = await query(
      "SELECT * FROM profile_requests WHERE user_id = $1 AND status = 'pending'",
      [user_id]
    );

    if (checkPending.rowCount > 0) {
      return res.status(400).json({ error: "Bạn đang có yêu cầu chờ duyệt." });
    }

    // Insert yêu cầu
    await query(
      `INSERT INTO profile_requests
       (user_id, new_full_name, new_phone, new_email, new_gender, new_dob,
        new_identity_card, new_home_town,
        new_relationship, new_is_head, new_is_living)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)`,
      [user_id, full_name, phone, email, gender, dob, identity_card, home_town, relationship, is_head, is_living]
    );

    // Gửi thông báo cho Admin (Ban quản trị)
    const adminTokensRes = await query(`
        SELECT u.fcm_token
        FROM users u
        JOIN userrole ur ON u.user_id = ur.user_id
        WHERE ur.role_id = 2 AND u.fcm_token IS NOT NULL AND u.fcm_token != ''
    `);

    const adminTokens = adminTokensRes.rows.map(row => row.fcm_token);
    if (adminTokens.length > 0) {
        sendMulticastNotification(
            adminTokens,
            "📋 Yêu cầu thay đổi thông tin",
            `Cư dân ${full_name} vừa gửi yêu cầu cập nhật hồ sơ.`
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
    // Join nhiều bảng để lấy thông tin hiện tại so sánh với thông tin mới
    const result = await query(`
      SELECT pr.*,
             ui.full_name as current_name,
             u.phone as current_phone,
             ui.email as current_email,
             ui.gender as current_gender,
             TO_CHAR(ui.dob, 'YYYY-MM-DD') as current_dob,
             ui.identity_card as current_identity_card, -- 🔥
             ui.home_town as current_home_town,         -- 🔥
             ui.is_living as current_is_living,

             -- Lấy tên quan hệ hiện tại từ bảng relationship thông qua ID
             r.relationship_with_the_head_of_household as current_relationship,
             a.apartment_number as current_room

      FROM profile_requests pr
      JOIN user_item ui ON pr.user_id = ui.user_id
      LEFT JOIN users u ON ui.user_id = u.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id -- Join để lấy tên quan hệ
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

// ✅ 4. [ADMIN] Duyệt/Từ chối (Logic khớp với create_user)
router.post("/resolve", async (req, res) => {
  const { request_id, action } = req.body;

  if (!['approve', 'reject'].includes(action)) return res.status(400).json({ error: "Action không hợp lệ" });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // Lấy thông tin yêu cầu
    const reqResult = await client.query("SELECT * FROM profile_requests WHERE request_id = $1", [request_id]);
    if (reqResult.rowCount === 0) throw new Error("Không tìm thấy yêu cầu");
    const request = reqResult.rows[0];

    if (action === 'approve') {

      // 1. Cập nhật thông tin cơ bản vào bảng user_item (bao gồm CCCD, Quê quán)
      // Lưu ý: Không cập nhật cột 'relationship' ở đây vì nó là ID
      await client.query(
        `UPDATE user_item
         SET full_name = COALESCE($1, full_name),
             email = COALESCE($2, email),
             gender = COALESCE($3, gender),
             dob = COALESCE($4, dob),
             identity_card = COALESCE($5, identity_card), -- 🔥 Update CCCD
             home_town = COALESCE($6, home_town),         -- 🔥 Update Quê quán
             is_living = COALESCE($7, is_living)
         WHERE user_id = $8`,
        [
            request.new_full_name,
            request.new_email,
            request.new_gender,
            request.new_dob,
            request.new_identity_card,
            request.new_home_town,
            request.new_is_living,
            request.user_id
        ]
      );

      // 2. Cập nhật Mối quan hệ và Chủ hộ (Nếu có thay đổi)
      // Theo create_user.js, thông tin này nằm ở bảng 'relationship'
      if (request.new_relationship || request.new_is_head !== null) {
          // Tìm relationship_id hiện tại của user
          const relIdRes = await client.query("SELECT relationship FROM user_item WHERE user_id = $1", [request.user_id]);

          if (relIdRes.rows.length > 0) {
              const relId = relIdRes.rows[0].relationship;

              // Update bảng relationship
              // COALESCE giúp giữ nguyên giá trị cũ nếu request không có giá trị mới
              await client.query(
                  `UPDATE relationship
                   SET relationship_with_the_head_of_household = COALESCE($1, relationship_with_the_head_of_household),
                       is_head_of_household = COALESCE($2, is_head_of_household)
                   WHERE relationship_id = $3`,
                  [request.new_relationship, request.new_is_head, relId]
              );
          }
      }

      // 3. Cập nhật SĐT (nếu có)
      if (request.new_phone) {
        await client.query(`UPDATE users SET phone = $1 WHERE user_id = $2`, [request.new_phone, request.user_id]);
      }
    }

    // Cập nhật trạng thái request
    await client.query("UPDATE profile_requests SET status = $1 WHERE request_id = $2",
      [action === 'approve' ? 'approved' : 'rejected', request_id]);

    await client.query("COMMIT");

    // Gửi thông báo kết quả
    const userTokenRes = await query("SELECT fcm_token FROM users WHERE user_id = $1", [request.user_id]);
    if (userTokenRes.rows.length > 0) {
        const userToken = userTokenRes.rows[0].fcm_token;
        if (userToken) {
            const statusMsg = action === 'approve' ? "đã được CHẤP THUẬN ✅" : "đã bị TỪ CHỐI ❌";
            sendNotification(userToken, "🔔 Kết quả cập nhật hồ sơ", `Yêu cầu thay đổi thông tin của bạn ${statusMsg}.`);
        }
    }

    res.json({ success: true, message: "Đã xử lý yêu cầu." });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Resolve Request Error:", err);
    res.status(500).json({ error: err.message });
  } finally {
    client.release();
  }
});

export default router;