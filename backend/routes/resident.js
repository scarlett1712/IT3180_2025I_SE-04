import express from "express";
import { pool } from "../db.js";

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

    // 1. Cập nhật bảng user_item (Thông tin chi tiết)
    // Sử dụng COALESCE để giữ nguyên giá trị cũ nếu không gửi dữ liệu mới
    await client.query(
      `UPDATE user_item
       SET full_name = COALESCE($1, full_name),
           gender = COALESCE($2, gender),
           dob = COALESCE($3, dob),
           email = COALESCE($4, email),
           identity_card = COALESCE($5, identity_card),
           home_town = COALESCE($6, home_town)
       WHERE user_id = $7`,
      [full_name, gender, dob, email, identity_card, home_town, userId]
    );

    // 2. Cập nhật số điện thoại trong bảng users (nếu có thay đổi)
    if (phone) {
      // Kiểm tra trùng số điện thoại trước
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

// ==================================================================
// 🗑️ API: Xóa cư dân (Xóa vĩnh viễn)
// ==================================================================
router.delete("/delete/:userId", async (req, res) => {
  const { userId } = req.params;

  try {
    // 1. Kiểm tra an toàn: Không cho phép xóa tài khoản Admin
    const roleCheck = await query(
        "SELECT r.role_name FROM userrole ur JOIN role r ON ur.role_id = r.role_id WHERE ur.user_id = $1",
        [userId]
    );

    if (roleCheck.rows.length > 0 && roleCheck.rows[0].role_name === 'ADMIN') {
        return res.status(403).json({ error: "Không thể xóa tài khoản Quản trị viên." });
    }

    // 2. Thực hiện xóa từ bảng users
    // (Do ràng buộc Khóa ngoại ON DELETE CASCADE, nó sẽ tự động xóa trong user_item, login_requests, v.v.)
    const result = await query("DELETE FROM users WHERE user_id = $1 RETURNING user_id", [userId]);

    if (result.rowCount === 0) {
        return res.status(404).json({ error: "Không tìm thấy cư dân này." });
    }

    res.json({ success: true, message: "Đã xóa cư dân vĩnh viễn." });

  } catch (err) {
    console.error("❌ Error deleting resident:", err);
    res.status(500).json({ error: "Lỗi server khi xóa cư dân." });
  }
});

// ==================================================================
// 👻 API: Ẩn/Hiện cư dân (Cập nhật trạng thái Đang ở / Đã đi)
// ==================================================================
router.put("/status/:userId", async (req, res) => {
  const { userId } = req.params;
  const { is_living } = req.body; // true = Đang ở, false = Đã rời đi (Ẩn)

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