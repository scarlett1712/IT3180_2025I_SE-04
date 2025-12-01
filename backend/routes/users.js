import express from "express";
import { pool } from "../db.js";
import bcrypt from "bcryptjs";
import crypto from "crypto"; // 🔥 Import thư viện tạo chuỗi ngẫu nhiên

const router = express.Router();

// 🛠️ TẠO BẢNG login_requests NẾU CHƯA CÓ
(async () => {
  try {
    await pool.query(`
      CREATE TABLE IF NOT EXISTS login_requests (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL,
        status VARCHAR(20) DEFAULT 'pending', -- pending, approved, rejected
        temp_token VARCHAR(255), -- Token tạm cho máy mới
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );
    `);
    console.log("✅ Table 'login_requests' ready.");
  } catch (err) {
    console.error("Error creating login_requests table", err);
  }
})();

/* ==========================================================
   🟢 API: Đăng nhập (Logic Mới - Hỗ trợ bảo mật thiết bị)
========================================================== */
router.post("/login", async (req, res) => {
  try {
    const { phone, password, is_polling, request_id } = req.body || {};

    // ------------------------------------------------------------
    // 🔄 CASE 1: MÁY MỚI (Máy B) ĐANG HỎI THĂM KẾT QUẢ (POLLING)
    // ------------------------------------------------------------
    if (is_polling) {
        if (!request_id) return res.status(400).json({ error: "Thiếu request_id" });

        const reqRes = await pool.query("SELECT * FROM login_requests WHERE id = $1", [request_id]);
        if (reqRes.rows.length === 0) return res.status(404).json({ error: "Yêu cầu không tồn tại hoặc đã hết hạn" });

        const request = reqRes.rows[0];

        // Nếu vẫn đang chờ
        if (request.status === 'pending') {
            return res.json({ status: 'pending' });
        }

        // Nếu bị từ chối
        if (request.status === 'rejected') {
            await pool.query("DELETE FROM login_requests WHERE id = $1", [request_id]);
            return res.status(403).json({ error: "Đăng nhập bị từ chối bởi thiết bị chính." });
        }

        // ✅ Nếu được DUYỆT (approved) -> Thực hiện đăng nhập thật

        // 1. Cập nhật token thật vào bảng users (Token này đã tạo sẵn trong bảng request)
        await pool.query("UPDATE users SET session_token = $1 WHERE user_id = $2", [request.temp_token, request.user_id]);

        // 2. Xóa request đã xong
        await pool.query("DELETE FROM login_requests WHERE id = $1", [request_id]);

        // 3. Lấy lại thông tin User để trả về cho App (giống logic đăng nhập thường)
        const userRes = await pool.query(`SELECT u.user_id, u.phone, ur.role_id FROM users u LEFT JOIN userrole ur ON u.user_id = ur.user_id WHERE u.user_id = $1`, [request.user_id]);
        const user = userRes.rows[0];

        const infoRes = await pool.query(
          `SELECT ui.full_name, ui.gender, TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob, ui.email,
                  r.relationship_with_the_head_of_household AS relationship, a.apartment_number AS room
           FROM user_item ui
           LEFT JOIN relationship r ON ui.relationship = r.relationship_id
           LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
           WHERE ui.user_id = $1`,
          [user.user_id]
        );
        const info = infoRes.rows.length > 0 ? infoRes.rows[0] : {};
        const role = user.role_id === 2 ? "ADMIN" : "USER";

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
                room: info.room || "",
                relationship: info.relationship || "",
            }
        });
    }

    // ------------------------------------------------------------
    // 🟢 CASE 2: ĐĂNG NHẬP LẦN ĐẦU (Gửi User/Pass)
    // ------------------------------------------------------------

    if (!phone || !password) {
      return res.status(400).json({ error: "Thiếu số điện thoại hoặc mật khẩu." });
    }

    // 🔹 Lấy thông tin cơ bản + Session Token
    const userRes = await pool.query(
      `SELECT u.user_id, u.phone, u.password_hash, ur.role_id, u.session_token
       FROM users u
       LEFT JOIN userrole ur ON u.user_id = ur.user_id
       WHERE u.phone = $1`,
      [phone]
    );

    if (userRes.rows.length === 0) {
      return res.status(404).json({ error: "Số điện thoại không tồn tại." });
    }

    const user = userRes.rows[0];
    const match = await bcrypt.default.compare(password, user.password_hash);
    if (!match) {
      return res.status(401).json({ error: "Sai mật khẩu." });
    }

    // 🔥 LOGIC BẢO MẬT: Kiểm tra xem đã có ai đăng nhập chưa
    if (user.session_token) {
        // Đã có máy khác (Máy A) đang giữ token -> Tạo yêu cầu duyệt
        const tempToken = crypto.randomBytes(32).toString('hex');
        const insertReq = await pool.query(
            "INSERT INTO login_requests (user_id, temp_token) VALUES ($1, $2) RETURNING id",
            [user.user_id, tempToken]
        );

        // Trả về tín hiệu "Cần duyệt"
        return res.json({
            require_approval: true,
            request_id: insertReq.rows[0].id,
            message: "Tài khoản đang đăng nhập nơi khác. Vui lòng xác nhận trên thiết bị cũ."
        });
    }

    // 🟢 Nếu chưa ai đăng nhập -> Đăng nhập ngay (Cấp token luôn)
    const sessionToken = crypto.randomBytes(32).toString('hex');
    await pool.query(
        "UPDATE users SET session_token = $1 WHERE user_id = $2",
        [sessionToken, user.user_id]
    );

    // 🔹 Lấy thêm thông tin chi tiết
    const infoRes = await pool.query(
      `
      SELECT
        ui.full_name,
        ui.gender,
        TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob,
        ui.email,
        r.relationship_with_the_head_of_household AS relationship,
        a.apartment_number AS room
      FROM user_item ui
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE ui.user_id = $1
      `,
      [user.user_id]
    );

    const info = infoRes.rows.length > 0 ? infoRes.rows[0] : {};
    const role = user.role_id === 2 ? "ADMIN" : "USER";

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
   🔔 API MỚI: Kiểm tra yêu cầu đăng nhập (Dành cho Máy A)
========================================================== */
router.get("/check_pending_login/:userId", async (req, res) => {
    try {
        const { userId } = req.params;
        const result = await pool.query(
            "SELECT * FROM login_requests WHERE user_id = $1 AND status = 'pending' ORDER BY created_at DESC LIMIT 1",
            [userId]
        );
        res.json(result.rows);
    } catch (err) {
        res.status(500).json({ error: "Lỗi server" });
    }
});

/* ==========================================================
   ✅ API MỚI: Duyệt/Hủy yêu cầu (Dành cho Máy A)
========================================================== */
router.post("/resolve_login", async (req, res) => {
    try {
        const { request_id, action } = req.body; // action: 'approve' | 'reject'
        await pool.query("UPDATE login_requests SET status = $1 WHERE id = $2", [action, request_id]);
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: "Lỗi server" });
    }
});

/* ==========================================================
   🟢 API: Tạo tài khoản Ban Quản Trị (Admin)
========================================================== */
router.post("/create_admin", async (req, res) => {
  const client = await pool.connect();

  try {
    const { phone, password, full_name, gender, dob, email } = req.body || {};

    if (!phone || !password || !full_name) {
      return res.status(400).json({ error: "Thiếu thông tin bắt buộc." });
    }

    await client.query("BEGIN");

    // 1️⃣ Kiểm tra trùng số điện thoại
    const exists = await client.query("SELECT 1 FROM users WHERE phone = $1", [phone]);
    if (exists.rows.length > 0) {
      await client.query("ROLLBACK");
      return res.status(400).json({ error: "Số điện thoại đã tồn tại." });
    }

    // 2️⃣ Hash mật khẩu
    const passwordHash = await bcrypt.default.hash(password, 10);

    // 3️⃣ Thêm vào bảng users
    const insertUser = await client.query(
      `INSERT INTO users (password_hash, phone, created_at, updated_at)
       VALUES ($1, $2, NOW(), NOW()) RETURNING user_id`,
      [passwordHash, phone]
    );
    const user_id = insertUser.rows[0].user_id;

    // 4️⃣ Thêm vào user_item (🟢 Lưu giới tính tiếng Việt)
    await client.query(
      `INSERT INTO user_item (user_id, full_name, gender, dob, email, is_living)
       VALUES ($1, $2, $3, $4, $5, TRUE)`,
      [user_id, full_name, gender || "Khác", dob || null, email || null]
    );

    // 5️⃣ Gán quyền ADMIN
    await client.query(`INSERT INTO userrole (user_id, role_id) VALUES ($1, 2)`, [user_id]);

    await client.query("COMMIT");

    return res.json({
      message: "✅ Tạo tài khoản Ban Quản Trị thành công!",
      user_id,
      phone,
    });
  } catch (err) {
    await client.query("ROLLBACK");
    console.error("💥 [CREATE ADMIN ERROR]", err);
    return res.status(500).json({ error: "Lỗi server khi tạo tài khoản admin." });
  } finally {
    client.release();
  }
});

/* ==========================================================
   🟠 API: Đặt lại mật khẩu (Forget Password)
========================================================== */
router.post("/reset_password", async (req, res) => {
  try {
    const { phone, new_password } = req.body || {};

    if (!phone || !new_password) {
      return res.status(400).json({ error: "Thiếu số điện thoại hoặc mật khẩu mới." });
    }

    // 1️⃣ Tìm người dùng theo số điện thoại
    const userRes = await pool.query("SELECT user_id FROM users WHERE phone = $1", [phone]);
    if (userRes.rows.length === 0) {
      return res.status(404).json({ error: "Không tìm thấy tài khoản với số điện thoại này." });
    }

    // 2️⃣ Hash mật khẩu mới
    const bcrypt = await import("bcryptjs");
    const hash = await bcrypt.default.hash(new_password, 10);

    // 3️⃣ Cập nhật mật khẩu
    await pool.query(
      "UPDATE users SET password_hash = $1, updated_at = NOW() WHERE phone = $2",
      [hash, phone]
    );

    return res.json({ message: "Đặt lại mật khẩu thành công." });
  } catch (err) {
    console.error("💥 [RESET PASSWORD ERROR]", err);
    return res.status(500).json({ error: "Lỗi server khi đặt lại mật khẩu." });
  }
});

export default router;