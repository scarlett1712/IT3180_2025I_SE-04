import express from "express";
import { pool } from "../db.js";
import bcrypt from "bcryptjs";
import crypto from "crypto";
import admin from "firebase-admin";
import "../utils/firebaseHelper.js";
// 🔥 IMPORT MIDDLEWARE
import { verifySession } from "../middleware/authMiddleware.js";

const router = express.Router();

// 🛠️ 1. HÀM KHỞI TẠO DB VỚI CƠ CHẾ THỬ LẠI (RETRY)
const wait = (ms) => new Promise(resolve => setTimeout(resolve, ms));

const initSchemaWithRetry = async (retries = 10) => {
  for (let i = 0; i < retries; i++) {
    try {
      await pool.query("SELECT 1");

      await pool.query(`
        CREATE TABLE IF NOT EXISTS login_requests (
          id SERIAL PRIMARY KEY,
          user_id INTEGER NOT NULL,
          status VARCHAR(20) DEFAULT 'pending',
          temp_token VARCHAR(255),
          created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
        );
      `);
      await pool.query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS session_token VARCHAR(255);`);
      await pool.query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS fcm_token TEXT;`);
      await pool.query(`ALTER TABLE user_item ADD COLUMN IF NOT EXISTS identity_card VARCHAR(50);`);
      await pool.query(`ALTER TABLE user_item ADD COLUMN IF NOT EXISTS home_town VARCHAR(255);`);
      await pool.query(`ALTER TABLE user_item ADD COLUMN IF NOT EXISTS is_living BOOLEAN DEFAULT TRUE;`);

      console.log("✅ Database schema verified (Users).");
      return;

    } catch (err) {
      if (err.code === '57P03' || err.code === 'ECONNREFUSED') {
        console.log(`⏳ Database đang khởi động... Thử lại sau 3s (${i + 1}/${retries})`);
        await wait(3000);
      } else {
        console.error("❌ Error initializing database schema:", err);
        break;
      }
    }
  }
  console.error("❌ Không thể kết nối Database sau nhiều lần thử.");
};

initSchemaWithRetry();

// 🔥 HELPER: Hàm chuyển đổi Role ID sang tên Role
const getRoleName = (roleId) => {
    if (roleId === 2) return "ADMIN";
    if (roleId === 3) return "ACCOUNTANT"; // ✅ Kế toán
    return "USER";
};

/* ==========================================================
   🔍 API: Lấy thông tin chi tiết (BẢO MẬT)
========================================================== */
router.get("/profile/:user_id", verifySession, async (req, res) => {
  try {
    const { user_id } = req.params;

    const result = await pool.query(`
      SELECT
        u.user_id, u.phone, ui.email,
        ui.full_name,
        TO_CHAR(ui.dob, 'YYYY-MM-DD') as dob,
        ui.gender,
        ui.identity_card,
        ui.home_town,
        ui.relationship,
        ur.role_id,
        a.apartment_number as room,
        r.relationship_with_the_head_of_household as relationship_name,
        r.is_head_of_household as is_head
      FROM users u
      JOIN user_item ui ON u.user_id = ui.user_id
      LEFT JOIN userrole ur ON u.user_id = ur.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE u.user_id = $1
    `, [user_id]);

    if (result.rows.length === 0) {
      return res.status(404).json({ error: "Không tìm thấy user" });
    }

    const userData = result.rows[0];

    // 🔥 Thêm thông tin Role vào response
    userData.role_name = getRoleName(userData.role_id);

    res.json({ success: true, user: userData });

  } catch (err) {
    console.error("Get Profile Error:", err);
    res.status(500).json({ error: "Lỗi server" });
  }
});

/* ==========================================================
   🟢 API: Đăng nhập (Mật khẩu)
========================================================== */
router.post("/login", async (req, res) => {
  try {
    const { phone, password, is_polling, request_id, force_login } = req.body || {};

    // --- LOGIC POLLING (Khi chờ duyệt thiết bị cũ) ---
    if (is_polling) {
        if (!request_id) return res.status(400).json({ error: "Thiếu request_id" });
        const reqRes = await pool.query("SELECT * FROM login_requests WHERE id = $1", [request_id]);
        if (reqRes.rows.length === 0) return res.status(404).json({ error: "Yêu cầu không tồn tại hoặc đã hết hạn" });
        const request = reqRes.rows[0];

        if (request.status === 'pending') return res.json({ status: 'pending' });
        if (request.status === 'rejected') {
            await pool.query("DELETE FROM login_requests WHERE id = $1", [request_id]);
            return res.status(403).json({ error: "Đăng nhập bị từ chối bởi thiết bị chính." });
        }

        await pool.query("UPDATE users SET session_token = $1 WHERE user_id = $2", [request.temp_token, request.user_id]);
        await pool.query("DELETE FROM login_requests WHERE user_id = $1", [request.user_id]);

        const userRes = await pool.query(`SELECT u.user_id, u.phone, ur.role_id FROM users u LEFT JOIN userrole ur ON u.user_id = ur.user_id WHERE u.user_id = $1`, [request.user_id]);
        const user = userRes.rows[0];

        // 🔥 Xác định Role
        const roleName = getRoleName(user.role_id);

        const infoRes = await pool.query(
          `SELECT ui.full_name, ui.gender, TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob, ui.email,
                  ui.identity_card, ui.home_town,
                  r.relationship_with_the_head_of_household AS relationship, a.apartment_number AS room
           FROM user_item ui
           LEFT JOIN relationship r ON ui.relationship = r.relationship_id
           LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
           WHERE ui.user_id = $1`, [user.user_id]
        );
        const info = infoRes.rows.length > 0 ? infoRes.rows[0] : {};

        return res.json({
            status: 'approved',
            message: "Đăng nhập thành công",
            session_token: request.temp_token,
            user: {
                id: user.user_id.toString(),
                phone: user.phone,
                role: roleName,        // String: ADMIN/USER/ACCOUNTANT
                role_id: user.role_id, // 🔥 Integer: 1/2/3 (Quan trọng cho Java)
                name: info.full_name || user.phone,
                gender: info.gender || "Khác",
                dob: info.dob || "01-01-2000",
                email: info.email || "",
                identity_card: info.identity_card || "",
                home_town: info.home_town || "",
                room: info.room || "",
                relationship: info.relationship || "",
            }
        });
    }

    // --- LOGIC ĐĂNG NHẬP CHÍNH ---
    if (!phone || !password) return res.status(400).json({ error: "Thiếu thông tin." });

    const userRes = await pool.query(
      `SELECT u.user_id, u.phone, u.password_hash, ur.role_id, u.session_token
       FROM users u
       LEFT JOIN userrole ur ON u.user_id = ur.user_id
       WHERE u.phone = $1`, [phone]
    );

    if (userRes.rows.length === 0) return res.status(404).json({ error: "Số điện thoại không tồn tại." });

    const user = userRes.rows[0];

    // 🔥 Xác định Role
    const roleName = getRoleName(user.role_id);

    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) return res.status(401).json({ error: "Sai mật khẩu." });

    await pool.query("DELETE FROM login_requests WHERE user_id = $1 AND created_at < NOW() - INTERVAL '5 minutes'", [user.user_id]);

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

    const infoRes = await pool.query(
      `SELECT ui.full_name, ui.gender, TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob, ui.email,
              ui.identity_card, ui.home_town,
              r.relationship_with_the_head_of_household AS relationship, a.apartment_number AS room
       FROM user_item ui
       LEFT JOIN relationship r ON ui.relationship = r.relationship_id
       LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
       WHERE ui.user_id = $1`, [user.user_id]
    );

    const info = infoRes.rows.length > 0 ? infoRes.rows[0] : {};

    return res.json({
      message: "Đăng nhập thành công",
      session_token: sessionToken,
      user: {
        id: user.user_id.toString(),
        phone: user.phone,
        role: roleName,        // String: ADMIN/USER/ACCOUNTANT
        role_id: user.role_id, // 🔥 Integer: 1/2/3
        name: info.full_name || user.phone,
        gender: info.gender || "Khác",
        dob: info.dob || "01-01-2000",
        email: info.email || "",
        identity_card: info.identity_card || "",
        home_town: info.home_town || "",
        room: info.room || "",
        relationship: info.relationship || "",
      },
    });
  } catch (err) {
    console.error("💥 [LOGIN ERROR]", err);
    res.status(500).json({ error: "Lỗi server khi đăng nhập." });
  }
});

/* ==========================================================
   🟢 API: Auth Firebase (OTP Login)
========================================================== */
router.post("/auth/firebase", async (req, res) => {
  try {
    const { idToken, fcm_token, force_login } = req.body;
    if (!idToken) return res.status(400).json({ error: "Thiếu Firebase ID Token" });

    const decodedToken = await admin.auth().verifyIdToken(idToken);
    const firebasePhone = decodedToken.phone_number;
    if (!firebasePhone) return res.status(400).json({ error: "Token không chứa số điện thoại." });

    let dbPhone = firebasePhone.replace("+84", "0");

    const userRes = await pool.query(
        `SELECT u.user_id, u.phone, ur.role_id, u.session_token
         FROM users u
         LEFT JOIN userrole ur ON u.user_id = ur.user_id
         WHERE u.phone = $1 OR u.phone = $2`, [dbPhone, firebasePhone]
    );

    if (userRes.rows.length === 0) {
        return res.status(404).json({ error: "Số điện thoại chưa được đăng ký.", phone: dbPhone });
    }

    const user = userRes.rows[0];

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
    await pool.query("DELETE FROM login_requests WHERE user_id = $1", [user.user_id]);

    if (fcm_token) {
        await pool.query("UPDATE users SET session_token = $1, fcm_token = $2 WHERE user_id = $3", [sessionToken, fcm_token, user.user_id]);
    } else {
        await pool.query("UPDATE users SET session_token = $1 WHERE user_id = $2", [sessionToken, user.user_id]);
    }

    const infoRes = await pool.query(
      `SELECT ui.full_name, ui.gender, TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob, ui.email,
              ui.identity_card, ui.home_town,
              r.relationship_with_the_head_of_household AS relationship, a.apartment_number AS room
       FROM user_item ui
       LEFT JOIN relationship r ON ui.relationship = r.relationship_id
       LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
       WHERE ui.user_id = $1`, [user.user_id]
    );

    const info = infoRes.rows.length > 0 ? infoRes.rows[0] : {};

    // 🔥 Xác định Role cho OTP Login
    const roleName = getRoleName(user.role_id);

    return res.json({
      message: "Xác thực Firebase thành công",
      session_token: sessionToken,
      user: {
        id: user.user_id.toString(),
        phone: user.phone,
        role: roleName,        // String
        role_id: user.role_id, // 🔥 Integer
        name: info.full_name || user.phone,
        gender: info.gender || "Khác",
        dob: info.dob || "01-01-2000",
        email: info.email || "",
        identity_card: info.identity_card || "",
        home_town: info.home_town || "",
        room: info.room || "",
        relationship: info.relationship || "",
      },
    });

  } catch (error) {
    console.error("❌ [FIREBASE AUTH ERROR]", error);
    res.status(401).json({ error: "Token không hợp lệ hoặc đã hết hạn." });
  }
});

/* ==========================================================
   🟢 API: Đăng ký Admin
========================================================== */
router.post("/create_admin", async (req, res) => {
  const client = await pool.connect();
  try {
    const { phone, password, full_name, gender, dob, email, identity_card, home_town } = req.body || {};

    if (!phone || !password || !full_name) return res.status(400).json({ error: "Thiếu thông tin bắt buộc." });

    await client.query("BEGIN");

    const exists = await client.query("SELECT 1 FROM users WHERE phone = $1", [phone]);
    if (exists.rows.length > 0) {
      await client.query("ROLLBACK");
      return res.status(400).json({ error: "Số điện thoại đã tồn tại." });
    }

    const passwordHash = await bcrypt.hash(password, 10);
    const insertUser = await client.query(
        `INSERT INTO users (password_hash, phone, created_at, updated_at) VALUES ($1, $2, NOW(), NOW()) RETURNING user_id`,
        [passwordHash, phone]
    );
    const user_id = insertUser.rows[0].user_id;

    await client.query(
      `INSERT INTO user_item (user_id, full_name, gender, dob, email, identity_card, home_town, is_living)
       VALUES ($1, $2, $3, $4, $5, $6, $7, TRUE)`,
      [user_id, full_name, gender || "Khác", dob || null, email || null, identity_card || null, home_town || null]
    );

    // Mặc định Admin là Role ID 2
    await client.query(`INSERT INTO userrole (user_id, role_id) VALUES ($1, 2)`, [user_id]);

    await client.query("COMMIT");
    return res.json({ message: "✅ Tạo tài khoản Ban Quản Trị thành công!", user_id, phone });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("💥 [CREATE ADMIN ERROR]", err);
    return res.status(500).json({ error: "Lỗi server khi tạo tài khoản admin." });
  } finally { client.release(); }
});

/* ==========================================================
   Các API phụ trợ (Logout, Reset Pass...)
========================================================== */

router.post("/reset_password", async (req, res) => {
  try {
    const { phone, new_password } = req.body || {};
    if (!phone || !new_password) return res.status(400).json({ error: "Thiếu thông tin." });
    const userRes = await pool.query("SELECT user_id FROM users WHERE phone = $1", [phone]);
    if (userRes.rows.length === 0) return res.status(404).json({ error: "Không tìm thấy tài khoản." });

    const hash = await bcrypt.hash(new_password, 10);
    await pool.query("UPDATE users SET password_hash = $1, updated_at = NOW() WHERE phone = $2", [hash, phone]);

    await pool.query("UPDATE users SET session_token = NULL WHERE user_id = $1", [userRes.rows[0].user_id]);
    await pool.query("DELETE FROM login_requests WHERE user_id = $1", [userRes.rows[0].user_id]);

    return res.json({ message: "Đặt lại mật khẩu thành công." });
  } catch (err) {
    console.error("💥 [RESET PASSWORD ERROR]", err);
    return res.status(500).json({ error: "Lỗi server." });
  }
});

router.get("/check_pending_login/:userId", async (req, res) => {
    try {
        const { userId } = req.params;
        const result = await pool.query("SELECT * FROM login_requests WHERE user_id = $1 AND status = 'pending' ORDER BY created_at DESC LIMIT 1", [userId]);
        res.json(result.rows);
    } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

router.post("/resolve_login", async (req, res) => {
    try {
        const { request_id, action } = req.body;
        await pool.query("UPDATE login_requests SET status = $1 WHERE id = $2", [action, request_id]);
        res.json({ success: true });
    } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

router.post("/logout", verifySession, async (req, res) => {
    try {
        const { user_id } = req.body;
        const targetId = req.currentUser ? req.currentUser.id : user_id;

        if (targetId) {
            await pool.query(
                `UPDATE users SET session_token = NULL, fcm_token = NULL WHERE user_id = $1`,
                [targetId]
            );
            await pool.query("DELETE FROM login_requests WHERE user_id = $1", [targetId]);
        }
        res.json({ success: true, message: "Đã đăng xuất và hủy nhận thông báo." });
    } catch (err) {
        console.error("Logout Error:", err);
        res.status(500).json({ error: "Lỗi server." });
    }
});

router.post("/update_fcm_token", verifySession, async (req, res) => {
    try {
        const { fcm_token } = req.body;
        const user_id = req.currentUser.id;

        if (!fcm_token) return res.status(400).json({ error: "Thiếu thông tin." });
        await pool.query("UPDATE users SET fcm_token = $1 WHERE user_id = $2", [fcm_token, user_id]);
        res.json({ success: true, message: "Đã cập nhật token thông báo." });
    } catch (err) { console.error("FCM Update Error:", err); res.status(500).json({ error: "Lỗi server." }); }
});

export default router;