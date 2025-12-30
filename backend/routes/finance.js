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

  // Nếu gửi lên room (kiểu cũ) thì báo lỗi hoặc xử lý riêng,
  // nhưng theo yêu cầu mới ta ưu tiên user_id
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

    // Nếu không tìm thấy (VD: User vô gia cư hoặc không có khoản thu này),
    // thì chỉ update chính user đó thôi (fallback)
    let idsToUpdate = [];
    if (targetsRes.rows.length > 0) {
        idsToUpdate = targetsRes.rows.map(r => r.user_finance_id);
    } else {
        // Fallback: Tìm chính xác theo user_id gửi lên
        const directRes = await client.query(
            "SELECT id FROM user_finances WHERE user_id = $1 AND finance_id = $2",
            [targetId, finance_id]
        );
        if (directRes.rows.length > 0) idsToUpdate = [directRes.rows[0].id];
    }

    if (idsToUpdate.length === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Không tìm thấy dữ liệu để cập nhật." });
    }

    // 2. Thực hiện Update đồng loạt
    await client.query(
        `UPDATE user_finances SET status = $1 WHERE id = ANY($2::int[])`,
        [status, idsToUpdate]
    );

    // 3. Xử lý Invoice (Hóa đơn)
    // Chỉ tạo 1 hóa đơn đại diện cho lần thanh toán này (gắn với người được chọn)
    if (status === 'da_thanh_toan') {
        const representativeId = idsToUpdate[0]; // Lấy ID đầu tiên làm đại diện
        const ordercode = `ADMIN-${Date.now()}-${targetId}`;

        // Kiểm tra xem đã có hóa đơn nào cho nhóm này chưa
        const existing = await client.query(
            "SELECT invoice_id FROM invoice WHERE finance_id = ANY($1::int[])",
            [idsToUpdate]
        );

        if (existing.rows.length === 0) {
            // Lấy số tiền cần lưu (lấy từ bản ghi đầu tiên)
            const amountRes = await client.query(
                `SELECT COALESCE(uf.amount, f.amount) as real_amount, f.title
                 FROM user_finances uf JOIN finances f ON uf.finance_id = f.id
                 WHERE uf.id = $1`, [representativeId]
            );
            const { real_amount, title } = amountRes.rows[0];

            await client.query(`
              INSERT INTO invoice (finance_id, amount, description, ordercode, currency, paytime)
              VALUES ($1, $2, $3, $4, 'VND', NOW())
            `, [representativeId, real_amount, title, ordercode]);
        }
    } else {
        // Nếu hủy thanh toán -> Xóa hóa đơn của tất cả thành viên liên quan
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

// ==================================================================
// 🟢 [POST] TẠO KHOẢN THU (ĐÃ SỬA: TẠO CHO TẤT CẢ MỌI NGƯỜI)
// ==================================================================
router.post("/create", async (req, res) => {
  const { title, content, amount, due_date, target_rooms, type, created_by } = req.body;
  if (!title || !target_rooms) return res.status(400).json({ error: "Thiếu dữ liệu" });

  const validRooms = target_rooms.filter(r => r && r !== 'null' && r !== 'Vô gia cư');

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // Tạo khoản thu cha
    const financeResult = await client.query(`INSERT INTO finances (title, content, amount, due_date, type, created_by) VALUES ($1, $2, $3, TO_DATE($4, 'DD-MM-YYYY'), $5, $6) RETURNING id`, [title, content || "", amount, due_date, type || "Bắt buộc", created_by]);
    const newId = financeResult.rows[0].id;

    // 🔥 LẤY TẤT CẢ USER TRONG PHÒNG (Bỏ điều kiện is_head)
    const userResult = await client.query(`
      SELECT ui.user_id, u.fcm_token
      FROM user_item ui
      JOIN users u ON ui.user_id=u.user_id
      LEFT JOIN relationship r ON ui.relationship=r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id=a.apartment_id
      WHERE a.apartment_number = ANY($1)
      AND a.apartment_number IS NOT NULL
    `, [validRooms]);

    for (const row of userResult.rows) {
      await client.query(`INSERT INTO user_finances (user_id, finance_id, status) VALUES ($1, $2, 'chua_thanh_toan') ON CONFLICT DO NOTHING`, [row.user_id, newId]);
      if (row.fcm_token) sendNotification(row.fcm_token, "📢 Phí mới", `Khoản thu mới: "${title}"`, { type: "finance", id: newId.toString() });
    }
    await client.query("COMMIT");
    res.status(201).json({ success: true });
  } catch (err) { await client.query("ROLLBACK"); res.status(500).json({ error: err.message }); } finally { client.release(); }
});

// ==================================================================
// 🟢 [POST] TẠO ĐIỆN NƯỚC (ĐÃ SỬA: TẠO CHO TẤT CẢ)
// ==================================================================
router.post("/create-utility-bulk", async (req, res) => {
  const { data, type, month, year, auto_calculate } = req.body;

  if (auto_calculate === true) {
    return await createFixedFeeInvoice(req, res, type, month, year);
  }

  if (!data || !Array.isArray(data) || data.length === 0) return res.status(400).json({ error: "Dữ liệu trống" });

  const client = await pool.connect();
  let successCount = 0;
  let insertData = [];
  let totalAmountAllRooms = 0;

  try {
    await client.query("BEGIN");

    // Lấy bảng giá
    const ratesRes = await client.query("SELECT * FROM utility_rates WHERE type=$1 ORDER BY min_usage ASC", [type]);
    const rates = ratesRes.rows;
    if (rates.length === 0) { await client.query("ROLLBACK"); return res.status(400).json({ error: "Chưa cấu hình giá" }); }

    const typeName = type === 'electricity' ? "Tiền điện" : "Tiền nước";
    const titlePattern = `${typeName} T${month}/${year}`;
    const validData = data.filter(item => item.room && item.room !== 'null' && item.room !== 'Vô gia cư');
    const roomNumbers = validData.map(item => item.room);

    // 🔥 Lấy TOÀN BỘ thành viên trong các phòng này
    const usersRes = await client.query(`
      SELECT a.apartment_number AS room, ui.user_id, u.fcm_token
      FROM apartment a
      JOIN relationship r ON a.apartment_id = r.apartment_id
      JOIN user_item ui ON r.relationship_id = ui.relationship
      JOIN users u ON ui.user_id = u.user_id
      WHERE a.apartment_number = ANY($1)
    `, [roomNumbers]);

    const roomToUsers = {};
    usersRes.rows.forEach(row => {
      if (!roomToUsers[row.room]) roomToUsers[row.room] = [];
      roomToUsers[row.room].push({ user_id: row.user_id, fcm_token: row.fcm_token });
    });

    for (const item of validData) {
      const { room, old_index, new_index } = item;
      const usage = new_index - old_index;
      if (usage < 0) continue;

      let cost = 0;
      let remaining = usage;
      for (const tier of rates) {
        if (remaining <= 0) break;
        const tierSize = tier.max_usage !== null ? (tier.max_usage - tier.min_usage + 1) : remaining;
        const used = Math.min(remaining, tierSize);
        cost += used * parseFloat(tier.price);
        remaining -= used;
      }
      totalAmountAllRooms += cost;

      const users = roomToUsers[room];
      if (users) {
        // Tạo nợ cho TẤT CẢ thành viên
        users.forEach(u => {
            insertData.push({
              user_id: u.user_id,
              amount: cost,
              note: `Cũ: ${old_index} | Mới: ${new_index} | Dùng: ${usage}`,
              fcm_token: u.fcm_token,
              room
            });
        });
        successCount++;
      }
    }

    if (insertData.length === 0) { await client.query("ROLLBACK"); return res.json({ success: false, message: "Không tạo được hóa đơn nào" }); }

    const fRes = await client.query(`
      INSERT INTO finances (title, content, amount, type, due_date, created_by)
      VALUES ($1, $2, $3, 'bat_buoc', NOW() + INTERVAL '10 days', 1)
      RETURNING id
    `, [titlePattern, `Hóa đơn ${typeName} tháng ${month}/${year}`, totalAmountAllRooms]);
    const financeId = fRes.rows[0].id;

    for (const d of insertData) {
      await client.query(`
        INSERT INTO user_finances (user_id, finance_id, status, amount, note)
        VALUES ($1, $2, 'chua_thanh_toan', $3, $4)
        ON CONFLICT (user_id, finance_id) DO UPDATE SET amount = EXCLUDED.amount, note = EXCLUDED.note
      `, [d.user_id, financeId, d.amount, d.note]);

      if (d.fcm_token) sendNotification(d.fcm_token, `📢 ${typeName}`, `Phòng ${d.room}: ${d.amount.toLocaleString()}đ`, { type: "finance", id: financeId.toString() });
    }

    await client.query("COMMIT");
    res.json({ success: true, message: `Thành công cho ${successCount} phòng` });

  } catch (err) { await client.query("ROLLBACK"); res.status(500).json({ error: "Lỗi server" }); } finally { client.release(); }
});

// ... (Giữ nguyên các hàm phụ trợ khác createFixedFeeInvoice tương tự logic trên) ...
// (Bạn có thể copy logic loop users ở trên áp dụng cho createFixedFeeInvoice nếu cần)

// ... Các API GET, Statistics giữ nguyên ...
router.get("/admin", async (req, res) => { /* Giữ nguyên code cũ */
    try {
    const result = await query(`
      SELECT
        f.id, f.title, f.content, f.amount AS price, f.type,
        TO_CHAR(f.due_date, 'DD-MM-YYYY') AS due_date,
        f.created_at AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Ho_Chi_Minh' as created_at,
        f.created_by, COALESCE(creator.full_name, 'Ban quản lý') AS sender,
        COUNT(DISTINCT uf.user_id) AS total_rooms,
        COUNT(DISTINCT CASE WHEN uf.status = 'da_thanh_toan' THEN uf.user_id END) AS paid_rooms,
        COALESCE(SUM(inv.amount), 0) AS total_collected_real
      FROM finances f
      LEFT JOIN user_finances uf ON f.id = uf.finance_id
      LEFT JOIN invoice inv ON inv.finance_id = uf.id
      LEFT JOIN user_item creator ON f.created_by = creator.user_id
      GROUP BY f.id, f.title, f.content, f.amount, f.type, f.due_date, f.created_at, f.created_by, creator.full_name
      ORDER BY f.created_at DESC;
    `);
    res.json(result.rows);
  } catch (err) {
    console.error("Error admin finances:", err);
    res.status(500).json({ error: "Lỗi server" });
  }
});

router.get("/:financeId/users", async (req, res) => { /* Giữ nguyên code cũ */
    try {
    const financeId = req.params.financeId;
    const result = await query(`
      SELECT
        ui.full_name, uf.user_id, a.apartment_number AS room, uf.status, uf.id AS user_finance_id,
        COALESCE(uf.amount, f.amount) AS amount, uf.note
      FROM user_finances uf
      JOIN finances f ON uf.finance_id = f.id
      JOIN user_item ui ON uf.user_id = ui.user_id
      JOIN relationship r ON ui.relationship = r.relationship_id
      JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE uf.finance_id = $1
      AND a.apartment_number IS NOT NULL -- 🔥 Không hiện vô gia cư
      ORDER BY
        CASE
          WHEN a.apartment_number::TEXT ~ '^\d+$' THEN a.apartment_number::TEXT::INTEGER
          ELSE COALESCE((regexp_replace(a.apartment_number::TEXT, '\D', '', 'g'))::INTEGER, 0)
        END ASC
    `, [financeId]);
    res.json(result.rows);
  } catch (e) { res.status(500).json({error: "Lỗi server"}); }
});

router.get("/user/:userId", async (req, res) => { /* Giữ nguyên code cũ */
    try {
    const userId = req.params.userId;
    const result = await query(`
      SELECT
        f.id, f.title,
        CASE WHEN uf.note IS NOT NULL AND uf.note != '' THEN f.content || ' (' || uf.note || ')' ELSE f.content END as content,
        COALESCE(uf.amount, f.amount) AS price, f.type,
        TO_CHAR(f.due_date, 'DD-MM-YYYY') AS due_date,
        f.created_by, COALESCE(ui.full_name, 'Ban quản lý') AS sender,
        uf.status, uf.id AS user_finance_id
      FROM finances f
      JOIN user_finances uf ON f.id = uf.finance_id
      LEFT JOIN user_item ui ON f.created_by = ui.user_id
      LEFT JOIN relationship r ON (SELECT relationship FROM user_item WHERE user_id = uf.user_id) = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE uf.user_id = $1 AND f.type != 'chi_phi'
      -- Chỉ hiện nếu user đang ở trong 1 phòng hợp lệ (để an toàn)
      AND (a.apartment_number IS NOT NULL OR f.type = 'tu_nguyen')
      ORDER BY f.due_date ASC NULLS LAST
    `, [userId]);
    res.json(result.rows);
  } catch (e) { res.status(500).json({ error: "Lỗi server", detail: e.message }); }
});

router.get("/statistics", async (req, res) => { /* Giữ nguyên code cũ */
    try {
    const { month, year } = req.query;
    const m = (month && month !== '0') ? parseInt(month) : null;
    const y = year ? parseInt(year) : null;
    const rev = await query(`SELECT COALESCE(SUM(amount), 0) as val FROM invoice WHERE ($1::int IS NULL OR EXTRACT(MONTH FROM paytime)=$1) AND ($2::int IS NULL OR EXTRACT(YEAR FROM paytime)=$2)`, [m, y]);
    const exp = await query(`SELECT COALESCE(SUM(amount), 0) as val FROM finances WHERE type='chi_phi' AND ($1::int IS NULL OR EXTRACT(MONTH FROM due_date)=$1) AND ($2::int IS NULL OR EXTRACT(YEAR FROM due_date)=$2)`, [m, y]);
    res.json({ revenue: parseFloat(rev.rows[0].val), expense: parseFloat(exp.rows[0].val) });
  } catch (e) { res.status(500).json({error:"Lỗi"}); }
});

router.get("/utility-rates", async (req, res) => { /* Giữ nguyên code cũ */
    try {
    const result = await pool.query("SELECT * FROM utility_rates WHERE type=$1 ORDER BY min_usage ASC", [req.query.type]);
    res.json(result.rows);
  } catch (e) { res.status(500).json({error:"Lỗi"}); }
});

export default router;