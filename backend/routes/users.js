import express from "express";
import { pool } from "../db.js";
import bcrypt from "bcryptjs";
import crypto from "crypto";
import admin from "firebase-admin";
import "../utils/firebaseHelper.js";

const router = express.Router();

// 🛠️ KHỞI TẠO DB
(async () => {
  try {
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

    // Đảm bảo bảng user_item có cột mới
    await pool.query(`ALTER TABLE user_item ADD COLUMN IF NOT EXISTS identity_card VARCHAR(50);`);
    await pool.query(`ALTER TABLE user_item ADD COLUMN IF NOT EXISTS home_town VARCHAR(255);`);

    console.log("✅ Database schema verified.");
  } catch (err) {
    console.error("Error initializing database schema:", err);
  }
})();

/* ==========================================================
   🟢 API: Đăng nhập (Mật khẩu)
========================================================== */
router.post("/login", async (req, res) => {
  try {
    const { phone, password, is_polling, request_id, force_login } = req.body || {};

    // --- CASE 1: POLLING (Kiểm tra trạng thái phê duyệt) ---
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

        // Approved
        await pool.query("UPDATE users SET session_token = $1 WHERE user_id = $2", [request.temp_token, request.user_id]);
        await pool.query("DELETE FROM login_requests WHERE user_id = $1", [request.user_id]);

        // Lấy thông tin chi tiết User để trả về
        const userRes = await pool.query(`SELECT u.user_id, u.phone, ur.role_id FROM users u LEFT JOIN userrole ur ON u.user_id = ur.user_id WHERE u.user_id = $1`, [request.user_id]);
        const user = userRes.rows[0];
        const role = (user.role_id == 2) ? "ADMIN" : "USER";

        const infoRes = await pool.query(
          `SELECT ui.full_name, ui.gender, TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob, ui.email,
                  ui.identity_card, ui.home_town,
                  r.relationship_with_the_head_of_household AS relationship, a.apartment_number AS room
           FROM user_item ui
           LEFT JOIN relationship r ON ui.relationship = r.relationship_id
           LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
           WHERE ui.user_id = $1`,
          [user.user_id]
        );
        const info = infoRes.rows.length > 0 ? infoRes.rows[0] : {};

        return res.json({
            status: 'approved',
            message: "Đăng nhập thành công",
            session_token: request.temp_token,
            user: {
                id: user.user_id.toString(),
                phone: user.phone,
                role: role,
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

    // --- CASE 2: NORMAL LOGIN (Đăng nhập lần đầu) ---
    if (!phone || !password) return res.status(400).json({ error: "Thiếu thông tin." });

    const userRes = await pool.query(
      `SELECT u.user_id, u.phone, u.password_hash, ur.role_id, u.session_token
       FROM users u
       LEFT JOIN userrole ur ON u.user_id = ur.user_id
       WHERE u.phone = $1`,
      [phone]
    );

    if (userRes.rows.length === 0) return res.status(404).json({ error: "Số điện thoại không tồn tại." });

    const user = userRes.rows[0];
    const role = (user.role_id == 2) ? "ADMIN" : "USER";

    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) return res.status(401).json({ error: "Sai mật khẩu." });

    // Dọn dẹp request cũ
    await pool.query("DELETE FROM login_requests WHERE user_id = $1 AND created_at < NOW() - INTERVAL '5 minutes'", [user.user_id]);

    // CHECK SESSION (Nếu đã đăng nhập nơi khác)
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

    // Tạo token mới -> Đá thiết bị cũ
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
       WHERE ui.user_id = $1`,
      [user.user_id]
    );

    const info = infoRes.rows.length > 0 ? infoRes.rows[0] : {};

    return res.json({
      message: "Đăng nhập thành công",
      session_token: sessionToken,
      user: {
        id: user.user_id.toString(),
        phone: user.phone,
        role: role,
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
   🟢 API: Auth Firebase (Đã sửa lỗi trùng session)
========================================================== */
router.post("/auth/firebase", async (req, res) => {
  try {
    const { idToken, fcm_token, force_login } = req.body; // 🔥 Nhận thêm force_login

    if (!idToken) {
        return res.status(400).json({ error: "Thiếu Firebase ID Token" });
    }

    // 1. Xác thực Token với Firebase Server
    const decodedToken = await admin.auth().verifyIdToken(idToken);
    const firebasePhone = decodedToken.phone_number; // Format: +84xxxxxxxxx

    if (!firebasePhone) {
        return res.status(400).json({ error: "Token không chứa số điện thoại." });
    }

    // 2. Chuyển đổi định dạng số điện thoại
    let dbPhone = firebasePhone.replace("+84", "0");
    console.log(`📲 [FIREBASE AUTH] Verified phone: ${firebasePhone} -> DB Check: ${dbPhone}`);

    // 3. Tìm user trong DB
    const userRes = await pool.query(
        `SELECT u.user_id, u.phone, ur.role_id, u.session_token
         FROM users u
         LEFT JOIN userrole ur ON u.user_id = ur.user_id
         WHERE u.phone = $1 OR u.phone = $2`,
        [dbPhone, firebasePhone]
    );

    if (userRes.rows.length === 0) {
        return res.status(404).json({
            error: "Số điện thoại chưa được đăng ký trong hệ thống.",
            phone: dbPhone
        });
    }

    const user = userRes.rows[0];

    // 🔥 4. KIỂM TRA SESSION (LOGIC MỚI THÊM)
    // Nếu user đang có session (đang online máy khác) VÀ không phải force_login
    if (user.session_token && !force_login) {
        const tempToken = crypto.randomBytes(32).toString('hex');

        // Tạo yêu cầu login request
        const insertReq = await pool.query(
            "INSERT INTO login_requests (user_id, temp_token) VALUES ($1, $2) RETURNING id",
            [user.user_id, tempToken]
        );

        // Trả về yêu cầu phê duyệt cho App Android
        return res.json({
            require_approval: true,
            request_id: insertReq.rows[0].id,
            allow_force_login: true,
            message: "Tài khoản đang đăng nhập nơi khác. Bạn có muốn tiếp tục?"
        });
    }

    // 5. Đăng nhập thành công (Cấp session_token MỚI -> Đá máy cũ)
    const sessionToken = crypto.randomBytes(32).toString('hex');

    // Xóa request cũ
    await pool.query("DELETE FROM login_requests WHERE user_id = $1", [user.user_id]);

    // Cập nhật session_token và fcm_token
    if (fcm_token) {
        await pool.query(
            "UPDATE users SET session_token = $1, fcm_token = $2 WHERE user_id = $3",
            [sessionToken, fcm_token, user.user_id]
        );
    } else {
        await pool.query(
            "UPDATE users SET session_token = $1 WHERE user_id = $2",
            [sessionToken, user.user_id]
        );
    }

    // 6. Lấy thông tin chi tiết
    const infoRes = await pool.query(
      `SELECT ui.full_name, ui.gender, TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob, ui.email,
              ui.identity_card, ui.home_town,
              r.relationship_with_the_head_of_household AS relationship, a.apartment_number AS room
       FROM user_item ui
       LEFT JOIN relationship r ON ui.relationship = r.relationship_id
       LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
       WHERE ui.user_id = $1`,
      [user.user_id]
    );

    const info = infoRes.rows.length > 0 ? infoRes.rows[0] : {};
    const role = user.role_id === 2 ? "ADMIN" : "USER";

    // 7. Trả về response thành công
    return res.json({
      message: "Xác thực Firebase thành công",
      session_token: sessionToken,
      user: {
        id: user.user_id.toString(),
        phone: user.phone,
        role: role,
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
   Các API phụ trợ (Giữ nguyên)
========================================================== */

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

router.post("/logout", async (req, res) => {
    try {
        const { user_id } = req.body;
        if (user_id) {
            await pool.query("UPDATE users SET session_token = NULL WHERE user_id = $1", [user_id]);
            await pool.query("DELETE FROM login_requests WHERE user_id = $1", [user_id]);
        }
        res.json({ success: true, message: "Đã đăng xuất." });
    } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

router.post("/update_fcm_token", async (req, res) => {
    try {
        const { user_id, fcm_token } = req.body;
        if (!user_id || !fcm_token) return res.status(400).json({ error: "Thiếu thông tin." });
        await pool.query("UPDATE users SET fcm_token = $1 WHERE user_id = $2", [fcm_token, user_id]);
        res.json({ success: true, message: "Đã cập nhật token thông báo." });
    } catch (err) { console.error("FCM Update Error:", err); res.status(500).json({ error: "Lỗi server." }); }
});

router.post("/create_admin", async (req, res) => {
  const client = await pool.connect();
  try {
    const { phone, password, full_name, gender, dob, email } = req.body || {};
    if (!phone || !password || !full_name) return res.status(400).json({ error: "Thiếu thông tin bắt buộc." });

    await client.query("BEGIN");
    const exists = await client.query("SELECT 1 FROM users WHERE phone = $1", [phone]);
    if (exists.rows.length > 0) {
      await client.query("ROLLBACK");
      return res.status(400).json({ error: "Số điện thoại đã tồn tại." });
    }
    const passwordHash = await bcrypt.hash(password, 10);
    const insertUser = await client.query(`INSERT INTO users (password_hash, phone, created_at, updated_at) VALUES ($1, $2, NOW(), NOW()) RETURNING user_id`, [passwordHash, phone]);
    const user_id = insertUser.rows[0].user_id;
    await client.query(
      `INSERT INTO user_item (user_id, full_name, gender, dob, email, is_living)
       VALUES ($1, $2, $3, $4, $5, TRUE)`,
      [user_id, full_name, gender || "Khác", dob || null, email || null]
    );
    await client.query(`INSERT INTO userrole (user_id, role_id) VALUES ($1, 2)`, [user_id]);
    await client.query("COMMIT");
    return res.json({ message: "✅ Tạo tài khoản Ban Quản Trị thành công!", user_id, phone });
  } catch (err) {
    await client.query("ROLLBACK");
    console.error("💥 [CREATE ADMIN ERROR]", err);
    return res.status(500).json({ error: "Lỗi server khi tạo tài khoản admin." });
  } finally { client.release(); }
});

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

export default router;