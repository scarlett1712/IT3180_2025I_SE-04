import express from "express";
import { pool } from "../db.js";
import admin from "firebase-admin";
import ExcelJS from 'exceljs';
import { sendNotification } from "../utils/firebaseHelper.js";

const router = express.Router();
const query = (text, params) => pool.query(text, params);

// ==================================================================
// 🛠️ 0. KHỞI TẠO BẢNG
// ==================================================================
export const createFinanceTables = async () => {
  try {
    await query(`
      CREATE TABLE IF NOT EXISTS finances (
        id SERIAL PRIMARY KEY,
        title VARCHAR(255) NOT NULL,
        content TEXT,
        amount NUMERIC(12, 2),
        type VARCHAR(50) DEFAULT 'khoan_thu' NOT NULL,
        due_date DATE,
        created_by INTEGER,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );
    `);
    // Cập nhật bảng user_finances có thêm amount và note
    await query(`
      CREATE TABLE IF NOT EXISTS user_finances (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL,
        finance_id INTEGER NOT NULL REFERENCES finances(id) ON DELETE CASCADE,
        status VARCHAR(100) DEFAULT 'chua_thanh_toan',
        amount NUMERIC(12, 2) DEFAULT NULL, -- 🔥 Số tiền riêng từng hộ (điện/nước)
        note TEXT DEFAULT '',               -- 🔥 Ghi chú riêng (Chỉ số cũ/mới)
        UNIQUE(user_id, finance_id)
      );
    `);
    await query(`
      CREATE TABLE IF NOT EXISTS utility_rates (
        rate_id serial PRIMARY KEY,
        type character varying(20) NOT NULL,
        tier_name character varying(100),
        min_usage integer DEFAULT 0,
        max_usage integer,
        price numeric(10, 2) NOT NULL,
        updated_at timestamp without time zone DEFAULT now(),
        CONSTRAINT unique_rate_tier UNIQUE (type, min_usage, max_usage)
      );
    `);
    await query(`
      CREATE TABLE IF NOT EXISTS invoice (
        invoice_id SERIAL PRIMARY KEY,
        finance_id INTEGER NOT NULL, -- ID của dòng user_finances
        amount NUMERIC(12, 2),
        description TEXT,
        ordercode VARCHAR(255),
        currency VARCHAR(50) DEFAULT 'VND',
        paytime TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );
    `);
    console.log("✅ Finance tables verified.");
  } catch (err) { console.error(err); }
};

// ==================================================================
// 🟢 [GET] ADMIN: DANH SÁCH KHOẢN THU (Đã gom nhóm)
// ==================================================================
router.get("/admin", async (req, res) => {
  try {
    const result = await query(`
      SELECT
        f.id,
        f.title,
        f.content,
        f.amount AS price,
        f.type,
        TO_CHAR(f.due_date, 'DD-MM-YYYY') AS due_date,
        f.created_at AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Ho_Chi_Minh' as created_at,
        f.created_by,
        COALESCE(creator.full_name, 'Ban quản lý') AS sender,
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

// ==================================================================
// 🟢 [POST] TẠO KHOẢN THU THƯỜNG (Admin chọn phòng)
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

    // Lấy TOÀN BỘ user trong các phòng được chọn
    const userResult = await client.query(`
      SELECT ui.user_id, u.fcm_token
      FROM user_item ui
      JOIN users u ON ui.user_id=u.user_id
      LEFT JOIN relationship r ON ui.relationship=r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id=a.apartment_id
      WHERE a.apartment_number = ANY($1)
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
// 🟢 [POST] TẠO HÓA ĐƠN ĐIỆN/NƯỚC/DỊCH VỤ HÀNG LOẠT
// ==================================================================
router.post("/create-utility-bulk", async (req, res) => {
  const { data, type, month, year, auto_calculate } = req.body;

  // 🔥 Xử lý phí cố định (quản lý / dịch vụ)
  if (auto_calculate === true) {
    return await createFixedFeeInvoice(req, res, type, month, year);
  }

  // 🔥 Xử lý Điện / Nước (có chỉ số)
  if (!data || !Array.isArray(data) || data.length === 0) {
    return res.status(400).json({ error: "Dữ liệu trống hoặc sai định dạng" });
  }

  const client = await pool.connect();
  let successCount = 0;
  let errors = [];
  let totalAmountAllRooms = 0;
  let insertData = [];

  try {
    await client.query("BEGIN");

    // 1. Lấy bảng giá
    const ratesRes = await client.query(
      "SELECT * FROM utility_rates WHERE type=$1 ORDER BY min_usage ASC",
      [type]
    );
    const rates = ratesRes.rows;
    if (rates.length === 0) {
      await client.query("ROLLBACK");
      return res.status(400).json({ error: "Chưa cấu hình đơn giá" });
    }

    const typeName = type === 'electricity' ? "Tiền điện" : "Tiền nước";
    const titlePattern = `${typeName} T${month}/${year}`;

    // 2. Lấy thông tin tất cả phòng trong data
    const roomNumbers = data.map(item => item.room);
    const usersRes = await client.query(`
      SELECT
        a.apartment_number AS room,
        ui.user_id,
        u.fcm_token
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

    // 3. Tính tiền
    for (const item of data) {
      const { room, old_index, new_index } = item;
      if (new_index < old_index) {
        errors.push(`P${room}: Chỉ số mới nhỏ hơn cũ`);
        continue;
      }

      const usage = new_index - old_index;
      let cost = 0;
      let remaining = usage;

      for (const tier of rates) {
        if (remaining <= 0) break;
        const tierSize = tier.max_usage !== null
          ? (tier.max_usage - tier.min_usage + 1)
          : remaining;
        const used = Math.min(remaining, tierSize);
        cost += used * parseFloat(tier.price);
        remaining -= used;
      }

      totalAmountAllRooms += cost;

      const users = roomToUsers[room];
      if (!users || users.length === 0) {
        errors.push(`P${room}: Không tìm thấy cư dân`);
        continue;
      }

      // Tạo bill cho tất cả mọi người trong phòng
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

    if (insertData.length === 0) {
      await client.query("ROLLBACK");
      return res.json({ success: false, message: "Không tạo được hóa đơn nào", errors });
    }

    // 4. Tạo finance cha
    const fRes = await client.query(`
      INSERT INTO finances (title, content, amount, type, due_date, created_by)
      VALUES ($1, $2, $3, 'bat_buoc', NOW() + INTERVAL '10 days', 1)
      RETURNING id
    `, [titlePattern, `Hóa đơn ${typeName} tháng ${month}/${year}`, totalAmountAllRooms]);

    const financeId = fRes.rows[0].id;

    // 5. Insert user_finances
    for (const d of insertData) {
      await client.query(`
        INSERT INTO user_finances (user_id, finance_id, status, amount, note)
        VALUES ($1, $2, 'chua_thanh_toan', $3, $4)
        ON CONFLICT (user_id, finance_id) DO UPDATE
        SET amount = EXCLUDED.amount, note = EXCLUDED.note
      `, [d.user_id, financeId, d.amount, d.note]);

      if (d.fcm_token) {
        sendNotification(
          d.fcm_token,
          `📢 ${typeName} tháng ${month}/${year}`,
          `Phòng ${d.room}: ${d.amount.toLocaleString('vi-VN')}đ (${d.note})`,
          { type: "finance", id: financeId.toString() }
        );
      }
    }

    await client.query("COMMIT");
    res.json({
      success: true,
      message: `Tạo hóa đơn thành công cho ${successCount} phòng`,
      errors
    });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Lỗi bulk utility:", err);
    res.status(500).json({ error: "Lỗi server", detail: err.message });
  } finally {
    client.release();
  }
});

async function createFixedFeeInvoice(req, res, type, month, year) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    const rateRes = await client.query(
      "SELECT price FROM utility_rates WHERE type = $1 LIMIT 1",
      [type]
    );

    if (rateRes.rows.length === 0) {
      await client.query("ROLLBACK");
      return res.status(400).json({ error: "Chưa cấu hình đơn giá phí này" });
    }

    const pricePerM2 = parseFloat(rateRes.rows[0].price);
    const typeName = type === "management_fee" ? "Phí quản lý" : "Phí dịch vụ";

    // Lấy tất cả căn hộ có người ở + diện tích
    const roomsRes = await client.query(`
      SELECT
        a.apartment_number AS room,
        a.area,
        ui.user_id,
        u.fcm_token
      FROM apartment a
      JOIN relationship r ON a.apartment_id = r.apartment_id
      JOIN user_item ui ON r.relationship_id = ui.relationship
      JOIN users u ON ui.user_id = u.user_id
      WHERE a.area IS NOT NULL AND a.area > 0
    `);

    let totalAmount = 0;
    let insertData = [];

    for (const row of roomsRes.rows) {
      const amount = row.area * pricePerM2;
      totalAmount += amount;

      insertData.push({
        user_id: row.user_id,
        amount,
        note: `Diện tích: ${row.area}m² × ${pricePerM2.toLocaleString()}đ/m²`,
        fcm_token: row.fcm_token,
        room: row.room
      });
    }

    if (insertData.length === 0) {
      await client.query("ROLLBACK");
      return res.json({ success: false, message: "Không có căn hộ nào để tính phí" });
    }

    // Tạo finance cha
    const title = `${typeName} T${month}/${year}`;
    const fRes = await client.query(`
      INSERT INTO finances (title, content, amount, type, due_date, created_by)
      VALUES ($1, $2, $3, 'bat_buoc', NOW() + INTERVAL '10 days', 1)
      RETURNING id
    `, [title, `${typeName} tháng ${month}/${year}`, totalAmount]);

    const financeId = fRes.rows[0].id;

    // Insert user_finances
    for (const d of insertData) {
      await client.query(`
        INSERT INTO user_finances (user_id, finance_id, status, amount, note)
        VALUES ($1, $2, 'chua_thanh_toan', $3, $4)
        ON CONFLICT (user_id, finance_id) DO UPDATE
        SET amount = EXCLUDED.amount, note = EXCLUDED.note
      `, [d.user_id, financeId, d.amount, d.note]);

      if (d.fcm_token) {
        sendNotification(
          d.fcm_token,
          `📢 ${typeName}`,
          `Phòng ${d.room}: ${d.amount.toLocaleString('vi-VN')}đ (${d.note})`,
          { type: "finance", id: financeId.toString() }
        );
      }
    }

    await client.query("COMMIT");
    res.json({ success: true, message: `Chốt ${typeName} thành công` });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Lỗi chốt phí cố định:", err);
    res.status(500).json({ error: "Lỗi server" });
  } finally {
    client.release();
  }
}

// ==================================================================
// 🟢 [GET] CHI TIẾT USER CỦA 1 KHOẢN THU (CHO ADMIN)
// ==================================================================
router.get("/:financeId/users", async (req, res) => {
  try {
    const financeId = req.params.financeId;
    const result = await query(`
      SELECT
        ui.full_name,
        uf.user_id,
        a.apartment_number AS room,
        uf.status,
        uf.id AS user_finance_id,
        COALESCE(uf.amount, f.amount) AS amount, -- Lấy số tiền riêng nếu có
        uf.note
      FROM user_finances uf
      JOIN finances f ON uf.finance_id = f.id
      JOIN user_item ui ON uf.user_id = ui.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE uf.finance_id = $1
      ORDER BY
        CASE
          WHEN a.apartment_number ~ '^\d+$' THEN a.apartment_number::INTEGER
          ELSE COALESCE((regexp_replace(a.apartment_number, '\D', '', 'g'))::INTEGER, 0)
        END ASC
    `, [financeId]);
    res.json(result.rows);
  } catch (e) { res.status(500).json({error: "Lỗi server"}); }
});

// ==================================================================
// 🟢 [GET] DANH SÁCH KHOẢN THU CỦA USER
// ==================================================================
router.get("/user/:userId", async (req, res) => {
  try {
    const userId = req.params.userId;
    const result = await query(`
      SELECT
        f.id,
        f.title,
        -- Ghép ghi chú riêng vào nội dung nếu có
        CASE
            WHEN uf.note IS NOT NULL AND uf.note != '' THEN f.content || ' (' || uf.note || ')'
            ELSE f.content
        END as content,
        -- Ưu tiên lấy tiền riêng của user
        COALESCE(uf.amount, f.amount) AS price,
        f.type,
        TO_CHAR(f.due_date, 'DD-MM-YYYY') AS due_date,
        f.created_by,
        COALESCE(ui.full_name, 'Ban quản lý') AS sender,
        uf.status,
        uf.id AS user_finance_id
      FROM finances f
      JOIN user_finances uf ON f.id = uf.finance_id
      LEFT JOIN user_item ui ON f.created_by = ui.user_id
      LEFT JOIN relationship r ON (SELECT relationship FROM user_item WHERE user_id = uf.user_id) = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE uf.user_id = $1 AND f.type != 'chi_phi'
      ORDER BY f.due_date ASC NULLS LAST
    `, [userId]);
    res.json(result.rows);
  } catch (e) { res.status(500).json({ error: "Lỗi server", detail: e.message }); }
});

// ==================================================================
// 🔵 [PUT] ADMIN CẬP NHẬT TRẠNG THÁI (ĐỒNG BỘ CẢ PHÒNG)
// ==================================================================
router.put("/update-status", async (req, res) => {
  // Admin gửi: room (ưu tiên) hoặc user_id (nếu tick lẻ)
  const { room, user_id, finance_id, status } = req.body;
  if (!finance_id || !status) return res.status(400).json({ error: "Thiếu thông tin" });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    let userFinanceRows;

    // 1. Tìm tất cả record cần update
    // Nếu có room -> Tìm cả phòng
    if (room) {
        userFinanceRows = await client.query(`
          SELECT uf.id, uf.user_id, COALESCE(uf.amount, f.amount) as real_amount, f.title
          FROM user_finances uf
          JOIN finances f ON uf.finance_id = f.id
          JOIN user_item ui ON uf.user_id = ui.user_id
          JOIN relationship r ON ui.relationship = r.relationship_id
          JOIN apartment a ON r.apartment_id = a.apartment_id
          WHERE uf.finance_id = $1 AND a.apartment_number = $2
        `, [finance_id, room]);
    } else if (user_id) {
        // Fallback: Nếu gửi user_id, tìm phòng của user đó rồi lấy hết cả phòng (để đồng bộ)
        userFinanceRows = await client.query(`
            SELECT uf.id, uf.user_id, COALESCE(uf.amount, f.amount) as real_amount, f.title
            FROM user_finances uf
            JOIN finances f ON uf.finance_id = f.id
            JOIN user_item ui ON uf.user_id = ui.user_id
            JOIN relationship r ON ui.relationship = r.relationship_id
            WHERE uf.finance_id = $1
            AND r.apartment_id = (
                SELECT r2.apartment_id FROM user_item ui2
                JOIN relationship r2 ON ui2.relationship = r2.relationship_id
                WHERE ui2.user_id = $2
            )
        `, [finance_id, user_id]);
    }

    if (!userFinanceRows || userFinanceRows.rows.length === 0) {
      await client.query("ROLLBACK");
      // Tránh lỗi 404 gây crash app, trả về lỗi 400
      return res.status(400).json({ error: "Không tìm thấy dữ liệu cư dân để cập nhật." });
    }

    const targetIds = userFinanceRows.rows.map(r => r.id);
    const representative = userFinanceRows.rows[0];

    // 2. Cập nhật trạng thái
    await client.query(`UPDATE user_finances SET status = $1 WHERE id = ANY($2::int[])`, [status, targetIds]);

    // 3. Xử lý Invoice
    if (status === 'da_thanh_toan') {
      const ordercode = `ADMIN-${Date.now()}-${representative.user_id}`;
      // Kiểm tra xem nhóm này đã có invoice chưa
      const existing = await client.query("SELECT invoice_id FROM invoice WHERE finance_id = ANY($1::int[])", [targetIds]);

      if (existing.rows.length === 0) {
        // Gắn invoice vào bản ghi đầu tiên (representative)
        await client.query(`
          INSERT INTO invoice (finance_id, amount, description, ordercode, currency, paytime)
          VALUES ($1, $2, $3, $4, 'VND', NOW())
        `, [targetIds[0], representative.real_amount, representative.title, ordercode]);
      }
    } else {
      // Hủy thanh toán -> Xóa invoice
      await client.query("DELETE FROM invoice WHERE finance_id = ANY($1::int[])", [targetIds]);
    }

    await client.query("COMMIT");
    res.json({ success: true, count: targetIds.length });
  } catch (err) {
    await client.query("ROLLBACK");
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

    // 1. Tìm bản ghi của user và những người cùng phòng
    const familyRes = await client.query(`
        SELECT uf.id, uf.user_id, COALESCE(uf.amount, f.amount) as real_amount, f.title
        FROM user_finances uf
        JOIN finances f ON uf.finance_id = f.id
        JOIN user_item ui ON uf.user_id = ui.user_id
        JOIN relationship r ON ui.relationship = r.relationship_id
        WHERE uf.finance_id = $1
        AND r.apartment_id = (
            SELECT r2.apartment_id
            FROM user_item ui2
            JOIN relationship r2 ON ui2.relationship = r2.relationship_id
            WHERE ui2.user_id = $2
        )
    `, [finance_id, user_id]);

    let targetRows = familyRes.rows;

    // Nếu không tìm thấy ai cùng phòng (lỗi data hoặc user lẻ), tìm chính nó
    if (targetRows.length === 0) {
        const selfRes = await client.query(`
            SELECT uf.id, uf.user_id, COALESCE(uf.amount, f.amount) as real_amount, f.title
            FROM user_finances uf
            JOIN finances f ON uf.finance_id = f.id
            WHERE uf.user_id = $1 AND uf.finance_id = $2
        `, [user_id, finance_id]);
        targetRows = selfRes.rows;
    }

    if (targetRows.length === 0) {
      await client.query("ROLLBACK");
      return res.status(404).json({ error: "Không tìm thấy khoản thu để cập nhật" });
    }

    const targetIds = targetRows.map(r => r.id);
    const representative = targetRows[0];

    // 2. Update
    await client.query(`UPDATE user_finances SET status = $1 WHERE id = ANY($2::int[])`, [status, targetIds]);

    // 3. Invoice
    if (status === 'da_thanh_toan') {
        const ordercode = `USER-${Date.now()}-${user_id}`;
        const existing = await client.query("SELECT invoice_id FROM invoice WHERE finance_id = ANY($1::int[])", [targetIds]);

        if (existing.rows.length === 0) {
            await client.query(`
              INSERT INTO invoice (finance_id, amount, description, ordercode, currency, paytime)
              VALUES ($1, $2, $3, $4, 'VND', NOW())
            `, [targetIds[0], representative.real_amount, representative.title, ordercode]);
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
// 🔍 KIỂM TRA TRẠNG THÁI THANH TOÁN (SMART CHECK - NO 404)
// ==================================================================
router.get("/user/payment-status/:id", async (req, res) => {
  try {
    const financeId = req.params.id;
    const { user_id } = req.query;

    if (!user_id) return res.status(400).json({ error: "Thiếu user_id" });

    const client = await pool.connect();

    // 1. Tìm xem chính mình đã trả chưa
    const selfCheck = await client.query(
      `SELECT status FROM user_finances WHERE finance_id = $1 AND user_id = $2`,
      [financeId, user_id]
    );

    if (selfCheck.rows.length > 0 && selfCheck.rows[0].status === 'da_thanh_toan') {
        client.release();
        return res.json({ status: "da_thanh_toan" });
    }

    // 2. Nếu chưa, tìm xem người cùng phòng đã trả chưa
    const roomCheck = await client.query(`
      SELECT uf.status
      FROM user_finances uf
      JOIN user_item ui ON uf.user_id = ui.user_id
      JOIN relationship r ON ui.relationship = r.relationship_id
      WHERE uf.finance_id = $1
      AND uf.status = 'da_thanh_toan'
      AND r.apartment_id = (
          SELECT r2.apartment_id
          FROM user_item ui2
          JOIN relationship r2 ON ui2.relationship = r2.relationship_id
          WHERE ui2.user_id = $2
      )
      LIMIT 1
    `, [financeId, user_id]);

    client.release();

    if (roomCheck.rows.length > 0) {
        return res.json({ status: "da_thanh_toan" });
    } else {
        // 🔥 QUAN TRỌNG: Luôn trả 200 OK để App không bị lỗi mạng
        return res.json({ status: "chua_thanh_toan" });
    }

  } catch (err) {
    console.error("Check status error:", err);
    res.status(500).json({ error: err.message });
  }
});

// ... Các API bổ trợ khác (Delete, Update Info, Statistics, Upgrade DB) giữ nguyên
router.delete("/:id", async (req, res) => {
  const { id } = req.params;
  try {
    const result = await query("DELETE FROM finances WHERE id = $1 RETURNING id", [id]);
    if (result.rowCount === 0) return res.status(404).json({ error: "Không tìm thấy" });
    res.json({ success: true });
  } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

router.put("/:id", async (req, res) => {
  const { id } = req.params;
  const { title, content, amount, due_date } = req.body;
  if (!title) return res.status(400).json({ error: "Thiếu tiêu đề" });
  const finalAmount = (amount === "" || amount === null || amount === "null") ? null : amount;
  try {
    const result = await query(`UPDATE finances SET title=$1, content=$2, amount=$3, due_date=TO_DATE($4, 'DD-MM-YYYY') WHERE id=$5 RETURNING id`, [title, content, finalAmount, due_date, id]);
    if (result.rowCount === 0) return res.status(404).json({ error: "Không tìm thấy" });
    res.json({ success: true });
  } catch (err) { res.status(500).json({ error: "Lỗi server" }); }
});

router.get("/statistics", async (req, res) => {
  try {
    const { month, year } = req.query;
    const m = (month && month !== '0') ? parseInt(month) : null;
    const y = year ? parseInt(year) : null;
    const rev = await query(`SELECT COALESCE(SUM(amount), 0) as val FROM invoice WHERE ($1::int IS NULL OR EXTRACT(MONTH FROM paytime)=$1) AND ($2::int IS NULL OR EXTRACT(YEAR FROM paytime)=$2)`, [m, y]);
    const exp = await query(`SELECT COALESCE(SUM(amount), 0) as val FROM finances WHERE type='chi_phi' AND ($1::int IS NULL OR EXTRACT(MONTH FROM due_date)=$1) AND ($2::int IS NULL OR EXTRACT(YEAR FROM due_date)=$2)`, [m, y]);
    res.json({ revenue: parseFloat(rev.rows[0].val), expense: parseFloat(exp.rows[0].val) });
  } catch (e) { res.status(500).json({error:"Lỗi"}); }
});

router.post("/update-rates", async (req, res) => {
  const { type, tiers } = req.body;
  if (!tiers || !Array.isArray(tiers)) return res.status(400).json({ error: "Dữ liệu tiers không hợp lệ" });
  const uniqueTiers = [], seen = new Set();
  for (const t of tiers) { const key = `${t.min}-${t.max}`; if (!seen.has(key)) { seen.add(key); uniqueTiers.push(t); } }
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query("DELETE FROM utility_rates WHERE type=$1", [type]);
    for (const t of uniqueTiers) { await client.query("INSERT INTO utility_rates (type, tier_name, min_usage, max_usage, price) VALUES ($1, $2, $3, $4, $5)", [type, t.tier_name, t.min, t.max, t.price]); }
    await client.query("COMMIT");
    res.json({ success: true });
  } catch (e) { await client.query("ROLLBACK"); res.status(500).json({ error: "Lỗi server" }); } finally { client.release(); }
});

router.get("/utility-rates", async (req, res) => {
  try {
    const result = await pool.query("SELECT * FROM utility_rates WHERE type=$1 ORDER BY min_usage ASC", [req.query.type]);
    res.json(result.rows);
  } catch (e) { res.status(500).json({error:"Lỗi"}); }
});

// API Fix DB (Để chạy lần đầu nếu cần)
router.get("/upgrade-database-schema", async (req, res) => {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query(`ALTER TABLE user_finances ADD COLUMN IF NOT EXISTS amount NUMERIC(12, 2) DEFAULT NULL, ADD COLUMN IF NOT EXISTS note TEXT DEFAULT '';`);
    await client.query("COMMIT");
    res.send("<h1>✅ Database Upgraded!</h1>");
  } catch (err) { await client.query("ROLLBACK"); res.status(500).send("Error: " + err.message); } finally { client.release(); }
});

export default router;