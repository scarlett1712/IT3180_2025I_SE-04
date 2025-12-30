import express from "express";
import { pool } from "../db.js";
import { verifySession } from "../middleware/authMiddleware.js";

const router = express.Router();
const query = (text, params) => pool.query(text, params);

/**
 * 🛠️ Helper: Chuẩn hóa ngày tháng sang định dạng YYYY-MM-DD cho Database
 */
const formatDateForDB = (dateStr) => {
    if (!dateStr || typeof dateStr !== 'string' || dateStr.trim() === "") return null;
    if (dateStr.match(/^\d{4}-\d{2}-\d{2}$/)) return dateStr;
    const parts = dateStr.split(/[-/]/);
    if (parts.length === 3) {
        if (parts[2].length === 4) {
            return `${parts[2]}-${parts[1]}-${parts[0]}`;
        }
    }
    return null;
};

// ==================================================================
// 🧹 API: CHUẨN HÓA SỐ ĐIỆN THOẠI (0xxxx -> +84xxxx)
// ==================================================================
router.get("/fix-phone-format", async (req, res) => {
    const client = await pool.connect();
    try {
        await client.query("BEGIN");
        const badUsers = await client.query("SELECT user_id, phone FROM users WHERE phone LIKE '0%'");
        console.log(`🔍 Tìm thấy ${badUsers.rowCount} số điện thoại lỗi format...`);

        let updatedCount = 0;
        let deletedCount = 0;

        for (const user of badUsers.rows) {
            const oldPhone = user.phone;
            const newPhone = "+84" + oldPhone.substring(1);
            try {
                await client.query(
                    "UPDATE users SET phone = $1 WHERE user_id = $2",
                    [newPhone, user.user_id]
                );
                updatedCount++;
            } catch (err) {
                if (err.code === '23505') {
                    console.log(`⚠️ Trùng số ${newPhone}. Đang xóa bản ghi rác (ID: ${user.user_id})...`);
                    await client.query("DELETE FROM userrole WHERE user_id = $1", [user.user_id]);
                    await client.query("DELETE FROM user_item WHERE user_id = $1", [user.user_id]);
                    await client.query("DELETE FROM users WHERE user_id = $1", [user.user_id]);
                    deletedCount++;
                } else {
                    throw err;
                }
            }
        }
        await client.query("COMMIT");
        res.send(`<h1>✅ Đã dọn dẹp xong!</h1><ul><li>Updated: ${updatedCount}</li><li>Deleted: ${deletedCount}</li></ul>`);
    } catch (err) {
        await client.query("ROLLBACK");
        console.error(err);
        res.status(500).send("Lỗi: " + err.message);
    } finally {
        client.release();
    }
});

// ==================================================================
// 📋 API: Lấy danh sách toàn bộ cư dân (Chi tiết)
// ==================================================================
router.get("/", verifySession, async (req, res) => {
  try {
    const queryStr = `
      SELECT DISTINCT ON (ui.user_id)
        ui.user_item_id, ui.user_id AS user_id, ui.full_name, ui.email, u.phone,
        TO_CHAR(ui.dob, 'DD-MM-YYYY') AS dob, ui.gender, ui.job, ui.identity_card, ui.home_town, ui.family_id,
        COALESCE((SELECT role_id FROM userrole WHERE user_id = ui.user_id AND role_id = 1 LIMIT 1),
                 (SELECT role_id FROM userrole WHERE user_id = ui.user_id LIMIT 1)) AS role_id,
        r.relationship_id, r.apartment_id, a.apartment_number, a.floor, a.area,
        r.relationship_with_the_head_of_household, ui.is_living, ui.avatar_path,
        EXISTS(SELECT 1 FROM userrole WHERE user_id = ui.user_id AND role_id IN (2, 3, 4)) AS is_staff
      FROM user_item ui
      LEFT JOIN users u ON ui.user_id = u.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE EXISTS (SELECT 1 FROM userrole ur WHERE ur.user_id = ui.user_id AND ur.role_id = 1)
      ORDER BY ui.user_id,
               CASE WHEN r.is_head_of_household = TRUE THEN 0 ELSE 1 END,
               CASE WHEN a.apartment_number IS NULL THEN 1 ELSE 0 END,
               -- 🔥 Fix lỗi sort integer ~ unknown bằng cách ép kiểu ::TEXT
               CASE
                 WHEN a.apartment_number::TEXT ~ '^[0-9]+$' THEN a.apartment_number::TEXT::INTEGER
                 ELSE COALESCE((regexp_replace(a.apartment_number::TEXT, '\\D', '', 'g'))::INTEGER, 0)
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
  const requester = req.user || req.currentUser;

  if (!userId) return res.status(400).json({ error: "Thiếu User ID" });

  const isAdmin = requester.role === 2 || requester.role === 'ADMIN' || requester.role_id === 2;
  const isOwner = parseInt(requester.id || requester.user_id) === parseInt(userId);

  if (!isAdmin && !isOwner) return res.status(403).json({ error: "Không có quyền." });

  const formattedDob = formatDateForDB(dob);
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query(
      `UPDATE user_item SET full_name = COALESCE($1::text, full_name), gender = COALESCE($2::text, gender),
           dob = COALESCE($3::date, dob), job = COALESCE($4::text, job), email = COALESCE($5::text, email),
           identity_card = COALESCE($6::text, identity_card), home_town = COALESCE($7::text, home_town)
       WHERE user_id = $8`,
      [full_name, gender, formattedDob, job, email, identity_card, home_town, userId]
    );

    if (phone) {
      const checkPhone = await client.query("SELECT user_id FROM users WHERE phone = $1 AND user_id != $2", [phone, userId]);
      if (checkPhone.rows.length > 0) throw new Error("SĐT đã được sử dụng.");
      await client.query(`UPDATE users SET phone = $1 WHERE user_id = $2`, [phone, userId]);
    }
    await client.query("COMMIT");
    res.json({ success: true, message: "Cập nhật thành công." });
  } catch (err) {
    await client.query("ROLLBACK");
    res.status(500).json({ error: err.message });
  } finally { client.release(); }
});

// ==================================================================
// 🗑️ API: XÓA CƯ DÂN (Chỉ Admin)
// ==================================================================
router.delete("/delete/:target_id", verifySession, async (req, res) => {
  const { target_id } = req.params;
  const requester = req.user || req.currentUser;
  const isAdmin = requester.role === 2 || requester.role === 'ADMIN' || requester.role_id === 2;
  if (!isAdmin) return res.status(403).json({ error: "Chỉ Admin mới có quyền xóa." });
  if (parseInt(target_id) === parseInt(requester.id || requester.user_id)) return res.status(400).json({ error: "Không thể tự xóa chính mình." });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query("DELETE FROM login_requests WHERE user_id = $1", [target_id]);
    await client.query("DELETE FROM user_notifications WHERE user_id = $1", [target_id]);
    await client.query("DELETE FROM user_finances WHERE user_id = $1", [target_id]);
    await client.query("DELETE FROM userrole WHERE user_id = $1", [target_id]);

    const relRes = await client.query("SELECT relationship FROM user_item WHERE user_id = $1", [target_id]);
    const relationshipId = relRes.rows.length > 0 ? relRes.rows[0].relationship : null;

    await client.query("DELETE FROM user_item WHERE user_id = $1", [target_id]);
    if (relationshipId) await client.query("DELETE FROM relationship WHERE relationship_id = $1", [relationshipId]);

    const deleteRes = await client.query("DELETE FROM users WHERE user_id = $1", [target_id]);
    if (deleteRes.rowCount === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Người dùng không tồn tại." });
    }
    await client.query("COMMIT");
    res.json({ success: true, message: "Đã xóa cư dân thành công." });
  } catch (err) {
    await client.query("ROLLBACK");
    res.status(500).json({ error: "Lỗi server khi xóa cư dân." });
  } finally { client.release(); }
});

// ==================================================================
// 👻 API: Ẩn/Hiện cư dân
// ==================================================================
router.put("/status/:userId", verifySession, async (req, res) => {
  const { userId } = req.params;
  const { is_living } = req.body;
  const requester = req.user || req.currentUser;
  const isAdmin = requester.role === 2 || requester.role === 'ADMIN' || requester.role_id === 2;
  if (!isAdmin) return res.status(403).json({ error: "Không có quyền." });
  if (is_living === undefined) return res.status(400).json({ error: "Thiếu params" });

  try {
    await query(`UPDATE user_item SET is_living = $1 WHERE user_id = $2`, [is_living, userId]);
    res.json({ success: true, message: is_living ? "Đã kích hoạt lại." : "Đã ẩn cư dân." });
  } catch (err) { res.status(500).json({ error: "Lỗi server." }); }
});

// ==================================================================
// 🏠 API: CẬP NHẬT PHÒNG
// ==================================================================
router.put("/assign-apartment", verifySession, async (req, res) => {
    const { user_id, apartment_id, relationship, is_head } = req.body;
    if (!user_id) return res.status(400).json({ error: "Thiếu user_id" });

    const client = await pool.connect();
    try {
        await client.query("BEGIN");
        const userRes = await client.query("SELECT relationship FROM user_item WHERE user_id = $1", [user_id]);
        if (userRes.rows.length === 0) { await client.query("ROLLBACK"); return res.status(404).json({ error: "Cư dân không tồn tại" }); }
        const currentUserRelationshipId = userRes.rows[0].relationship;

        // Check trùng chủ hộ
        if (apartment_id && is_head === true) {
            const checkHead = await client.query(
                `SELECT relationship_id FROM relationship WHERE apartment_id = $1 AND is_head_of_household = TRUE`,
                [apartment_id]
            );
            if (checkHead.rows.length > 0) {
                const existingHeadId = checkHead.rows[0].relationship_id;
                if (!currentUserRelationshipId || existingHeadId !== currentUserRelationshipId) {
                    await client.query("ROLLBACK");
                    return res.status(400).json({ error: "LỖI: Phòng này đã có Chủ hộ rồi!" });
                }
            }
        }

        if (!apartment_id) {
            await client.query("UPDATE user_item SET relationship = NULL WHERE user_id = $1", [user_id]);
            if (currentUserRelationshipId) await client.query("DELETE FROM relationship WHERE relationship_id = $1", [currentUserRelationshipId]);
        } else {
            const finalRelationship = is_head ? 'Bản thân' : (relationship || "Thành viên");
            const insertRel = await client.query(
                `INSERT INTO relationship (apartment_id, relationship_with_the_head_of_household, is_head_of_household)
                VALUES ($1, $2, $3) RETURNING relationship_id`,
                [apartment_id, finalRelationship, is_head || false]
            );
            const newRelationshipId = insertRel.rows[0].relationship_id;

            await client.query("UPDATE user_item SET relationship = $1 WHERE user_id = $2", [newRelationshipId, user_id]);
            if (currentUserRelationshipId && currentUserRelationshipId !== newRelationshipId) {
                await client.query("DELETE FROM relationship WHERE relationship_id = $1", [currentUserRelationshipId]);
            }
            await client.query("UPDATE apartment SET status = 'Occupied' WHERE apartment_id = $1", [apartment_id]);
        }
        await client.query("COMMIT");
        res.json({ success: true, message: "Cập nhật thành công" });
    } catch (err) {
        await client.query("ROLLBACK");
        if (err.code === '23505') return res.status(400).json({ error: "Dữ liệu bị xung đột." });
        res.status(500).json({ error: "Lỗi Server: " + err.message });
    } finally { client.release(); }
});

// ==================================================================
// 🏢 API: LẤY DANH SÁCH PHÒNG (CHO SPINNER)
// ==================================================================
router.get("/list-for-selection", async (req, res) => {
  try {
    // 🔥 ĐÃ SỬA: Chỉ gọi bảng apartment, không gọi ui, r, a vì không có JOIN
    const result = await pool.query(`
      SELECT apartment_number, floor
      FROM apartment
      ORDER BY
        -- 1. Sắp xếp theo Tầng (Số tăng dần)
        CASE
            WHEN floor::TEXT ~ '^[0-9]+$' THEN floor::TEXT::INTEGER
            ELSE 0
        END ASC,

        -- 2. Sắp xếp theo Phòng (Số tăng dần)
        CASE
            WHEN apartment_number::TEXT ~ '^[0-9]+$' THEN apartment_number::TEXT::INTEGER
            ELSE 0
        END ASC
    `);

    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Lỗi lấy danh sách phòng" });
  }
});

export default router;