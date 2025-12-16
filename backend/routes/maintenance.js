import express from "express";
import { pool } from "../db.js";
import { sendNotification } from "../utils/firebaseHelper.js";
// 🔥 Import helper upload ảnh
import { uploadToCloudinary } from "../utils/cloudinaryHelper.js";

const router = express.Router();

/* ==========================================================
   🛠️ QUẢN LÝ TÀI SẢN (ASSETS)
========================================================== */

// 1. Lấy danh sách tài sản
router.get("/assets", async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT a.*,
             (SELECT image_url FROM asset_images ai WHERE ai.asset_id = a.asset_id LIMIT 1) as thumbnail
      FROM asset a
      ORDER BY a.asset_id DESC
    `);
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Lỗi lấy danh sách tài sản" });
  }
});

// 2. Thêm tài sản mới
router.post("/assets", async (req, res) => {
  const { asset_name, location, status, purchase_date, images } = req.body;

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    const assetRes = await client.query(
      "INSERT INTO asset (asset_name, location, status, purchase_date) VALUES ($1, $2, $3, $4) RETURNING asset_id",
      [asset_name, location, status || 'Good', purchase_date]
    );
    const assetId = assetRes.rows[0].asset_id;

    if (images && Array.isArray(images) && images.length > 0) {
      console.log(`📸 Uploading ${images.length} images for asset ${assetId}...`);
      for (const base64 of images) {
        const uploadRes = await uploadToCloudinary(base64, "enoti_assets");
        if (uploadRes && uploadRes.url) {
          await client.query(
            "INSERT INTO asset_images (asset_id, image_url) VALUES ($1, $2)",
            [assetId, uploadRes.url]
          );
        }
      }
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Đã thêm tài sản mới thành công." });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Lỗi thêm tài sản:", err);
    res.status(500).json({ error: "Lỗi thêm tài sản" });
  } finally {
    client.release();
  }
});

/* ==========================================================
   🔥 [MỚI] API CẬP NHẬT & XÓA TÀI SẢN (CHO ADMIN)
========================================================== */

// 2.1. Cập nhật thông tin thiết bị
router.put("/asset/:id", async (req, res) => {
  const { id } = req.params;
  const { name, location, status } = req.body; // Nhận name, location, status từ Android

  try {
    const query = `
      UPDATE asset
      SET asset_name = $1, location = $2, status = $3
      WHERE asset_id = $4
    `;
    const result = await pool.query(query, [name, location, status, id]);

    if (result.rowCount === 0) {
        return res.status(404).json({ error: "Không tìm thấy thiết bị" });
    }

    res.json({ message: "Cập nhật thành công" });
  } catch (error) {
    console.error("Lỗi cập nhật asset:", error);
    res.status(500).json({ error: "Lỗi server: " + error.message });
  }
});

// 2.2. Xóa thiết bị
router.delete("/asset/:id", async (req, res) => {
  const { id } = req.params;
  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // Xóa ảnh liên quan
    await client.query("DELETE FROM asset_images WHERE asset_id = $1", [id]);

    // Xóa lịch bảo trì liên quan
    await client.query("DELETE FROM maintenanceschedule WHERE asset_id = $1", [id]);

    // Xóa báo cáo sự cố liên quan
    await client.query("DELETE FROM incident_reports WHERE asset_id = $1", [id]);

    // Cuối cùng xóa asset
    const result = await client.query("DELETE FROM asset WHERE asset_id = $1", [id]);

    if (result.rowCount === 0) {
        await client.query("ROLLBACK");
        return res.status(404).json({ error: "Không tìm thấy thiết bị" });
    }

    await client.query("COMMIT");
    res.json({ message: "Đã xóa thiết bị" });
  } catch (error) {
    await client.query("ROLLBACK");
    console.error("Lỗi xóa asset:", error);
    res.status(500).json({ error: "Lỗi server: " + error.message });
  } finally {
    client.release();
  }
});


/* ==========================================================
   📅 QUẢN LÝ LỊCH BẢO TRÌ (SCHEDULE)
========================================================== */

// 3. Lấy danh sách lịch bảo trì
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

// 4. Tạo lịch bảo trì
router.post("/schedule/create", async (req, res) => {
  const { asset_id, scheduled_date, user_id, description } = req.body;

  if (!asset_id || !scheduled_date) {
    return res.status(400).json({ error: "Thiếu thông tin bắt buộc" });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    await client.query(
      `INSERT INTO maintenanceschedule (asset_id, scheduled_date, user_id, status, description)
       VALUES ($1, $2, $3, 'Pending', $4)`,
      [asset_id, scheduled_date, user_id, description]
    );

    await client.query("UPDATE asset SET status = 'Maintenance' WHERE asset_id = $1", [asset_id]);

    await client.query("COMMIT");

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

// 5. Cập nhật trạng thái bảo trì
router.post("/schedule/update", async (req, res) => {
  const { schedule_id, status, result_note } = req.body;
  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    await client.query(
      "UPDATE maintenanceschedule SET status = $1, result_note = $2 WHERE schedule_id = $3",
      [status, result_note, schedule_id]
    );

    const scheduleRes = await client.query("SELECT asset_id FROM maintenanceschedule WHERE schedule_id = $1", [schedule_id]);
    if (scheduleRes.rows.length > 0) {
        const assetId = scheduleRes.rows[0].asset_id;
        if (status === 'In Progress') {
            await client.query("UPDATE asset SET status = 'Maintenance' WHERE asset_id = $1", [assetId]);
        }
        else if (status === 'Completed') {
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

// 6. Lấy danh sách nhân viên
router.get("/staff-list", async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT u.user_id, ui.full_name, u.phone
      FROM users u
      JOIN user_item ui ON u.user_id = ui.user_id
      ORDER BY ui.full_name ASC
    `);
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Lỗi lấy danh sách nhân viên" });
  }
});

// 7. [USER & ADMIN] Lấy chi tiết thiết bị (Kèm ảnh & Lịch sử)
router.get("/asset/:asset_id/details", async (req, res) => {
  const { asset_id } = req.params;
  const { user_id, role } = req.query;

  try {
    const assetRes = await pool.query("SELECT * FROM asset WHERE asset_id = $1", [asset_id]);
    if (assetRes.rows.length === 0) {
      return res.status(404).json({ error: "Không tìm thấy thiết bị" });
    }
    const assetInfo = assetRes.rows[0];

    const imagesRes = await pool.query("SELECT image_url FROM asset_images WHERE asset_id = $1", [asset_id]);
    const imageUrls = imagesRes.rows.map(row => row.image_url);

    let historyQuery = "";
    let queryParams = [];

    if (role === 'admin') {
      historyQuery = `
        SELECT
          ms.schedule_id as id, 'Maintenance' as type, ms.status, ms.description,
          ms.result_note as result, TO_CHAR(ms.scheduled_date, 'YYYY-MM-DD HH24:MI:SS') as date,
          ui.full_name as performer_name
        FROM maintenanceschedule ms
        LEFT JOIN users u ON ms.user_id = u.user_id
        LEFT JOIN user_item ui ON u.user_id = ui.user_id
        WHERE ms.asset_id = $1
        ORDER BY ms.scheduled_date DESC
      `;
      queryParams = [asset_id];
    } else {
      historyQuery = `
        SELECT
          schedule_id as id, 'Maintenance' as type, status, description,
          result_note as result, TO_CHAR(scheduled_date, 'YYYY-MM-DD HH24:MI:SS') as date,
          'Ban quản lý' as performer_name
        FROM maintenanceschedule WHERE asset_id = $1
        UNION ALL
        SELECT
          report_id as id, 'MyReport' as type, status, description,
          admin_note as result, TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI:SS') as date,
          'Tôi' as performer_name
        FROM incident_reports WHERE asset_id = $1 AND user_id = $2
        ORDER BY date DESC
      `;
      queryParams = [asset_id, user_id || 0];
    }

    const historyRes = await pool.query(historyQuery, queryParams);

    res.json({
        asset: assetInfo,
        images: imageUrls,
        history: historyRes.rows
    });

  } catch (err) {
    console.error("Lỗi API chi tiết thiết bị:", err);
    res.status(500).json({ error: "Lỗi Server: " + err.message });
  }
});

export default router;