import express from "express";
import { pool } from "../db.js";
import { verifySession } from "../middleware/authMiddleware.js";

const router = express.Router();
const query = (text, params) => pool.query(text, params);

// ==================================================================
// 📋 API: Lấy danh sách toàn bộ cư dân (Chi tiết)
// ==================================================================
router.get("/", async (req, res) => {
  try {
    const queryStr = `
      SELECT
        ui.user_id AS user_id,
        ui.full_name,
        ui.email,
        u.phone,
        TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob,
        ui.gender,
        ui.identity_card, -- 🔥 THÊM: CCCD
        ui.home_town,     -- 🔥 THÊM: Quê quán
        ur.role_id,
        r.relationship_id,
        r.apartment_id,
        a.apartment_number,
        a.floor,
        a.area,
        r.relationship_with_the_head_of_household,
        ui.is_living,
        ui.avatar_path
      FROM user_item ui
      LEFT JOIN users u ON ui.user_id = u.user_id
      LEFT JOIN userrole ur ON ui.user_id = ur.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE ur.role_id = 1 -- Chỉ lấy cư dân (Role ID = 1)
      ORDER BY ui.full_name;
    `;

    const result = await pool.query(queryStr);
    res.json(result.rows);
  } catch (err) {
    console.error("💥 Error fetching residents:", err);
    res.status(500).json({ error: err.message });
  }
});

// ==================================================================
// ✏️ API: Cập nhật thông tin cư dân (Dành cho Admin)
// ==================================================================
router.put("/update/:userId", async (req, res) => {
  const { userId } = req.params;
  const { full_name, gender, dob, email, phone, identity_card, home_town } = req.body;

  if (!userId) return res.status(400).json({ error: "Thiếu User ID" });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Cập nhật bảng user_item (Thông tin chi tiết bao gồm CCCD, Quê quán)
    await client.query(
      `UPDATE user_item
       SET full_name = COALESCE($1, full_name),
           gender = COALESCE($2, gender),
           dob = COALESCE($3, dob),
           email = COALESCE($4, email),
           identity_card = COALESCE($5, identity_card), -- Cập nhật CCCD
           home_town = COALESCE($6, home_town)          -- Cập nhật Quê quán
       WHERE user_id = $7`,
      [full_name, gender, dob, email, identity_card, home_town, userId]
    );

    // 2. Cập nhật số điện thoại trong bảng users (nếu có thay đổi)
    if (phone) {
      const checkPhone = await client.query(
          "SELECT user_id FROM users WHERE phone = $1 AND user_id != $2",
          [phone, userId]
      );
      if (checkPhone.rows.length > 0) {
          throw new Error("Số điện thoại mới đã được sử dụng bởi tài khoản khác.");
      }

      await client.query(
        `UPDATE users SET phone = $1 WHERE user_id = $2`,
        [phone, userId]
      );
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Cập nhật thông tin cư dân thành công." });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("❌ Error updating resident:", err);
    res.status(500).json({ error: err.message || "Lỗi server khi cập nhật thông tin." });
  } finally {
    client.release();
  }
});

/* ==========================================================
   🗑️ API: XÓA CƯ DÂN (Chỉ Admin mới được dùng)
========================================================== */
router.delete("/delete/:target_id", verifySession, async (req, res) => {
  const { target_id } = req.params;
  const currentUserId = req.currentUser.id; // Lấy từ token của người đang thao tác

  // 1. Kiểm tra quyền Admin
  if (req.currentUser.role !== 'ADMIN') {
      return res.status(403).json({ error: "Bạn không có quyền xóa cư dân." });
  }

  // 2. Không cho phép tự xóa chính mình
  if (parseInt(target_id) === parseInt(currentUserId)) {
      return res.status(400).json({ error: "Không thể tự xóa tài khoản của chính mình." });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // --- Bắt đầu dọn dẹp dữ liệu liên quan ---

    // 1. Xóa các yêu cầu đăng nhập đang chờ
    await client.query("DELETE FROM login_requests WHERE user_id = $1", [target_id]);

    // 2. Xóa thông báo liên quan (Bảng phụ)
    await client.query("DELETE FROM user_notifications WHERE user_id = $1", [target_id]);

    // 3. Xóa thông tin tài chính cá nhân
    await client.query("DELETE FROM user_finances WHERE user_id = $1", [target_id]);

    // 4. Xóa vai trò (User Role)
    await client.query("DELETE FROM userrole WHERE user_id = $1", [target_id]);

    // 5. Xóa thông tin hồ sơ (User Item)
    await client.query("DELETE FROM user_item WHERE user_id = $1", [target_id]);

    // 6. Cuối cùng: Xóa tài khoản chính (Users)
    const deleteRes = await client.query("DELETE FROM users WHERE user_id = $1", [target_id]);

    if (deleteRes.rowCount === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Không tìm thấy cư dân để xóa." });
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Đã xóa cư dân thành công." });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Delete User Error:", err);
    // Nếu lỗi do ràng buộc khóa ngoại (Foreign Key) chưa xử lý hết
    if (err.code === '23503') {
        res.status(400).json({ error: "Không thể xóa vì cư dân này còn dữ liệu liên kết (Báo cáo, Hóa đơn...)." });
    } else {
        res.status(500).json({ error: "Lỗi server khi xóa cư dân." });
    }
  } finally {
    client.release();
  }
});

// ==================================================================
// 👻 API: Ẩn/Hiện cư dân
// ==================================================================
router.put("/status/:userId", async (req, res) => {
  const { userId } = req.params;
  const { is_living } = req.body;

  if (is_living === undefined) {
      return res.status(400).json({ error: "Thiếu trạng thái is_living" });
  }

  try {
    await query(
      `UPDATE user_item SET is_living = $1 WHERE user_id = $2`,
      [is_living, userId]
    );

    const msg = is_living ? "Đã kích hoạt lại cư dân." : "Đã ẩn cư dân (Đánh dấu rời đi).";
    res.json({ success: true, message: msg });

  } catch (err) {
    console.error("❌ Error changing resident status:", err);
    res.status(500).json({ error: "Lỗi server khi cập nhật trạng thái." });
  }
});

export default router;