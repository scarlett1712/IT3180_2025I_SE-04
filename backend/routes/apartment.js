import express from "express";
import { pool } from "../db.js"; // Đảm bảo đường dẫn tới db.js đúng
import { verifySession } from "../middleware/authMiddleware.js"; // Middleware bảo mật

const router = express.Router();
const query = (text, params) => pool.query(text, params);


// ==================================================================
// 🛠️ API FIX LỖI DB: CHO PHÉP CƯ DÂN VÔ GIA CƯ (Chạy 1 lần)
// ==================================================================
router.get("/fix-relationship-constraint", async (req, res) => {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    // 1. Cho phép cột apartment_id chứa giá trị NULL (để làm người vô gia cư)
    await client.query(`
      ALTER TABLE relationship
      ALTER COLUMN apartment_id DROP NOT NULL;
    `);

    // 2. (Tùy chọn) Đảm bảo bảng user_item không bị lỗi khóa ngoại khi relationship bị xóa
    // (Phòng hờ cho các logic khác)
    /* await client.query(`
      ALTER TABLE user_item
      DROP CONSTRAINT IF EXISTS user_item_relationship_fkey,
      ADD CONSTRAINT user_item_relationship_fkey
      FOREIGN KEY (relationship) REFERENCES relationship(relationship_id) ON DELETE SET NULL;
    `);
    */

    await client.query("COMMIT");
    console.log("✅ Đã sửa DB thành công: Cho phép apartment_id là NULL");
    res.send("<h1>✅ Đã sửa Database thành công! Giờ bạn có thể Xóa phòng và Đuổi người ra đường.</h1>");

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("❌ Lỗi sửa DB:", err);
    res.status(500).send("<h1>❌ Lỗi: " + err.message + "</h1>");
  } finally {
    client.release();
  }
});

// ==================================================================
// 📋 1. [GET] LẤY DANH SÁCH TẤT CẢ CĂN HỘ
// API: /api/apartments
// ==================================================================
router.get("/", verifySession, async (req, res) => {
  try {
    // Lấy danh sách, sắp xếp theo tầng và số phòng
    const result = await query(`
      SELECT
        apartment_id,
        building_id,
        apartment_number,
        floor,
        area,
        status,
        TO_CHAR(start_date, 'DD-MM-YYYY') as start_date,
        TO_CHAR(end_date, 'DD-MM-YYYY') as end_date
      FROM apartment
      ORDER BY floor ASC, apartment_number ASC
    `);
    res.json(result.rows);
  } catch (err) {
    console.error("Lỗi lấy danh sách phòng:", err);
    res.status(500).json({ error: "Lỗi server" });
  }
});

// ==================================================================
// 🔍 2. [GET] LẤY CHI TIẾT 1 CĂN HỘ
// API: /api/apartments/:id
// ==================================================================
router.get("/:id", verifySession, async (req, res) => {
  try {
    const { id } = req.params;
    const result = await query(`
      SELECT * FROM apartment WHERE apartment_id = $1
    `, [id]);

    if (result.rows.length === 0) {
      return res.status(404).json({ error: "Không tìm thấy phòng" });
    }
    res.json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: "Lỗi server" });
  }
});

// ==================================================================
// ➕ 3. [POST] THÊM PHÒNG MỚI
// API: /api/apartments/create
// Body: { "apartment_number": "101", "floor": 1, "area": 50.5, "building_id": 1, "status": "trong" }
// ==================================================================
router.post("/create", verifySession, async (req, res) => {
  const { building_id, apartment_number, floor, area, status, start_date } = req.body;

  // Kiểm tra dữ liệu bắt buộc
  if (!apartment_number || !floor || !area) {
    return res.status(400).json({ error: "Thiếu thông tin (Số phòng, Tầng, Diện tích)" });
  }

  try {
    // Kiểm tra trùng số phòng (trong cùng 1 tòa nhà)
    const check = await query(
      "SELECT apartment_id FROM apartment WHERE apartment_number = $1 AND building_id = $2",
      [apartment_number, building_id || 1] // Mặc định building_id = 1 nếu không gửi
    );
    if (check.rows.length > 0) {
      return res.status(400).json({ error: "Số phòng này đã tồn tại!" });
    }

    // Thêm mới
    const result = await query(
      `INSERT INTO apartment
       (building_id, apartment_number, floor, area, status, start_date)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING *`,
      [
        building_id || 1,
        apartment_number,
        floor,
        area,
        status || 'trong', // Mặc định trạng thái là 'trong' (trống)
        start_date ? new Date(start_date) : new Date()
      ]
    );

    res.json({ success: true, message: "Thêm phòng thành công", data: result.rows[0] });

  } catch (err) {
    console.error("Lỗi thêm phòng:", err);
    res.status(500).json({ error: err.message });
  }
});

// ==================================================================
// ✏️ 4. [PUT] CẬP NHẬT THÔNG TIN PHÒNG
// API: /api/apartments/update/:id
// ==================================================================
router.put("/update/:id", verifySession, async (req, res) => {
  const { id } = req.params;
  const { apartment_number, floor, area, status, building_id } = req.body;

  try {
    // Cập nhật (COALESCE giữ nguyên giá trị cũ nếu không gửi giá trị mới)
    const result = await query(
      `UPDATE apartment
       SET apartment_number = COALESCE($1, apartment_number),
           floor = COALESCE($2, floor),
           area = COALESCE($3, area),
           status = COALESCE($4, status),
           building_id = COALESCE($5, building_id)
       WHERE apartment_id = $6
       RETURNING *`,
      [apartment_number, floor, area, status, building_id, id]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: "Không tìm thấy phòng để sửa" });
    }

    res.json({ success: true, message: "Cập nhật thành công", data: result.rows[0] });

  } catch (err) {
    console.error("Lỗi sửa phòng:", err);
    res.status(500).json({ error: err.message });
  }
});

// 🗑️ 5. [DELETE] XÓA PHÒNG
router.delete("/delete/:id", verifySession, async (req, res) => {
  const { id } = req.params;
  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    // 1. Cập nhật bảng relationship: Set apartment_id = NULL cho tất cả cư dân trong phòng này
    // Đồng thời set is_head_of_household = FALSE (vì không còn phòng để làm chủ hộ)
    await client.query(
        `UPDATE relationship
         SET apartment_id = NULL, is_head_of_household = FALSE
         WHERE apartment_id = $1`,
         [id]
    );

    // 3. Tiến hành xóa phòng
    const result = await client.query("DELETE FROM apartment WHERE apartment_id = $1 RETURNING apartment_id", [id]);

    if (result.rows.length === 0) {
      await client.query("ROLLBACK");
      return res.status(404).json({ error: "Phòng không tồn tại" });
    }

    await client.query("COMMIT");
    res.json({ success: true, message: "Đã xóa phòng. Cư dân đã được chuyển sang danh sách 'Vô gia cư'." });

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("Lỗi xóa phòng:", err);
    res.status(500).json({ error: "Lỗi server: " + err.message });
  } finally {
    client.release();
  }
});

export default router;