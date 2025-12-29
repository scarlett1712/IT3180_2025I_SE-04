import express from "express";
import { pool } from "../db.js"; // Đảm bảo đường dẫn tới db.js đúng
import { verifySession } from "../middleware/authMiddleware.js"; // Middleware bảo mật

const router = express.Router();
const query = (text, params) => pool.query(text, params);

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

// ==================================================================
// 🗑️ 5. [DELETE] XÓA PHÒNG
// API: /api/apartments/delete/:id
// ==================================================================
router.delete("/delete/:id", verifySession, async (req, res) => {
  const { id } = req.params;

  try {
    // ⚠️ Kiểm tra xem phòng có đang có người ở không (bảng relationship/user_item)
    // Nếu có, chặn xóa để tránh lỗi dữ liệu mồ côi
    const checkOccupied = await query(
        `SELECT r.relationship_id
         FROM relationship r
         WHERE r.apartment_id = $1`,
         [id]
    );

    if (checkOccupied.rows.length > 0) {
        return res.status(400).json({
            error: "Không thể xóa phòng này vì đang có cư dân hoặc lịch sử thuê. Hãy xóa cư dân trước."
        });
    }

    const result = await query("DELETE FROM apartment WHERE apartment_id = $1 RETURNING apartment_id", [id]);

    if (result.rows.length === 0) {
      return res.status(404).json({ error: "Phòng không tồn tại" });
    }

    res.json({ success: true, message: "Đã xóa phòng thành công" });

  } catch (err) {
    console.error("Lỗi xóa phòng:", err);
    res.status(500).json({ error: "Lỗi server (Có thể do ràng buộc khóa ngoại)" });
  }
});

export default router;