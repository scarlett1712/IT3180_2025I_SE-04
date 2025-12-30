import express from "express";
import { pool } from "../db.js";
import admin from "firebase-admin";
import ExcelJS from 'exceljs';
import { sendNotification } from "../utils/firebaseHelper.js";

const router = express.Router();
const query = (text, params) => pool.query(text, params);

// ... (Phần khởi tạo bảng và GET Admin giữ nguyên) ...

// ==================================================================
// 🔵 [PUT] ADMIN CẬP NHẬT TRẠNG THÁI (ĐỒNG BỘ CẢ PHÒNG)
// ==================================================================
router.put("/update-status", async (req, res) => {
  // 🔥 Admin gửi lên user_id của người được tick chọn
  const { user_id, finance_id, status } = req.body;

  if (!finance_id || !status) return res.status(400).json({ error: "Thiếu thông tin" });

  const targetId = user_id || req.body.room;

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Tìm danh sách User ID cần update
    // Logic: Tìm xem user_id này ở phòng nào -> Lấy tất cả user trong phòng đó
    const targetsRes = await client.query(`
        SELECT ui_member.user_id, uf.id as user_finance_id
        FROM user_item ui_target
        -- Join để tìm phòng của target
        JOIN relationship r_target ON ui_target.relationship = r_target.relationship_id
        -- Join ngược lại để tìm tất cả thành viên trong phòng đó
        JOIN relationship r_member ON r_target.apartment_id = r_member.apartment_id
        JOIN user_item ui_member ON r_member.relationship_id = ui_member.relationship
        -- Join bảng tài chính để lấy ID dòng nợ
        JOIN user_finances uf ON ui_member.user_id = uf.user_id
        WHERE ui_target.user_id = $1  -- Input là 1 user_id bất kỳ trong phòng
        AND uf.finance_id = $2        -- Khoản thu tương ứng
    `, [targetId, finance_id]);

    // Nếu không tìm thấy (VD: User vô gia cư hoặc không có khoản thu này), thì báo lỗi
    if (targetsRes.rows.length === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Lỗi cập nhật phòng. Không tìm thấy người dùng hoặc khoản thu tương ứng trong phòng này." });
    }

    const idsToUpdate = targetsRes.rows.map(r => r.user_finance_id);

    // 2. Thực hiện Update đồng loạt
    await client.query(
        `UPDATE user_finances SET status = $1 WHERE id = ANY($2::int[])`,
        [status, idsToUpdate]
    );

    // 3. Xử lý Invoice (Hóa đơn)
    if (status === 'da_thanh_toan') {
        const representativeId = idsToUpdate[0]; 
        const ordercode = `ADMIN-${Date.now()}-${targetId}`;

        const existing = await client.query(
            "SELECT invoice_id FROM invoice WHERE finance_id = ANY($1::int[])",
            [idsToUpdate]
        );

        if (existing.rows.length === 0) {
            const amountRes = await client.query(
                `SELECT COALESCE(uf.amount, f.amount) as real_amount, f.title
                 FROM user_finances uf JOIN finances f ON uf.finance_id = f.id
                 WHERE uf.id = $1`, [representativeId]
            );

            // 🔥 KIỂM TRA KẾT QUẢ TRƯỚC KHI TRUY CẬP
            if (amountRes.rows.length > 0) {
                const { real_amount, title } = amountRes.rows[0];
                await client.query(`
                  INSERT INTO invoice (finance_id, amount, description, ordercode, currency, paytime)
                  VALUES ($1, $2, $3, $4, 'VND', NOW())
                `, [representativeId, real_amount, title, ordercode]);
            } else {
                // Ghi log lỗi nếu không tìm thấy thông tin khoản thu, nhưng không làm sập server
                console.error(`[Admin Update] Không tìm thấy chi tiết khoản thu cho user_finance_id: ${representativeId}`);
            }
        }
    } else {
        await client.query(
            "DELETE FROM invoice WHERE finance_id = ANY($1::int[])",
            [idsToUpdate]
        );
    }

    await client.query("COMMIT");
    res.json({ success: true, updated_count: idsToUpdate.length });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Admin Update Error:", err);
    res.status(500).json({ error: err.message });
  } finally { client.release(); }
});

// ... (Các phần còn lại của file giữ nguyên) ...

// ==================================================================
// 🔵 [PUT] USER TỰ THANH TOÁN (ĐỒNG BỘ CẢ PHÒNG)
// ==================================================================
router.put("/user/update-status", async (req, res) => {
  const { user_id, finance_id, status } = req.body;
  if (!user_id || !finance_id || !status) return res.status(400).json({ error: "Thiếu thông tin" });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Tìm tất cả user_finance_id của CẢ GIA ĐÌNH
    const familyRes = await client.query(`
        SELECT uf.id, uf.user_id, COALESCE(uf.amount, f.amount) as real_amount, f.title
        FROM user_item ui_payer
        -- Tìm phòng của người trả tiền
        JOIN relationship r_payer ON ui_payer.relationship = r_payer.relationship_id
        -- Tìm các thành viên khác cùng phòng
        JOIN relationship r_family ON r_payer.apartment_id = r_family.apartment_id
        JOIN user_item ui_family ON r_family.relationship_id = ui_family.relationship
        -- Tìm khoản nợ của họ
        JOIN user_finances uf ON ui_family.user_id = uf.user_id
        JOIN finances f ON uf.finance_id = f.id
        WHERE ui_payer.user_id = $1
        AND uf.finance_id = $2
    `, [user_id, finance_id]);

    let targetIds = [];
    let representativeInfo = null;

    if (familyRes.rows.length > 0) {
        // Trường hợp ở trong phòng: Update hết cho cả nhà
        targetIds = familyRes.rows.map(r => r.id);
        representativeInfo = familyRes.rows[0];
    } else {
        // Trường hợp user lẻ (không phòng, hoặc lỗi data): Update chính mình
        const selfRes = await client.query(`
            SELECT uf.id, COALESCE(uf.amount, f.amount) as real_amount, f.title
            FROM user_finances uf JOIN finances f ON uf.finance_id = f.id
            WHERE uf.user_id = $1 AND uf.finance_id = $2
        `, [user_id, finance_id]);

        if (selfRes.rows.length === 0) {
            await client.query("ROLLBACK");
            return res.status(404).json({ error: "Không tìm thấy khoản thu" });
        }
        targetIds = [selfRes.rows[0].id];
        representativeInfo = selfRes.rows[0];
    }

    // 2. Update trạng thái
    await client.query(`UPDATE user_finances SET status = $1 WHERE id = ANY($2::int[])`, [status, targetIds]);

    // 3. Tạo Invoice
    if (status === 'da_thanh_toan') {
        const ordercode = `USER-${Date.now()}-${user_id}`;
        // Kiểm tra trùng
        const existing = await client.query("SELECT invoice_id FROM invoice WHERE finance_id = ANY($1::int[])", [targetIds]);

        if (existing.rows.length === 0) {
            // Gắn invoice vào ID đầu tiên tìm thấy (đại diện)
            await client.query(`
              INSERT INTO invoice (finance_id, amount, description, ordercode, currency, paytime)
              VALUES ($1, $2, $3, $4, 'VND', NOW())
            `, [targetIds[0], representativeInfo.real_amount, representativeInfo.title, ordercode]);
        }
    } else {
        await client.query("DELETE FROM invoice WHERE finance_id = ANY($1::int[])", [targetIds]);
    }

    await client.query("COMMIT");
    res.json({ success: true });
  } catch (err) {
    await client.query("ROLLBACK");
    res.status(500).json({ error: "Lỗi server" });
  } finally { client.release(); }
});

// ... (Các phần còn lại của file giữ nguyên) ...

export default router;