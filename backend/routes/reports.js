import express from "express";
import { pool } from "../db.js";
import { sendNotification } from "../utils/firebaseHelper.js";

const router = express.Router();

// 1. [USER] Tạo báo cáo sự cố -> Cập nhật thiết bị thành 'Broken'
router.post("/create", async (req, res) => {
  const { user_id, asset_id, description } = req.body;

  if (!user_id || !asset_id || !description) {
      return res.status(400).json({ error: "Thiếu thông tin báo cáo." });
  }

  const client = await pool.connect(); // 🔥 Dùng Client để chạy Transaction
  try {
    await client.query("BEGIN");

    // 1. Insert báo cáo (Trạng thái mặc định là Pending)
    await client.query(
      `INSERT INTO incident_reports (user_id, asset_id, description, status)
       VALUES ($1, $2, $3, 'Pending')`,
      [user_id, asset_id, description]
    );

    // 2. 🔥 TỰ ĐỘNG CẬP NHẬT THIẾT BỊ SANG 'Broken' (Hỏng/Chờ sửa)
    await client.query(
      `UPDATE asset SET status = 'Broken' WHERE asset_id = $1`,
      [asset_id]
    );

    await client.query("COMMIT");

    // 3. Gửi thông báo cho Admin (Sau khi commit thành công)
    const adminRes = await pool.query(`
        SELECT u.fcm_token
        FROM users u
        JOIN userrole ur ON u.user_id = ur.user_id
        WHERE ur.role_id = 2 AND u.fcm_token IS NOT NULL
    `);

    for (const row of adminRes.rows) {
        if (row.fcm_token) {
            sendNotification(
                row.fcm_token,
                "⚠️ Báo cáo sự cố mới",
                `Cư dân báo hỏng thiết bị #${asset_id}: "${description}"`,
                { type: "report", assetId: asset_id.toString() }
            );
        }
    }

    res.json({ success: true, message: "Gửi báo cáo thành công! Trạng thái thiết bị đã được cập nhật." });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Create Report Error:", err);
    res.status(500).json({ error: "Lỗi server khi tạo báo cáo" });
  } finally {
    client.release();
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

  if (!report_id || !status) {
      return res.status(400).json({ error: "Thiếu thông tin report_id hoặc status" });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Cập nhật Database
    await client.query(
        `UPDATE incident_reports
         SET status = $1,
             admin_note = $2,
             resolved_at = (CASE WHEN $1='Completed' THEN NOW() ELSE resolved_at END)
         WHERE report_id = $3`,
        [status, admin_note || "", report_id]
    );

    // 2. 🔥 LOGIC GỬI THÔNG BÁO "AN TOÀN"
    // Chúng ta bọc nó trong try-catch riêng để nếu lỗi thông báo thì vẫn tính là update thành công
    try {
        const userRes = await client.query(
            "SELECT u.fcm_token FROM incident_reports r JOIN users u ON r.user_id = u.user_id WHERE r.report_id = $1",
            [report_id]
        );

        if(userRes.rows.length > 0 && userRes.rows[0].fcm_token) {
            let title = "🔔 Cập nhật phản ánh";
            let body = "";

            if (status === 'Processing') body = "Ban quản lý đã tiếp nhận và đang xử lý phản ánh của bạn.";
            else if (status === 'Completed') body = "Sự cố bạn báo cáo đã được xử lý xong. Cảm ơn bạn!";
            else if (status === 'Rejected') body = `Phản ánh của bạn bị từ chối. Lý do: ${admin_note}`;

            if (body) {
                // Gọi hàm gửi (Import từ firebaseHelper)
                await sendNotification(userRes.rows[0].fcm_token, title, body);
            }
        }
    } catch (notifyError) {
        // 🔥 Nếu gửi thông báo lỗi, chỉ in ra console, KHÔNG làm crash server
        console.error("⚠️ Lỗi gửi thông báo (nhưng DB đã update):", notifyError.message);
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Đã cập nhật trạng thái báo cáo." });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("❌ Update Status Error:", err); // Xem lỗi chi tiết ở Terminal chạy Server
    res.status(500).json({ error: "Lỗi server khi cập nhật: " + err.message });
  } finally {
    client.release();
  }
});

export default router;