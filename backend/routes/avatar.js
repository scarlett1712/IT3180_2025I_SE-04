import express from "express";
import multer from "multer";
import path from "path";
import fs from "fs";
import { pool } from "../db.js";
import { fileURLToPath } from "url";
import { dirname } from "path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const router = express.Router();

// 🔧 Cấu hình multer cho upload ảnh
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    const uploadDir = "./public/uploads/avatars";
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
    }
    cb(null, uploadDir);
  },
  filename: function (req, file, cb) {
    const userId = req.body.userId || "unknown";
    const timestamp = Date.now();
    const fileExtension = path.extname(file.originalname);
    cb(null, `avatar_${userId}_${timestamp}${fileExtension}`);
  },
});

const upload = multer({
  storage: storage,
  limits: { fileSize: 5 * 1024 * 1024 },
  fileFilter: function (req, file, cb) {
    if (file.mimetype.startsWith("image/")) cb(null, true);
    else cb(new Error("Chỉ chấp nhận file ảnh (JPEG, PNG, etc.)!"), false);
  },
});

// 📤 POST /api/avatar/upload
router.post("/upload", upload.single("avatar"), async (req, res) => {
  try {
    if (!req.file)
      return res.status(400).json({ success: false, error: "Không có file được tải lên" });

    const { userId } = req.body;
    if (!userId) {
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ success: false, error: "Thiếu userId" });
    }

    const userCheck = await pool.query(
      "SELECT user_id FROM user_item WHERE user_id = $1",
      [userId]
    );
    if (userCheck.rows.length === 0) {
      fs.unlinkSync(req.file.path);
      return res.status(404).json({ success: false, error: "User không tồn tại" });
    }

    const avatarPath = `/uploads/avatars/${req.file.filename}`;
    const oldAvatarResult = await pool.query(
      "SELECT avatar_path FROM user_item WHERE user_id = $1",
      [userId]
    );

    if (oldAvatarResult.rows.length > 0 && oldAvatarResult.rows[0].avatar_path) {
      const oldAvatarPath = path.join(__dirname, "../public", oldAvatarResult.rows[0].avatar_path);
      if (fs.existsSync(oldAvatarPath)) fs.unlinkSync(oldAvatarPath);
    }

    const result = await pool.query(
      "UPDATE user_item SET avatar_path = $1 WHERE user_id = $2 RETURNING user_id, full_name, avatar_path",
      [avatarPath, userId]
    );

    res.json({
      success: true,
      message: "Upload avatar thành công!",
      avatarUrl: avatarPath,
      user: result.rows[0],
    });
  } catch (error) {
    console.error("💥 Error uploading avatar:", error);
    if (req.file && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
    res.status(500).json({
      success: false,
      error: "Lỗi server khi upload avatar: " + error.message,
    });
  }
});

// ... (các route GET/DELETE phía dưới giữ nguyên)

export default router;
