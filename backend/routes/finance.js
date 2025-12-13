import express from "express";
import { pool } from "../db.js";
import admin from "firebase-admin";
import ExcelJS from 'exceljs';
import { sendNotification } from "../utils/firebaseHelper.js";

const router = express.Router();
const query = (text, params) => pool.query(text, params);

// ==================================================================
// 🛠️ KHỞI TẠO BẢNG (Removed status column from user_finances)
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
    // 🔥 REMOVED status column - payment status is now determined by invoice existence
    await query(`
      CREATE TABLE IF NOT EXISTS user_finances (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL,
        finance_id INTEGER NOT NULL REFERENCES finances(id) ON DELETE CASCADE,
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
    console.log("✅ Finance tables verified.");
  } catch (err) { console.error(err); }
};

// ==================================================================
// 🟢 [GET] LẤY DANH SÁCH CÁC KHOẢN THU (ADMIN)
// ==================================================================
router.get("/admin", async (req, res) => {
  try {
    console.log("📊 Fetching all finances (admin view)");

    const result = await query(`
      SELECT
        f.id,
        f.title,
        f.content,
        f.amount AS price,
        f.type,
        TO_CHAR(f.due_date, 'DD-MM-YYYY') AS due_date,
        f.created_at,
        f.created_by,
        COALESCE(ui.full_name, 'Ban quản lý') AS sender,

        -- Count distinct rooms
        COUNT(DISTINCT a.apartment_number) FILTER (WHERE f.type != 'chi_phi') AS total_rooms,

        -- 🔥 Count paid rooms by checking invoice existence
        COUNT(DISTINCT CASE
          WHEN EXISTS (
            SELECT 1 FROM invoice i
            WHERE i.finance_id = f.id
            AND i.user_id = uf.user_id
          )
          THEN a.apartment_number
        END) FILTER (WHERE f.type != 'chi_phi') AS paid_rooms,

        -- Total collected from invoices
        COALESCE((
            SELECT SUM(i.amount)
            FROM invoice i
            WHERE i.finance_id = f.id
        ), 0) AS total_collected_real

      FROM finances f
      LEFT JOIN user_finances uf ON f.id = uf.finance_id
      LEFT JOIN user_item ui ON f.created_by = ui.user_id
      LEFT JOIN relationship r ON ui.relationship = r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id = a.apartment_id
      GROUP BY f.id, f.title, f.content, f.amount, f.type, f.due_date, f.created_at, f.created_by, ui.full_name
      ORDER BY f.created_at DESC;
    `);

    console.log(`✅ Found ${result.rows.length} finance records`);
    res.json(result.rows);
  } catch (err) {
    console.error("❌ Error fetching admin finances:", err);
    console.error("Stack trace:", err.stack);
    res.status(500).json({
      error: "Lỗi server",
      detail: err.message
    });
  }
});

// ==================================================================
// 🟢 [GET] LẤY TẤT CẢ KHOẢN THU
// ==================================================================
router.get("/all", async (req, res) => {
  try {
    const result = await query(`
      SELECT id, title, content, amount, type,
             TO_CHAR(due_date, 'YYYY-MM-DD') AS due_date, created_by
      FROM finances
      ORDER BY due_date ASC NULLS LAST
    `);
    res.json(result.rows);
  } catch (e) {
    res.status(500).json({error:"Lỗi"});
  }
});

// ==================================================================
// 🟢 [GET] LẤY KHOẢN THU CỦA USER
// ==================================================================
router.get("/user/:userId", async (req, res) => {
  try {
    console.log(`📊 Fetching finances for user: ${req.params.userId}`);

    // 🔥 Use invoice table to determine status
    const result = await query(`
      SELECT
        f.id,
        f.title,
        f.content,
        f.amount AS price,
        f.type,
        TO_CHAR(f.due_date, 'DD-MM-YYYY') AS due_date,
        f.created_by,
        COALESCE(ui.full_name, 'Ban quản lý') AS sender,
        CASE
          WHEN EXISTS (
            SELECT 1 FROM invoice i
            WHERE i.finance_id = f.id
            AND i.user_id = $1
          )
          THEN 'da_thanh_toan'
          ELSE 'chua_thanh_toan'
        END AS status
      FROM finances f
      JOIN user_finances uf ON f.id = uf.finance_id
      LEFT JOIN user_item ui ON f.created_by = ui.user_id
      WHERE uf.user_id = $1 AND f.type != 'chi_phi'
      ORDER BY f.due_date ASC NULLS LAST
    `, [req.params.userId]);

    console.log(`✅ Found ${result.rows.length} finance records for user ${req.params.userId}`);
    res.json(result.rows);
  } catch (e) {
    console.error("❌ Error fetching user finances:", e);
    console.error("Stack trace:", e.stack);
    res.status(500).json({
      error: "Lỗi server khi lấy dữ liệu khoản thu",
      detail: e.message
    });
  }
});

// ==================================================================
// 🔥 [GET] KIỂM TRA TRẠNG THÁI THANH TOÁN CỦA USER
// ==================================================================
router.get("/user/payment-status/:financeId", async (req, res) => {
  try {
    const { financeId } = req.params;
    const { user_id } = req.query;

    if (!user_id) {
      return res.status(400).json({ error: "Missing user_id parameter" });
    }

    console.log(`🔍 Checking payment status for finance ${financeId}, user ${user_id}`);

    // 🔥 Check if invoice exists for this user and finance
    const result = await query(`
      SELECT
        CASE
          WHEN EXISTS (
            SELECT 1 FROM invoice
            WHERE finance_id = $1 AND user_id = $2
          )
          THEN 'da_thanh_toan'
          ELSE 'chua_thanh_toan'
        END AS status
    `, [financeId, user_id]);

    const status = result.rows[0].status;

    console.log(`✅ Payment status: ${status}`);

    res.json({ status });

  } catch (error) {
    console.error("❌ Error fetching payment status:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

// ==================================================================
// 🟢 [GET] LẤY DANH SÁCH USER CỦA MỘT KHOẢN THU
// ==================================================================
router.get("/:financeId/users", async (req, res) => {
  try {
    // 🔥 Use invoice table to determine status
    const result = await query(`
      SELECT
        ui.full_name,
        uf.user_id,
        a.apartment_number AS room,
        CASE
          WHEN EXISTS (
            SELECT 1 FROM invoice i
            WHERE i.finance_id = $1
            AND i.user_id = uf.user_id
          )
          THEN 'da_thanh_toan'
          ELSE 'chua_thanh_toan'
        END AS status
      FROM user_finances uf
      JOIN user_item ui ON uf.user_id = ui.user_id
      JOIN relationship r ON ui.relationship = r.relationship_id
      JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE uf.finance_id = $1
      ORDER BY a.apartment_number ASC
    `, [req.params.financeId]);
    res.json(result.rows);
  } catch (e) {
    console.error("Error fetching finance users:", e);
    res.status(500).json({error:"Lỗi"});
  }
});

// ==================================================================
// 🔵 [PUT] CẬP NHẬT TRẠNG THÁI THANH TOÁN (ADMIN - BY ROOM)
// ==================================================================
router.put("/update-status", async (req, res) => {
  const { room, finance_id, status } = req.body;
  console.log(`[UPDATE] Room: ${room}, FinanceID: ${finance_id}, Status: ${status}`);

  if (!room || !finance_id || !status) {
    return res.status(400).json({ error: "Thiếu thông tin" });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // Get user_id for this room
    const userResult = await client.query(`
      SELECT ui.user_id
      FROM user_item ui
      JOIN relationship r ON ui.relationship = r.relationship_id
      JOIN apartment a ON r.apartment_id = a.apartment_id
      WHERE a.apartment_number = $1
    `, [room]);

    if (userResult.rows.length === 0) {
      await client.query("ROLLBACK");
      return res.status(404).json({ error: "Không tìm thấy user cho phòng này" });
    }

    const userId = userResult.rows[0].user_id;

    if (status === 'da_thanh_toan') {
      // 🔥 Create invoice if marking as paid
      const financeResult = await client.query(
        "SELECT title, amount FROM finances WHERE id = $1",
        [finance_id]
      );

      if (financeResult.rows.length === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Không tìm thấy khoản thu" });
      }

      const finance = financeResult.rows[0];
      const ordercode = `ADMIN-${Date.now()}-${userId}`;

      // Check if invoice already exists
      const existingInvoice = await client.query(
        "SELECT id FROM invoice WHERE finance_id = $1 AND user_id = $2",
        [finance_id, userId]
      );

      if (existingInvoice.rows.length === 0) {
        await client.query(`
          INSERT INTO invoice (finance_id, user_id, amount, description, ordercode, currency, paytime)
          VALUES ($1, $2, $3, $4, $5, 'VND', NOW())
        `, [finance_id, userId, finance.amount, finance.title, ordercode]);
      }

    } else {
      // 🔥 Delete invoice if marking as unpaid
      await client.query(
        "DELETE FROM invoice WHERE finance_id = $1 AND user_id = $2",
        [finance_id, userId]
      );
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Cập nhật thành công" });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Error updating payment status:", err);
    res.status(500).json({ error: "Lỗi server" });
  } finally {
    client.release();
  }
});

// ==================================================================
// 🔵 [PUT] CẬP NHẬT TRẠNG THÁI THANH TOÁN (USER)
// ==================================================================
router.put("/user/update-status", async (req, res) => {
  const { user_id, finance_id, status } = req.body;

  if (!user_id || !finance_id || !status) {
    return res.status(400).json({ error: "Thiếu thông tin" });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    if (status === 'da_thanh_toan') {
      // 🔥 Create invoice if marking as paid
      const financeResult = await client.query(
        "SELECT title, amount FROM finances WHERE id = $1",
        [finance_id]
      );

      if (financeResult.rows.length === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Không tìm thấy khoản thu" });
      }

      const finance = financeResult.rows[0];
      const ordercode = `USER-${Date.now()}-${user_id}`;

      // Check if invoice already exists
      const existingInvoice = await client.query(
        "SELECT id FROM invoice WHERE finance_id = $1 AND user_id = $2",
        [finance_id, user_id]
      );

      if (existingInvoice.rows.length === 0) {
        await client.query(`
          INSERT INTO invoice (finance_id, user_id, amount, description, ordercode, currency, paytime)
          VALUES ($1, $2, $3, $4, $5, 'VND', NOW())
        `, [finance_id, user_id, finance.amount, finance.title, ordercode]);
      }

    } else {
      // 🔥 Delete invoice if marking as unpaid/cancelled
      await client.query(
        "DELETE FROM invoice WHERE finance_id = $1 AND user_id = $2",
        [finance_id, user_id]
      );
    }

    await client.query("COMMIT");
    res.json({ success: true });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Error updating user payment status:", err);
    res.status(500).json({ error: "Lỗi server" });
  } finally {
    client.release();
  }
});

// ==================================================================
// 🔵 [PUT] CẬP NHẬT THÔNG TIN KHOẢN THU
// ==================================================================
router.put("/:id", async (req, res) => {
  const { id } = req.params;
  const { title, content, amount, due_date } = req.body;
  if (!title) {
    return res.status(400).json({ error: "Thiếu tiêu đề" });
  }
  const finalAmount = (amount === "" || amount === null || amount === "null") ? null : amount;
  try {
    const result = await query(`
      UPDATE finances
      SET title=$1, content=$2, amount=$3, due_date=TO_DATE($4, 'DD-MM-YYYY')
      WHERE id=$5
      RETURNING id
    `, [title, content, finalAmount, due_date, id]);

    if (result.rowCount === 0) {
      return res.status(404).json({ error: "Không tìm thấy" });
    }
    res.json({ success: true });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Lỗi server" });
  }
});

// ==================================================================
// 🔴 [DELETE] XÓA KHOẢN THU
// ==================================================================
router.delete("/:id", async (req, res) => {
  const { id } = req.params;
  try {
    const result = await query("DELETE FROM finances WHERE id = $1 RETURNING id", [id]);
    if (result.rowCount === 0) {
      return res.status(404).json({ error: "Không tìm thấy" });
    }
    res.json({ success: true });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Lỗi server" });
  }
});

// ==================================================================
// 🟢 [POST] TẠO KHOẢN THU MỚI
// ==================================================================
router.post("/create", async (req, res) => {
  const { title, content, amount, due_date, target_rooms, type, created_by } = req.body;
  if (!title || !target_rooms) {
    return res.status(400).json({ error: "Thiếu dữ liệu" });
  }
  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    const financeResult = await client.query(`
      INSERT INTO finances (title, content, amount, due_date, type, created_by)
      VALUES ($1, $2, $3, TO_DATE($4, 'DD-MM-YYYY'), $5, $6)
      RETURNING id
    `, [title, content || "", amount, due_date, type || "Bắt buộc", created_by]);

    const newId = financeResult.rows[0].id;

    const userResult = await client.query(`
      SELECT ui.user_id, u.fcm_token
      FROM user_item ui
      JOIN users u ON ui.user_id=u.user_id
      LEFT JOIN relationship r ON ui.relationship=r.relationship_id
      LEFT JOIN apartment a ON r.apartment_id=a.apartment_id
      WHERE a.apartment_number=ANY($1)
    `, [target_rooms]);

    for (const row of userResult.rows) {
      // 🔥 Just insert into user_finances, no status column
      await client.query(`
        INSERT INTO user_finances (user_id, finance_id)
        VALUES ($1, $2)
        ON CONFLICT DO NOTHING
      `, [row.user_id, newId]);

      if (row.fcm_token) {
        sendNotification(
          row.fcm_token,
          "📢 Phí mới",
          `Khoản thu mới: "${title}"`,
          { type: "finance", id: newId.toString() }
        );
      }
    }

    await client.query("COMMIT");
    res.status(201).json({ success: true });
  } catch (err) {
    await client.query("ROLLBACK");
    console.error(err);
    res.status(500).json({ error: err.message });
  } finally {
    client.release();
  }
});

// ==================================================================
// 🟢 [POST] TẠO HÓA ĐƠN ĐIỆN/NƯỚC HÀNG LOẠT
// ==================================================================
router.post("/create-utility-bulk", async (req, res) => {
  const { data, type, month, year } = req.body;
  if (!data || data.length === 0) {
    return res.status(400).json({ error: "Dữ liệu sai" });
  }

  const client = await pool.connect();
  let successCount = 0, errors = [];

  try {
    await client.query("BEGIN");

    const ratesRes = await client.query(
      "SELECT * FROM utility_rates WHERE type=$1 ORDER BY min_usage ASC",
      [type]
    );
    const rates = ratesRes.rows;
    const typeName = type === 'electricity' ? "Tiền điện" : "Tiền nước";

    for (const item of data) {
      const { room, old_index, new_index } = item;
      if (!room || new_index <= old_index) {
        errors.push(`P${room}: Sai số liệu`);
        continue;
      }

      const titlePattern = `${typeName} T${month}/${year} - P${room}`;
      const checkExist = await client.query("SELECT id FROM finances WHERE title=$1", [titlePattern]);
      if (checkExist.rows.length > 0) {
        errors.push(`P${room}: Đã có`);
        continue;
      }

      const usage = new_index - old_index;
      let totalCost = 0, remaining = usage;
      for (const tier of rates) {
        if (remaining <= 0) break;
        const range = tier.max_usage ? (tier.max_usage - tier.min_usage + 1) : Infinity;
        const used = Math.min(remaining, range);
        totalCost += used * parseFloat(tier.price);
        remaining -= used;
      }

      const userRes = await client.query(`
        SELECT ui.user_id, u.fcm_token
        FROM user_item ui
        JOIN users u ON ui.user_id=u.user_id
        JOIN relationship r ON ui.relationship=r.relationship_id
        JOIN apartment a ON r.apartment_id=a.apartment_id
        WHERE a.apartment_number=$1
      `, [room]);

      if (userRes.rows.length === 0) {
        errors.push(`P${room}: Vắng chủ`);
        continue;
      }

      const fRes = await client.query(`
        INSERT INTO finances (title, content, amount, type, due_date, created_by)
        VALUES ($1, $2, $3, 'bat_buoc', NOW() + INTERVAL '10 days', 1)
        RETURNING id
      `, [titlePattern, `Cũ: ${old_index} | Mới: ${new_index} | Dùng: ${usage}`, totalCost]);

      const fId = fRes.rows[0].id;

      for (const u of userRes.rows) {
        // 🔥 Just insert into user_finances, no status
        await client.query(
          "INSERT INTO user_finances (user_id, finance_id) VALUES ($1, $2) ON CONFLICT DO NOTHING",
          [u.user_id, fId]
        );
        if (u.fcm_token) {
          sendNotification(
            u.fcm_token,
            `📢 ${typeName}`,
            `P${room}: ${totalCost.toLocaleString()}đ`,
            { type: "finance", id: fId.toString() }
          );
        }
      }
      successCount++;
    }

    await client.query("COMMIT");
    res.json({ success: true, message: `Đã tạo ${successCount} hóa đơn.`, errors });
  } catch (err) {
    await client.query("ROLLBACK");
    console.error(err);
    res.status(500).json({ error: "Lỗi server" });
  } finally {
    client.release();
  }
});

// ==================================================================
// 🟢 [GET] THỐNG KÊ THU CHI
// ==================================================================
router.get("/statistics", async (req, res) => {
  try {
    const { month, year } = req.query;
    const m = (month && month !== '0') ? parseInt(month) : null;
    const y = year ? parseInt(year) : null;

    const rev = await query(`
      SELECT COALESCE(SUM(amount), 0) as val
      FROM invoice
      WHERE ($1::int IS NULL OR EXTRACT(MONTH FROM paytime)=$1)
        AND ($2::int IS NULL OR EXTRACT(YEAR FROM paytime)=$2)
    `, [m, y]);

    const exp = await query(`
      SELECT COALESCE(SUM(amount), 0) as val
      FROM finances
      WHERE type='chi_phi'
        AND ($1::int IS NULL OR EXTRACT(MONTH FROM due_date)=$1)
        AND ($2::int IS NULL OR EXTRACT(YEAR FROM due_date)=$2)
    `, [m, y]);

    res.json({
      revenue: parseFloat(rev.rows[0].val),
      expense: parseFloat(exp.rows[0].val)
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({error:"Lỗi"});
  }
});

// ==================================================================
// 🔵 [POST] CẬP NHẬT ĐỊNH MỨC GIÁ
// ==================================================================
router.post("/update-rates", async (req, res) => {
  const { type, tiers } = req.body;

  if (!tiers || !Array.isArray(tiers)) {
    return res.status(400).json({ error: "Dữ liệu tiers không hợp lệ" });
  }

  const uniqueTiers = [];
  const seen = new Set();
  for (const t of tiers) {
    const key = `${t.min}-${t.max}`;
    if (!seen.has(key)) {
      seen.add(key);
      uniqueTiers.push(t);
    }
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query("DELETE FROM utility_rates WHERE type=$1", [type]);

    for (const t of uniqueTiers) {
      await client.query(
        "INSERT INTO utility_rates (type, tier_name, min_usage, max_usage, price) VALUES ($1, $2, $3, $4, $5)",
        [type, t.tier_name, t.min, t.max, t.price]
      );
    }

    await client.query("COMMIT");
    res.json({ success: true });
  } catch (e) {
    await client.query("ROLLBACK");
    if (e.code === '23505') {
      res.status(400).json({ error: "Dữ liệu bậc giá bị trùng lặp trong hệ thống." });
    } else {
      console.error(e);
      res.status(500).json({ error: "Lỗi server khi cập nhật định mức giá." });
    }
  } finally {
    client.release();
  }
});

// ==================================================================
// 🟢 [GET] LẤY ĐỊNH MỨC GIÁ
// ==================================================================
router.get("/utility-rates", async (req, res) => {
  try {
    const result = await pool.query(
      "SELECT * FROM utility_rates WHERE type=$1 ORDER BY min_usage ASC",
      [req.query.type]
    );
    res.json(result.rows);
  } catch (e) {
    console.error(e);
    res.status(500).json({error:"Lỗi"});
  }
});

export default router;