import express from "express";
import { pool } from "../db.js";
import { verifySession } from "../middleware/authMiddleware.js";

const router = express.Router();
const query = (text, params) => pool.query(text, params);

/**
 * 🛠️ Helper: Chuẩn hóa ngày tháng sang định dạng YYYY-MM-DD cho Database
 * Input chấp nhận: "17-12-2005", "17/12/2005", "2005-12-17"
 */
const formatDateForDB = (dateStr) => {
    if (!dateStr || typeof dateStr !== 'string' || dateStr.trim() === "") return null;

    // 1. Nếu đã đúng chuẩn YYYY-MM-DD -> Giữ nguyên
    if (dateStr.match(/^\d{4}-\d{2}-\d{2}$/)) return dateStr;

    // 2. Xử lý tách chuỗi (DD-MM-YYYY hoặc DD/MM/YYYY)
    const parts = dateStr.split(/[-/]/);
    if (parts.length === 3) {
        // Kiểm tra sơ bộ tính hợp lệ (Năm phải có 4 chữ số)
        if (parts[2].length === 4) {
            // Format: DD-MM-YYYY -> YYYY-MM-DD
            return `${parts[2]}-${parts[1]}-${parts[0]}`;
        }
        // Trường hợp khác (ví dụ YYYY/MM/DD mà lọt vào đây) -> Cần log để debug nếu cần
    }

    return null; // Trả về null để SQL giữ nguyên giá trị cũ (COALESCE)
};

// ==================================================================
// 📋 API: Lấy danh sách toàn bộ cư dân (Chi tiết)
// ==================================================================
router.get("/", verifySession, async (req, res) => {
  try {
    // 🛡️ Bảo mật: Chỉ Admin hoặc Ban quản lý (Role 2, 3, 4) mới xem được full list
    // Nếu app của bạn cho phép cư dân xem danh sách hàng xóm thì bỏ check này
    // if (![2, 3, 4].includes(req.user.role)) { // Giả sử req.user được gán từ middleware
    //    return res.status(403).json({ error: "Không có quyền truy cập danh sách này." });
    // }

    const queryStr = `
      SELECT
        ui.user_id AS user_id,
        ui.full_name,
        ui.email,
        u.phone,
        TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob,
        ui.gender,
        ui.identity_card,
        ui.home_town,
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
      WHERE ur.role_id = 1
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
// ✏️ API: Cập nhật thông tin cư dân
// ==================================================================
router.put("/update/:userId", verifySession, async (req, res) => {
  const { userId } = req.params;
  const { full_name, gender, dob, email, phone, identity_card, home_town } = req.body;

  // 🔥 Lấy thông tin người đang thực hiện request (từ token)
  // Middleware của bạn có thể gán vào req.user hoặc req.currentUser. Hãy kiểm tra!
  const requester = req.user || req.currentUser;

  if (!userId) return res.status(400).json({ error: "Thiếu User ID" });

  // 🛡️ Bảo mật: Chỉ Admin HOẶC Chính chủ mới được sửa
  // Giả sử Role ID 2 là Admin. Bạn cần sửa lại theo logic role của mình.
  const isAdmin = requester.role === 2 || requester.role === 'ADMIN';
  const isOwner = parseInt(requester.id) === parseInt(userId);

  if (!isAdmin && !isOwner) {
      return res.status(403).json({ error: "Bạn không có quyền sửa thông tin người khác." });
  }

  const formattedDob = formatDateForDB(dob);

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Cập nhật bảng user_item
    await client.query(
      `UPDATE user_item
       SET full_name = COALESCE($1, full_name),
           gender = COALESCE($2, gender),
           dob = COALESCE($3, dob),
           email = COALESCE($4, email),
           identity_card = COALESCE($5, identity_card),
           home_town = COALESCE($6, home_town)
       WHERE user_id = $7`,
      [full_name, gender, formattedDob, email, identity_card, home_town, userId]
    );

    // 2. Cập nhật số điện thoại (Chỉ Admin hoặc chính chủ được đổi SĐT login)
    if (phone) {
      // Check trùng SĐT
      const checkPhone = await client.query(
          "SELECT user_id FROM users WHERE phone = $1 AND user_id != $2",
          [phone, userId]
      );
      if (checkPhone.rows.length > 0) {
          throw new Error("Số điện thoại này đã được sử dụng.");
      }

      await client.query(
        `UPDATE users SET phone = $1 WHERE user_id = $2`,
        [phone, userId]
      );
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Cập nhật thành công." });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("❌ Error updating resident:", err);
    res.status(500).json({ error: err.message });
  } finally {
    client.release();
  }
});

// ==================================================================
// 🗑️ API: XÓA CƯ DÂN (Chỉ Admin)
// ==================================================================
router.delete("/delete/:target_id", verifySession, async (req, res) => {
  const { target_id } = req.params;
  const requester = req.user || req.currentUser; // 🔥 Check lại biến này

  // 🛡️ Check quyền Admin (Role ID = 2 hoặc string 'ADMIN')
  const isAdmin = requester.role === 2 || requester.role === 'ADMIN';

  if (!isAdmin) {
      return res.status(403).json({ error: "Chỉ Admin mới có quyền xóa cư dân." });
  }

  if (parseInt(target_id) === parseInt(requester.id || requester.user_id)) {
      return res.status(400).json({ error: "Không thể tự xóa chính mình." });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Xóa các bảng phụ thuộc trước
    await client.query("DELETE FROM login_requests WHERE user_id = $1", [target_id]);
    await client.query("DELETE FROM user_notifications WHERE user_id = $1", [target_id]);
    await client.query("DELETE FROM user_finances WHERE user_id = $1", [target_id]);
    await client.query("DELETE FROM invoice WHERE user_id = $1", [target_id]); // 🔥 Thêm xóa hóa đơn nếu có
    await client.query("DELETE FROM userrole WHERE user_id = $1", [target_id]);

    // 🔥 2. Xử lý bảng user_item và relationship
    // Lấy relationship_id trước khi xóa user_item
    const relRes = await client.query("SELECT relationship FROM user_item WHERE user_id = $1", [target_id]);
    const relationshipId = relRes.rows.length > 0 ? relRes.rows[0].relationship : null;

    // Xóa user_item
    await client.query("DELETE FROM user_item WHERE user_id = $1", [target_id]);

    // Nếu có relationship, xóa luôn bản ghi trong bảng relationship (để tránh rác)
    // Lưu ý: Nếu logic của bạn là 1 relationship dùng chung cho cả hộ thì ĐỪNG xóa dòng này
    // Nhưng thường relationship table map 1-1 với user trong căn hộ, nên xóa là đúng.
    if (relationshipId) {
       await client.query("DELETE FROM relationship WHERE relationship_id = $1", [relationshipId]);
    }

    // 3. Cuối cùng xóa users
    const deleteRes = await client.query("DELETE FROM users WHERE user_id = $1", [target_id]);

    if (deleteRes.rowCount === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Người dùng không tồn tại." });
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Đã xóa cư dân thành công." });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Delete User Error:", err);
    res.status(500).json({ error: "Lỗi server khi xóa cư dân." });
  } finally {
    client.release();
  }
});

// ==================================================================
// 👻 API: Ẩn/Hiện cư dân (Soft Delete) - Chỉ Admin
// ==================================================================
router.put("/status/:userId", verifySession, async (req, res) => {
  const { userId } = req.params;
  const { is_living } = req.body;
  const requester = req.user || req.currentUser;

  // 🛡️ Check quyền Admin
  const isAdmin = requester.role === 2 || requester.role === 'ADMIN';
  if (!isAdmin) return res.status(403).json({ error: "Bạn không có quyền này." });

  if (is_living === undefined) return res.status(400).json({ error: "Thiếu params" });

  try {
    await query(
      `UPDATE user_item SET is_living = $1 WHERE user_id = $2`,
      [is_living, userId]
    );

    const msg = is_living ? "Đã kích hoạt lại." : "Đã ẩn cư dân.";
    res.json({ success: true, message: msg });

  } catch (err) {
    console.error("❌ Error changing status:", err);
    res.status(500).json({ error: "Lỗi server." });
  }
});

export default router;