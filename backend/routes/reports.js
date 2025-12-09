import express from "express";
import { pool } from "../db.js";
// 🔥 QUAN TRỌNG: Đảm bảo đường dẫn import đúng
import { sendNotification } from "../utils/firebaseHelper.js";

const router = express.Router();

// 1. [USER] Tạo báo cáo sự cố
router.post("/create", async (req, res) => {
  const { user_id, asset_id, description } = req.body;

  if (!user_id || !asset_id || !description) {
      return res.status(400).json({ error: "Thiếu thông tin báo cáo." });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Insert dữ liệu mới
    await client.query(
      `INSERT INTO incident_reports (user_id, asset_id, description, status, created_at)
       VALUES ($1, $2, $3, 'Pending', NOW())`,
      [user_id, asset_id, description]
    );

    // 2. Cập nhật trạng thái thiết bị -> Broken
    await client.query(
      `UPDATE asset SET status = 'Broken' WHERE asset_id = $1`,
      [asset_id]
    );

    await client.query("COMMIT");

    // 3. Gửi thông báo cho Admin
    (async () => {
        try {
            const adminRes = await pool.query(`
                SELECT u.fcm_token
                FROM users u
                JOIN userrole ur ON u.user_id = ur.user_id
                WHERE ur.role_id = 2 AND u.fcm_token IS NOT NULL
            `);
            for (const row of adminRes.rows) {
                if (row.fcm_token) {
                    sendNotification(row.fcm_token, "⚠️ Sự cố mới", description, { type: "report" })
                        .catch(e => console.error("Lỗi gửi push lẻ:", e.message));
                }
            }
        } catch (e) { console.error("Lỗi gửi thông báo admin:", e); }
    })();

    res.json({ success: true, message: "Gửi báo cáo thành công!" });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Create Report Error:", err);
    res.status(500).json({ error: "Lỗi server khi tạo báo cáo" });
  } finally {
    client.release();
  }
});

// 2. [ADMIN] Lấy danh sách báo cáo
router.get("/all", async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT r.report_id,
             r.description,
             r.status,
             r.admin_note,
             TO_CHAR(r.created_at, 'DD/MM/YYYY HH24:MI') as created_at,
             ui.full_name as reporter_name,
             u.phone as reporter_phone,
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
    res.status(500).json({ error: "Lỗi tải danh sách" });
  }
});

// 3. [ADMIN] Cập nhật trạng thái & Phản hồi (🔥 ĐÃ FIX LỖI 42P08 BẰNG CÁCH TÁCH PARAM)
router.post("/update-status", async (req, res) => {
  const { report_id, status, admin_note } = req.body;

  if (!report_id || !status) {
      return res.status(400).json({ error: "Thiếu thông tin report_id hoặc status" });
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    const safeNote = admin_note || "";

    // 🔥 KỸ THUẬT SỬA LỖI:
    // Thay vì dùng $1 cho cả 2 chỗ, ta dùng $1 cho việc SET và $3 cho việc SO SÁNH
    // Mảng tham số truyền vào sẽ là: [status, safeNote, status, report_id]
    // Điều này đảm bảo PostgreSQL không bị nhầm lẫn kiểu dữ liệu.

    await client.query(
        `UPDATE incident_reports
         SET status = $1,
             admin_note = $2,
             resolved_at = (CASE WHEN $3 = 'Completed' THEN NOW() ELSE resolved_at END)
         WHERE report_id = $4`,
        [status, safeNote, status, report_id] // Truyền status 2 lần
    );

    // 2. Gửi thông báo an toàn
    try {
        const userRes = await client.query(
            `SELECT u.fcm_token
             FROM incident_reports r
             JOIN users u ON r.user_id = u.user_id
             WHERE r.report_id = $1`,
            [report_id]
        );

        if(userRes.rows.length > 0 && userRes.rows[0].fcm_token) {
            let title = "🔔 Cập nhật phản ánh";
            let body = "";

            if (status === 'Processing') body = "Ban quản lý đang xử lý phản ánh của bạn.";
            else if (status === 'Completed') body = "Sự cố bạn báo cáo đã được xử lý xong.";
            else if (status === 'Rejected') body = `Phản ánh bị từ chối. Lý do: ${safeNote}`;

            if (body) {
                await sendNotification(userRes.rows[0].fcm_token, title, body, { type: "report_update" });
            }
        }
    } catch (notifyError) {
        console.error("⚠️ Lỗi gửi thông báo (Nhưng DB đã update):", notifyError.message);
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Đã cập nhật trạng thái báo cáo." });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("❌ Update Status Error:", err);
    res.status(500).json({ error: "Lỗi server: " + err.message });
  } finally {
    client.release();
  }
});

export default router;