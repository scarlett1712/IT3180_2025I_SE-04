import express from "express";
import { pool } from "../db.js";
// 🔥 Đảm bảo đường dẫn import middleware chính xác
import { verifySession } from "../middleware/authMiddleware.js";

const router = express.Router();
const query = (text, params) => pool.query(text, params);

/**
 * 🛠️ Helper: Chuẩn hóa ngày tháng sang định dạng YYYY-MM-DD cho Database
 * Input: "17-12-2005", "17/12/2005"
 * Output: "2005-12-17"
 */
const formatDateForDB = (dateStr) => {
    if (!dateStr || dateStr.trim() === "") return null; // Trả về null nếu chuỗi rỗng

    // Nếu đã đúng chuẩn YYYY-MM-DD thì giữ nguyên
    if (dateStr.match(/^\d{4}-\d{2}-\d{2}$/)) return dateStr;

    // Xử lý tách chuỗi (chấp nhận cả - và /)
    const parts = dateStr.split(/[-/]/);
    if (parts.length === 3) {
        // Giả định định dạng đầu vào là DD-MM-YYYY
        // parts[0]=Ngày, parts[1]=Tháng, parts[2]=Năm
        return `${parts[2]}-${parts[1]}-${parts[0]}`;
    }

    return null; // Trả về null nếu format lạ để COALESCE giữ lại giá trị cũ trong DB
};

// ==================================================================
// 📋 API: Lấy danh sách toàn bộ cư dân (Chi tiết)
// ==================================================================
router.get("/", verifySession, async (req, res) => { // 🔥 Thêm verifySession cho an toàn
  try {
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
// ✏️ API: Cập nhật thông tin cư dân (ĐÃ FIX LỖI DATE OUT OF RANGE)
// ==================================================================
router.put("/update/:userId", verifySession, async (req, res) => {
  const { userId } = req.params;
  const { full_name, gender, dob, email, phone, identity_card, home_town } = req.body;

  if (!userId) return res.status(400).json({ error: "Thiếu User ID" });

  // 🔥 1. CHUẨN HÓA NGÀY SINH
  // Biến này sẽ là "YYYY-MM-DD" hoặc null
  const formattedDob = formatDateForDB(dob);

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 🔥 2. Cập nhật bảng user_item
    // Sử dụng formattedDob vào vị trí $3
    await client.query(
      `UPDATE user_item
       SET full_name = COALESCE($1, full_name),
           gender = COALESCE($2, gender),
           dob = COALESCE($3, dob),          -- Nếu formattedDob là null, giữ nguyên giá trị cũ
           email = COALESCE($4, email),
           identity_card = COALESCE($5, identity_card),
           home_town = COALESCE($6, home_town)
       WHERE user_id = $7`,
      [full_name, gender, formattedDob, email, identity_card, home_town, userId]
    );

    // 3. Cập nhật số điện thoại trong bảng users (nếu có)
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
  const currentUserId = req.currentUser.id; // Lấy từ token (đảm bảo middleware đã chạy)

  if (req.currentUser.role !== 'ADMIN') {
      return res.status(403).json({ error: "Bạn không có quyền xóa cư dân." });
  }

  if (parseInt(target_id) === parseInt(currentUserId)) {
      return res.status(400).json({ error: "Không thể tự xóa tài khoản của chính mình." });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // Xóa dữ liệu liên quan
    await client.query("DELETE FROM login_requests WHERE user_id = $1", [target_id]);
    await client.query("DELETE FROM user_notifications WHERE user_id = $1", [target_id]);
    await client.query("DELETE FROM user_finances WHERE user_id = $1", [target_id]);
    await client.query("DELETE FROM userrole WHERE user_id = $1", [target_id]);
    await client.query("DELETE FROM user_item WHERE user_id = $1", [target_id]);

    // Xóa tài khoản chính
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
    if (err.code === '23503') {
        res.status(400).json({ error: "Không thể xóa vì cư dân này còn dữ liệu liên kết." });
    } else {
        res.status(500).json({ error: "Lỗi server khi xóa cư dân." });
    }
  } finally {
    client.release();
  }
});

// ==================================================================
// 👻 API: Ẩn/Hiện cư dân (Soft Delete)
// ==================================================================
router.put("/status/:userId", verifySession, async (req, res) => {
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

    const msg = is_living ? "Đã kích hoạt lại cư dân." : "Đã ẩn cư dân.";
    res.json({ success: true, message: msg });

  } catch (err) {
    console.error("❌ Error changing resident status:", err);
    res.status(500).json({ error: "Lỗi server khi cập nhật trạng thái." });
  }
});

export default router;