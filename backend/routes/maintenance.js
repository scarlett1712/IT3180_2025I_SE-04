import express from "express";
import { pool } from "../db.js";
import { sendNotification } from "../utils/firebaseHelper.js"; // Import để gửi thông báo

const router = express.Router();

/* ==========================================================
   🛠️ QUẢN LÝ TÀI SẢN (ASSETS)
========================================================== */

// 1. Lấy danh sách tài sản
router.get("/assets", async (req, res) => {
  try {
    const result = await pool.query("SELECT * FROM asset ORDER BY asset_id DESC");
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: "Lỗi lấy danh sách tài sản" });
  }
});

// 2. Thêm tài sản mới
router.post("/assets", async (req, res) => {
  const { asset_name, location, status, purchase_date } = req.body;
  try {
    await pool.query(
      "INSERT INTO asset (asset_name, location, status, purchase_date) VALUES ($1, $2, $3, $4)",
      [asset_name, location, status || 'Good', purchase_date]
    );
    res.json({ success: true, message: "Đã thêm tài sản mới." });
  } catch (err) {
    res.status(500).json({ error: "Lỗi thêm tài sản" });
  }
});

/* ==========================================================
   📅 QUẢN LÝ LỊCH BẢO TRÌ (SCHEDULE)
========================================================== */

// 3. Lấy danh sách lịch bảo trì (Kèm tên tài sản và tên nhân viên)
router.get("/schedule", async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT ms.*,
             a.asset_name, a.location,
             ui.full_name as staff_name, u.phone as staff_phone
      FROM maintenanceschedule ms
      JOIN asset a ON ms.asset_id = a.asset_id
      LEFT JOIN users u ON ms.user_id = u.user_id
      LEFT JOIN user_item ui ON u.user_id = ui.user_id
      ORDER BY ms.scheduled_date DESC
    `);
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Lỗi lấy lịch bảo trì" });
  }
});

// 4. Tạo lịch bảo trì & Giao việc cho nhân viên
router.post("/schedule/create", async (req, res) => {
  const { asset_id, scheduled_date, user_id, description } = req.body; // user_id là ID nhân viên kỹ thuật

  if (!asset_id || !scheduled_date) {
    return res.status(400).json({ error: "Thiếu thông tin bắt buộc" });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // Tạo lịch
    await client.query(
      `INSERT INTO maintenanceschedule (asset_id, scheduled_date, user_id, status, description)
       VALUES ($1, $2, $3, 'Pending', $4)`,
      [asset_id, scheduled_date, user_id, description]
    );

    // Cập nhật trạng thái tài sản thành "Đang bảo trì" (Maintenance)
    await client.query("UPDATE asset SET status = 'Maintenance' WHERE asset_id = $1", [asset_id]);

    await client.query("COMMIT");

    // 🔥 Gửi thông báo cho nhân viên nếu được giao việc
    if (user_id) {
      const userRes = await pool.query("SELECT fcm_token FROM users WHERE user_id = $1", [user_id]);
      if (userRes.rows.length > 0 && userRes.rows[0].fcm_token) {
        sendNotification(
            userRes.rows[0].fcm_token,
            "🛠️ Công việc mới",
            `Bạn được giao lịch bảo trì vào ngày ${scheduled_date}.`
        );
      }
    }

    res.json({ success: true, message: "Đã tạo lịch bảo trì." });
  } catch (err) {
    await client.query("ROLLBACK");
    console.error(err);
    res.status(500).json({ error: "Lỗi tạo lịch" });
  } finally {
    client.release();
  }
});

// 5. Cập nhật trạng thái bảo trì (Hoàn thành/Hủy)
router.post("/schedule/update", async (req, res) => {
  const { schedule_id, status, result_note } = req.body;
  // status: 'Completed', 'Cancelled', 'In Progress'

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // Cập nhật lịch
    await client.query(
      "UPDATE maintenanceschedule SET status = $1, result_note = $2 WHERE schedule_id = $3",
      [status, result_note, schedule_id]
    );

    // Nếu hoàn thành, cập nhật lại trạng thái tài sản thành 'Good'
    if (status === 'Completed') {
        // Lấy asset_id từ lịch
        const scheduleRes = await client.query("SELECT asset_id FROM maintenanceschedule WHERE schedule_id = $1", [schedule_id]);
        if (scheduleRes.rows.length > 0) {
            const assetId = scheduleRes.rows[0].asset_id;
            await client.query("UPDATE asset SET status = 'Good' WHERE asset_id = $1", [assetId]);
        }
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Cập nhật trạng thái thành công." });
  } catch (err) {
    await client.query("ROLLBACK");
    res.status(500).json({ error: "Lỗi cập nhật" });
  } finally {
    client.release();
  }
});

router.get("/staff-list", async (req, res) => {
  try {
    // Lấy user có role là nhân viên (giả sử role_id = 1 hoặc 3, hoặc lấy hết tùy logic)
    // Ở đây tôi lấy tất cả user để bạn dễ test, sau này bạn có thể thêm WHERE ur.role_id = ...
    const result = await pool.query(`
      SELECT u.user_id, ui.full_name, u.phone
      FROM users u
      JOIN user_item ui ON u.user_id = ui.user_id
      ORDER BY ui.full_name ASC
    `);
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: "Lỗi lấy danh sách nhân viên" });
  }
});

export default router;