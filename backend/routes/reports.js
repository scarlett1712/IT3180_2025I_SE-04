import express from "express";
import { pool } from "../db.js";
import { sendNotification } from "../utils/firebaseHelper.js";

const router = express.Router();

// 1. [USER] Tạo báo cáo sự cố (Insert + Notify Admin)
router.post("/create", async (req, res) => {
  const { user_id, asset_id, description } = req.body;

  if (!user_id || !asset_id || !description) {
      return res.status(400).json({ error: "Thiếu thông tin báo cáo." });
  }

  try {
    // 1. Insert dữ liệu mới
    await pool.query(
      `INSERT INTO incident_reports (user_id, asset_id, description, status)
       VALUES ($1, $2, $3, 'Pending')`,
      [user_id, asset_id, description]
    );

    // 2. 🔥 GỬI THÔNG BÁO CHO ADMIN
    // Tìm tất cả user có role_id = 2 (Admin) và có fcm_token
    const adminRes = await pool.query(`
        SELECT u.fcm_token
        FROM users u
        JOIN userrole ur ON u.user_id = ur.user_id
        WHERE ur.role_id = 2 AND u.fcm_token IS NOT NULL
    `);

    // Gửi loop cho tất cả admin
    for (const row of adminRes.rows) {
        if (row.fcm_token) {
            sendNotification(
                row.fcm_token,
                "Báo cáo sự cố mới",
                `Có một báo cáo sự cố mới từ cư dân: "${description}". Vui lòng kiểm tra.`,
                { type: "report" }
            );
        }
    }

    res.json({ success: true, message: "Gửi báo cáo thành công! Ban quản lý sẽ sớm kiểm tra." });
  } catch (err) {
    console.error("Create Report Error:", err);
    res.status(500).json({ error: "Lỗi server khi tạo báo cáo" });
  }
});

// 2. [ADMIN] Lấy danh sách báo cáo (Join để lấy tên người báo & tên thiết bị)
router.get("/all", async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT r.report_id,
             r.description,
             r.status,
             r.admin_note,
             TO_CHAR(r.created_at, 'YYYY-MM-DD HH24:MI') as created_at,

             -- Thông tin người báo
             ui.full_name as reporter_name,
             u.phone as reporter_phone,

             -- Thông tin thiết bị
             a.asset_name,
             a.location

      FROM incident_reports r
      JOIN users u ON r.user_id = u.user_id
      JOIN user_item ui ON r.user_id = ui.user_id
      LEFT JOIN asset a ON r.asset_id = a.asset_id

      ORDER BY r.created_at DESC
    `);

    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Lỗi tải danh sách báo cáo" });
  }
});

// 3. [ADMIN] Cập nhật trạng thái & Phản hồi
router.post("/update-status", async (req, res) => {
  const { report_id, status, admin_note } = req.body;
  // status: 'Processing', 'Completed', 'Rejected'

  try {
    // Cập nhật trạng thái và ghi chú
    await pool.query(
        `UPDATE incident_reports
         SET status = $1, admin_note = $2, resolved_at = (CASE WHEN $1='Completed' THEN NOW() ELSE resolved_at END)
         WHERE report_id = $3`,
        [status, admin_note || "", report_id]
    );

    // Gửi thông báo push cho người dân
    const userRes = await pool.query(
        "SELECT u.fcm_token FROM incident_reports r JOIN users u ON r.user_id = u.user_id WHERE r.report_id = $1",
        [report_id]
    );

    if(userRes.rows.length > 0 && userRes.rows[0].fcm_token) {
        let title = "🔔 Cập nhật phản ánh";
        let body = "";

        if (status === 'Processing') body = "Ban quản lý đã tiếp nhận và đang xử lý phản ánh của bạn.";
        else if (status === 'Completed') body = "Sự cố bạn báo cáo đã được xử lý xong. Cảm ơn bạn!";
        else if (status === 'Rejected') body = `Phản ánh của bạn bị từ chối. Lý do: ${admin_note}`;

        if (body) sendNotification(userRes.rows[0].fcm_token, title, body);
    }

    res.json({ success: true, message: "Đã cập nhật trạng thái báo cáo." });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Lỗi cập nhật" });
  }
});

export default router;