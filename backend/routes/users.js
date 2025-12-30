import express from "express";
import { pool } from "../db.js";
import bcrypt from "bcryptjs";
import crypto from "crypto";
import admin from "firebase-admin";
import "../utils/firebaseHelper.js";
import { verifySession } from "../middleware/authMiddleware.js";

const router = express.Router();

// ==================================================================
// 🛠️ 1. KHỞI TẠO DB (RETRY MECHANISM)
// ==================================================================
const wait = (ms) => new Promise(resolve => setTimeout(resolve, ms));

const initSchemaWithRetry = async (retries = 10) => {
  for (let i = 0; i < retries; i++) {
    try {
      await pool.query("SELECT 1");
      // Tạo bảng login_requests nếu chưa có
      await pool.query(`
        CREATE TABLE IF NOT EXISTS login_requests (
          id SERIAL PRIMARY KEY,
          user_id INTEGER NOT NULL,
          status VARCHAR(20) DEFAULT 'pending',
          temp_token VARCHAR(255),
          created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
        );
      `);
      // Thêm cột thiếu vào users và user_item (Safe Migration)
      await pool.query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS session_token VARCHAR(255);`);
      await pool.query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS fcm_token TEXT;`);
      await pool.query(`ALTER TABLE user_item ADD COLUMN IF NOT EXISTS job VARCHAR(255);`);
      await pool.query(`ALTER TABLE user_item ADD COLUMN IF NOT EXISTS identity_card VARCHAR(50);`);
      await pool.query(`ALTER TABLE user_item ADD COLUMN IF NOT EXISTS home_town VARCHAR(255);`);
      await pool.query(`ALTER TABLE user_item ADD COLUMN IF NOT EXISTS is_living BOOLEAN DEFAULT TRUE;`);

      console.log("✅ Database schema verified (Users & Auth).");
      return;
    } catch (err) {
      if (err.code === '57P03' || err.code === 'ECONNREFUSED') {
        console.log(`⏳ DB đang khởi động... Thử lại sau 3s (${i + 1}/${retries})`);
        await wait(3000);
      } else {
        console.error("❌ Init DB Error:", err);
        break;
      }
    }
  }
};
initSchemaWithRetry();

// ==================================================================
// 🛠️ HELPER FUNCTIONS
// ==================================================================
const getRoleName = (roleId) => {
    switch (roleId) {
        case 2: return "ADMIN";
        case 3: return "ACCOUNTANT";
        case 4: return "AGENCY";
        default: return "USER";
    }
};

const buildUserObject = (userRow, infoRow) => {
    return {
        id: userRow.user_id.toString(),
        familyId: infoRow.family_id ? infoRow.family_id.toString() : "0",
        phone: userRow.phone,
        role: getRoleName(userRow.role_id || 1),
        role_id: userRow.role_id || 1,
        name: infoRow.full_name || userRow.phone,
        gender: infoRow.gender || "Khác",
        dob: infoRow.dob || "01-01-2000",
        job: infoRow.job || "Không",
        email: infoRow.email || "",
        identity_card: infoRow.identity_card || "",
        home_town: infoRow.home_town || "",
        room: infoRow.room || 0,
        relationship: infoRow.relationship || "",
    };
};

// ==================================================================
// 🏠 API MỚI: LẤY THÔNG TIN CĂN HỘ CỦA TÔI
// ==================================================================
router.get("/my-apartment", verifySession, async (req, res) => {
    // Lấy ID user từ token (middleware verifySession đã giải mã)
    const currentUserId = req.currentUser.id || req.user.user_id;

    const client = await pool.connect();
    try {
        // 1. Tìm thông tin phòng của user này
        // Join qua 3 bảng: user_item -> relationship -> apartment
        const aptRes = await client.query(`
            SELECT
                a.apartment_id,
                a.apartment_number,
                a.floor,
                a.area,
                a.status,
                r.relationship_with_the_head_of_household AS my_role
            FROM user_item ui
            JOIN relationship r ON ui.relationship = r.relationship_id
            JOIN apartment a ON r.apartment_id = a.apartment_id
            WHERE ui.user_id = $1
        `, [currentUserId]);

        if (aptRes.rows.length === 0) {
            return res.status(404).json({
                error: "Bạn hiện chưa được xếp vào căn hộ nào."
            });
        }

        const apartmentInfo = aptRes.rows[0];
        const apartmentId = apartmentInfo.apartment_id;

        // 2. Lấy danh sách TOÀN BỘ thành viên trong phòng đó
        const membersRes = await client.query(`
            SELECT
                ui.user_id,
                ui.full_name,
                TO_CHAR(ui.dob, 'DD-MM-YYYY') as dob,
                u.phone,
                ui.job,
                r.relationship_with_the_head_of_household AS relationship,
                r.is_head_of_household,
                ui.avatar_path
            FROM relationship r
            JOIN user_item ui ON r.relationship_id = ui.relationship
            LEFT JOIN users u ON ui.user_id = u.user_id
            WHERE r.apartment_id = $1
            ORDER BY r.is_head_of_household DESC, ui.user_item_id ASC
        `, [apartmentId]);

        res.json({
            success: true,
            apartment: {
                id: apartmentInfo.apartment_id,
                number: apartmentInfo.apartment_number,
                floor: apartmentInfo.floor,
                area: apartmentInfo.area,
                status: apartmentInfo.status,
                my_role: apartmentInfo.my_role
            },
            members: membersRes.rows // Danh sách thành viên
        });

    } catch (err) {
        console.error("My Apartment API Error:", err);
        res.status(500).json({ error: "Lỗi Server: " + err.message });
    } finally {
        client.release();
    }
});

// ==================================================================
// 📋 API: Lấy thông tin chi tiết User
// ==================================================================
router.get("/profile/:user_id", verifySession, async (req, res) => {
  try {
    const { user_id } = req.params;

    const result = await pool.query(`
      SELECT
        u.user_id, u.phone, ui.email,
        ui.full_name,
        TO_CHAR(ui.dob, 'YYYY-MM-DD') as dob,
        ui.job, ui.gender, ui.identity_card, ui.home_town,
        ui.relationship, ur.role_id,
        a.apartment_number as room,
        a.apartment_id as family_id,
        r.relationship_with_the_head_of_household as relationship_name,
        r.is_head_of_household as is_head
      FROM users u
      JOIN user_item ui ON u.user_id = ui.user_id
      LEFT JOIN userrole ur ON u.user_id = ur.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE u.user_id = $1
    `, [user_id]);

    if (result.rows.length === 0) return res.status(404).json({ error: "Không tìm thấy user" });

    const userData = result.rows[0];
    userData.role_name = getRoleName(userData.role_id);

    res.json({ success: true, user: userData });
  } catch (err) {
    console.error("Get Profile Error:", err);
    res.status(500).json({ error: "Lỗi server" });
  }
});

// ==================================================================
// 🟢 API: Đăng nhập (Phone/Pass + Polling)
// ==================================================================
router.post("/login", async (req, res) => {
  try {
    const { phone, password, is_polling, request_id, force_login } = req.body || {};

    // --- LOGIC POLLING ---
    if (is_polling) {
        if (!request_id) return res.status(400).json({ error: "Thiếu request_id" });
        const reqRes = await pool.query("SELECT * FROM login_requests WHERE id = $1", [request_id]);
        if (reqRes.rows.length === 0) return res.status(404).json({ error: "Yêu cầu không tồn tại/hết hạn" });

        const request = reqRes.rows[0];
        if (request.status === 'pending') return res.json({ status: 'pending' });
        if (request.status === 'rejected') {
            await pool.query("DELETE FROM login_requests WHERE id = $1", [request_id]);
            return res.status(403).json({ error: "Đăng nhập bị từ chối." });
        }

        // Approved
        await pool.query("UPDATE users SET session_token = $1 WHERE user_id = $2", [request.temp_token, request.user_id]);
        await pool.query("DELETE FROM login_requests WHERE user_id = $1", [request.user_id]);

        const userAndInfo = await getUserAndInfo(request.user_id);
        return res.json({
            status: 'approved',
            message: "Đăng nhập thành công",
            session_token: request.temp_token,
            user: buildUserObject(userAndInfo.user, userAndInfo.info)
        });
    }

    // --- LOGIC LOGIN CHÍNH ---
    if (!phone || !password) return res.status(400).json({ error: "Thiếu thông tin." });

    const userRes = await pool.query(`
        SELECT u.user_id, u.phone, u.password_hash, ur.role_id, u.session_token
        FROM users u
        LEFT JOIN userrole ur ON u.user_id = ur.user_id
        WHERE u.phone = $1
    `, [phone]);

    if (userRes.rows.length === 0) return res.status(404).json({ error: "Số điện thoại không tồn tại." });
    const user = userRes.rows[0];

    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) return res.status(401).json({ error: "Sai mật khẩu." });

    // Clean request cũ
    await pool.query("DELETE FROM login_requests WHERE user_id = $1 AND created_at < NOW() - INTERVAL '5 minutes'", [user.user_id]);

    // Check login nơi khác
    if (user.session_token && !force_login) {
        const tempToken = crypto.randomBytes(32).toString('hex');
        const insertReq = await pool.query(
            "INSERT INTO login_requests (user_id, temp_token) VALUES ($1, $2) RETURNING id",
            [user.user_id, tempToken]
        );
        return res.json({
            require_approval: true,
            request_id: insertReq.rows[0].id,
            allow_force_login: true,
            message: "Tài khoản đang đăng nhập nơi khác."
        });
    }

    const sessionToken = crypto.randomBytes(32).toString('hex');
    await pool.query("UPDATE users SET session_token = $1 WHERE user_id = $2", [sessionToken, user.user_id]);
    await pool.query("DELETE FROM login_requests WHERE user_id = $1", [user.user_id]);

    const userAndInfo = await getUserAndInfo(user.user_id);
    return res.json({
        message: "Đăng nhập thành công",
        session_token: sessionToken,
        user: buildUserObject(user, userAndInfo.info)
    });

  } catch (err) {
    console.error("Login Error:", err);
    res.status(500).json({ error: "Lỗi server." });
  }
});

// ==================================================================
// 🟢 API: Firebase Auth
// ==================================================================
router.post("/auth/firebase", async (req, res) => {
  try {
    const { idToken, fcm_token, force_login } = req.body;
    if (!idToken) return res.status(400).json({ error: "Thiếu Firebase ID Token" });

    const decodedToken = await admin.auth().verifyIdToken(idToken);
    const firebasePhone = decodedToken.phone_number;
    if (!firebasePhone) return res.status(400).json({ error: "Token lỗi." });

    let dbPhone = firebasePhone.replace("+84", "0");
    const userRes = await pool.query(`
        SELECT u.user_id, u.phone, ur.role_id, u.session_token
        FROM users u LEFT JOIN userrole ur ON u.user_id = ur.user_id
        WHERE u.phone = $1 OR u.phone = $2
    `, [dbPhone, firebasePhone]);

    if (userRes.rows.length === 0) return res.status(404).json({ error: "SĐT chưa đăng ký.", phone: dbPhone });
    const user = userRes.rows[0];

    if (user.session_token && !force_login) {
        const tempToken = crypto.randomBytes(32).toString('hex');
        const insertReq = await pool.query("INSERT INTO login_requests (user_id, temp_token) VALUES ($1, $2) RETURNING id", [user.user_id, tempToken]);
        return res.json({ require_approval: true, request_id: insertReq.rows[0].id, allow_force_login: true });
    }

    const sessionToken = crypto.randomBytes(32).toString('hex');
    await pool.query("DELETE FROM login_requests WHERE user_id = $1", [user.user_id]);
    await pool.query("UPDATE users SET session_token = $1, fcm_token = COALESCE($2, fcm_token) WHERE user_id = $3", [sessionToken, fcm_token, user.user_id]);

    const userAndInfo = await getUserAndInfo(user.user_id);
    return res.json({ message: "OK", session_token: sessionToken, user: buildUserObject(user, userAndInfo.info) });

  } catch (err) { res.status(401).json({ error: "Auth Fail." }); }
});

// ==================================================================
// 👥 API Tạo Tài Khoản (Admin, Accountant, Agency)
// ==================================================================
router.post("/create_admin", async (req, res) => createStaffAccount(req, res, 2, "Admin"));
router.post("/create_accountant", async (req, res) => createStaffAccount(req, res, 3, "Accountant"));
router.post("/create_agency", async (req, res) => createStaffAccount(req, res, 4, "Agency"));

// 🔥 HÀM XỬ LÝ TẠO TÀI KHOẢN (ĐÃ FIX LỖI THAM SỐ)
async function createStaffAccount(req, res, roleId, roleName) {
  const client = await pool.connect();
  try {
    const { phone, password, full_name, email, identity_card, home_town, dob, gender, job } = req.body;

    if (!phone || !password || !full_name) return res.status(400).json({ error: "Thiếu SĐT, Pass hoặc Tên" });

    await client.query("BEGIN");

    // Check trùng
    const check = await client.query("SELECT user_id FROM users WHERE phone = $1", [phone]);
    if (check.rows.length > 0) { await client.query("ROLLBACK"); return res.status(409).json({ error: "SĐT đã tồn tại" }); }

    // Tạo User
    const hash = await bcrypt.hash(password, 10);
    const uRes = await client.query(`INSERT INTO users (password_hash, phone, created_at) VALUES ($1, $2, NOW()) RETURNING user_id`, [hash, phone]);
    const userId = uRes.rows[0].user_id;

    // Format DOB
    let fDob = '2000-01-01';
    if (dob) {
        const parts = dob.split('/');
        if (parts.length === 3) fDob = `${parts[2]}-${parts[1]}-${parts[0]}`; // dd/mm/yyyy -> yyyy-mm-dd
        else fDob = dob;
    }

    // Tạo User Item (🔥 ĐÃ SỬA THỨ TỰ THAM SỐ ĐÚNG VỚI SQL)
    await client.query(
      `INSERT INTO user_item
       (user_id, full_name, gender, dob, job, email, identity_card, home_town, is_living)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, TRUE)`,
      [
          userId,
          full_name,
          gender || 'Khác',
          fDob,
          job || null,          // Tham số $5
          email || null,        // Tham số $6
          identity_card || null,// Tham số $7
          home_town || null     // Tham số $8
      ]
    );

    // Gán Role
    await client.query(`INSERT INTO userrole (user_id, role_id) VALUES ($1, $2)`, [userId, roleId]);

    await client.query("COMMIT");
    res.json({ success: true, message: `Tạo ${roleName} thành công!` });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error(`Create ${roleName} Error:`, err);
    res.status(500).json({ error: err.message });
  } finally { client.release(); }
}

// ==================================================================
// 🛠️ CÁC API KHÁC (Reset Pass, Logout, Notify)
// ==================================================================

router.post("/reset_password", async (req, res) => {
  try {
    const { phone, new_password } = req.body;
    if (!phone || !new_password) return res.status(400).json({ error: "Thiếu info" });
    const uRes = await pool.query("SELECT user_id FROM users WHERE phone = $1", [phone]);
    if (uRes.rows.length === 0) return res.status(404).json({ error: "User not found" });

    const hash = await bcrypt.hash(new_password, 10);
    const uid = uRes.rows[0].user_id;
    await pool.query("UPDATE users SET password_hash = $1, session_token = NULL WHERE user_id = $2", [hash, uid]);
    await pool.query("DELETE FROM login_requests WHERE user_id = $1", [uid]);
    res.json({ message: "Đổi mật khẩu thành công" });
  } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

router.post("/logout", verifySession, async (req, res) => {
    try {
        const uid = req.currentUser ? req.currentUser.id : req.body.user_id;
        if (uid) {
            await pool.query("UPDATE users SET session_token = NULL, fcm_token = NULL WHERE user_id = $1", [uid]);
            await pool.query("DELETE FROM login_requests WHERE user_id = $1", [uid]);
        }
        res.json({ success: true });
    } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

router.post("/update_fcm_token", verifySession, async (req, res) => {
    try {
        const { fcm_token } = req.body;
        if (!fcm_token) return res.status(400).json({ error: "Thiếu token" });
        await pool.query("UPDATE users SET fcm_token = $1 WHERE user_id = $2", [fcm_token, req.currentUser.id]);
        res.json({ success: true });
    } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

router.get("/check_pending_login/:userId", async (req, res) => {
    const resQ = await pool.query("SELECT * FROM login_requests WHERE user_id = $1 AND status = 'pending' ORDER BY created_at DESC LIMIT 1", [req.params.userId]);
    res.json(resQ.rows);
});

router.post("/resolve_login", async (req, res) => {
    await pool.query("UPDATE login_requests SET status = $1 WHERE id = $2", [req.body.action, req.body.request_id]);
    res.json({ success: true });
});

// Helper lấy info nhanh
async function getUserAndInfo(userId) {
    const infoRes = await pool.query(`
        SELECT ui.full_name, ui.gender, TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob, ui.job, ui.email,
               ui.identity_card, ui.home_town,
               r.relationship_with_the_head_of_household AS relationship,
               a.apartment_number AS room, a.apartment_id AS family_id
        FROM user_item ui
        LEFT JOIN relationship r ON ui.relationship = r.relationship_id
        LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
        WHERE ui.user_id = $1
    `, [userId]);
    const userRes = await pool.query("SELECT user_id, phone FROM users WHERE user_id = $1", [userId]);
    return { user: userRes.rows[0], info: infoRes.rows.length > 0 ? infoRes.rows[0] : {} };
}

export default router;