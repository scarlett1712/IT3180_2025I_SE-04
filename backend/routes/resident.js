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
    }

    return null; // Trả về null để SQL giữ nguyên giá trị cũ (COALESCE)
};

// ==================================================================
// 📋 API: Lấy danh sách toàn bộ cư dân (Chi tiết)
// ==================================================================
router.get("/", verifySession, async (req, res) => {
  try {
    const queryStr = `
      SELECT DISTINCT ON (ui.user_id)
        ui.user_item_id,
        ui.user_id AS user_id,
        ui.full_name,
        ui.email,
        u.phone,
        TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob,
        ui.gender,
        ui.job,
        ui.identity_card,
        ui.home_town,
        ui.family_id,
        COALESCE(
          (SELECT role_id FROM userrole WHERE user_id = ui.user_id AND role_id = 1 LIMIT 1),
          (SELECT role_id FROM userrole WHERE user_id = ui.user_id LIMIT 1)
        ) AS role_id,
        r.relationship_id,
        r.apartment_id,
        a.apartment_number, -- Giá trị này sẽ là NULL nếu vô gia cư
        a.floor,
        a.area,
        r.relationship_with_the_head_of_household,
        ui.is_living,
        ui.avatar_path,
        EXISTS(
          SELECT 1 FROM userrole
          WHERE user_id = ui.user_id
          AND role_id IN (2, 3, 4)
        ) AS is_staff
      FROM user_item ui
      LEFT JOIN users u ON ui.user_id = u.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE EXISTS (
        SELECT 1 FROM userrole ur
        WHERE ur.user_id = ui.user_id
        AND ur.role_id = 1
      )

      -- Đã comment lại để hiển thị cả người vô gia cư (NULL apartment)
      -- AND a.apartment_number IS NOT NULL
      -- AND a.apartment_number != ''
      -- AND a.apartment_number != 'null'

      ORDER BY ui.user_id,
               CASE WHEN r.is_head_of_household = TRUE THEN 0 ELSE 1 END,
               -- Sắp xếp: Ai có phòng lên trước, Vô gia cư xuống dưới
               CASE WHEN a.apartment_number IS NULL THEN 1 ELSE 0 END,
               CASE
                 WHEN a.apartment_number ~ '^\\d+$' THEN a.apartment_number::INTEGER
                 ELSE COALESCE((regexp_replace(a.apartment_number, '\\D', '', 'g'))::INTEGER, 0)
               END ASC;
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
  const { full_name, gender, dob, job, email, phone, identity_card, home_town } = req.body;

  // 🔥 Lấy thông tin người đang thực hiện request (từ token)
  const requester = req.user || req.currentUser;

  if (!userId) return res.status(400).json({ error: "Thiếu User ID" });

  // 🛡️ Bảo mật: Chỉ Admin HOẶC Chính chủ mới được sửa
  const isAdmin = requester.role === 2 || requester.role === 'ADMIN' || requester.role_id === 2;
  const isOwner = parseInt(requester.id || requester.user_id) === parseInt(userId);

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
       SET full_name = COALESCE($1::text, full_name),
           gender = COALESCE($2::text, gender),
           dob = COALESCE($3::date, dob),
           job = COALESCE($4::text, job),
           email = COALESCE($5::text, email),
           identity_card = COALESCE($6::text, identity_card),
           home_town = COALESCE($7::text, home_town)
       WHERE user_id = $8`,
      [full_name, gender, formattedDob, job, email, identity_card, home_town, userId]
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
  const requester = req.user || req.currentUser;

  // 🛡️ Check quyền Admin
  const isAdmin = requester.role === 2 || requester.role === 'ADMIN' || requester.role_id === 2;

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
    await client.query("DELETE FROM userrole WHERE user_id = $1", [target_id]);

    // 2. Xử lý bảng user_item và relationship
    const relRes = await client.query("SELECT relationship FROM user_item WHERE user_id = $1", [target_id]);
    const relationshipId = relRes.rows.length > 0 ? relRes.rows[0].relationship : null;

    await client.query("DELETE FROM user_item WHERE user_id = $1", [target_id]);

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

  const isAdmin = requester.role === 2 || requester.role === 'ADMIN' || requester.role_id === 2;
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

// ==================================================================
// 🏠 API: CẬP NHẬT PHÒNG CHO CƯ DÂN & QUAN HỆ VỚI CHỦ HỘ
// ==================================================================
router.put("/assign-apartment", verifySession, async (req, res) => {
    // Nhận thêm: relationship (VD: Con cái) và is_head (true/false)
    const { user_id, apartment_id, relationship, is_head } = req.body;

    if (!user_id) return res.status(400).json({ error: "Thiếu user_id" });

    const client = await pool.connect();
    try {
        await client.query("BEGIN");

        // 1. Tìm relationship_id
        const relRes = await client.query("SELECT relationship FROM user_item WHERE user_id = $1", [user_id]);

        if (relRes.rows.length === 0) {
             await client.query("ROLLBACK");
             return res.status(404).json({ error: "User chưa có relationship id" });
        }

        const relationshipId = relRes.rows[0].relationship;

        // 2. Logic xử lý
        // Nếu apartment_id là NULL (Đuổi ra) -> Reset các trường quan hệ
        if (!apartment_id) {
            await client.query(
                `UPDATE relationship
                 SET apartment_id = NULL,
                     relationship_with_the_head_of_household = NULL,
                     is_head_of_household = FALSE
                 WHERE relationship_id = $1`,
                [relationshipId]
            );
        } else {
            // Nếu Thêm vào phòng -> Cập nhật đầy đủ
            await client.query(
                `UPDATE relationship
                 SET apartment_id = $1,
                     relationship_with_the_head_of_household = COALESCE($2, relationship_with_the_head_of_household),
                     is_head_of_household = COALESCE($3, FALSE)
                 WHERE relationship_id = $4`,
                [apartment_id, relationship, is_head, relationshipId]
            );
        }

        await client.query("COMMIT");
        res.json({ success: true, message: "Cập nhật thành công" });

    } catch (err) {
        await client.query("ROLLBACK");
        console.error("Assign Apartment Error:", err);
        res.status(500).json({ error: err.message });
    } finally {
        client.release();
    }
});

export default router;