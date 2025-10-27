const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { pool } = require('../db.js');

const router = express.Router();

// 🔧 Cấu hình multer cho upload ảnh
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    const uploadDir = './public/uploads/avatars';
    // Tạo thư mục nếu chưa tồn tại
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
    }
    cb(null, uploadDir);
  },
  filename: function (req, file, cb) {
    const userId = req.body.userId || 'unknown';
    const timestamp = Date.now();
    const fileExtension = path.extname(file.originalname);
    cb(null, `avatar_${userId}_${timestamp}${fileExtension}`);
  }
});

const upload = multer({
  storage: storage,
  limits: {
    fileSize: 5 * 1024 * 1024 // 5MB limit
  },
  fileFilter: function (req, file, cb) {
    // Chỉ chấp nhận file ảnh
    if (file.mimetype.startsWith('image/')) {
      cb(null, true);
    } else {
      cb(new Error('Chỉ chấp nhận file ảnh (JPEG, PNG, etc.)!'), false);
    }
  }
});

// 📤 POST /api/avatar/upload - Upload avatar mới
router.post('/upload', upload.single('avatar'), async (req, res) => {
  try {
    // Kiểm tra có file được upload không
    if (!req.file) {
      return res.status(400).json({
        success: false,
        error: 'Không có file được tải lên'
      });
    }

    const { userId } = req.body;

    // Kiểm tra userId
    if (!userId) {
      // Xóa file vừa upload nếu không có userId
      fs.unlinkSync(req.file.path);
      return res.status(400).json({
        success: false,
        error: 'Thiếu userId'
      });
    }

    // Kiểm tra user có tồn tại không
    const userCheck = await pool.query(
      'SELECT user_id FROM user_item WHERE user_id = $1',
      [userId]
    );

    if (userCheck.rows.length === 0) {
      fs.unlinkSync(req.file.path);
      return res.status(404).json({
        success: false,
        error: 'User không tồn tại'
      });
    }

    // Đường dẫn avatar để lưu trong database
    const avatarPath = `/uploads/avatars/${req.file.filename}`;

    // Xóa avatar cũ nếu có
    const oldAvatarResult = await pool.query(
      'SELECT avatar_path FROM user_item WHERE user_id = $1',
      [userId]
    );

    if (oldAvatarResult.rows.length > 0 && oldAvatarResult.rows[0].avatar_path) {
      const oldAvatarPath = path.join(__dirname, '../public', oldAvatarResult.rows[0].avatar_path);
      if (fs.existsSync(oldAvatarPath)) {
        fs.unlinkSync(oldAvatarPath);
      }
    }

    // Cập nhật avatar_path trong database
    const result = await pool.query(
      'UPDATE user_item SET avatar_path = $1 WHERE user_id = $2 RETURNING user_id, full_name, avatar_path',
      [avatarPath, userId]
    );

    res.json({
      success: true,
      message: 'Upload avatar thành công!',
      avatarUrl: avatarPath,
      user: result.rows[0]
    });

  } catch (error) {
    console.error('💥 Error uploading avatar:', error);

    // Xóa file nếu có lỗi
    if (req.file && fs.existsSync(req.file.path)) {
      fs.unlinkSync(req.file.path);
    }

    res.status(500).json({
      success: false,
      error: 'Lỗi server khi upload avatar: ' + error.message
    });
  }
});

// 📥 GET /api/avatar/:userId - Lấy avatar theo userId
router.get('/:userId', async (req, res) => {
  try {
    const { userId } = req.params;

    const result = await pool.query(
      'SELECT avatar_path FROM user_item WHERE user_id = $1',
      [userId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({
        success: false,
        error: 'User không tồn tại'
      });
    }

    if (!result.rows[0].avatar_path) {
      return res.status(404).json({
        success: false,
        error: 'User chưa có avatar'
      });
    }

    const avatarPath = path.join(__dirname, '../public', result.rows[0].avatar_path);

    // Kiểm tra file có tồn tại không
    if (!fs.existsSync(avatarPath)) {
      return res.status(404).json({
        success: false,
        error: 'File avatar không tồn tại'
      });
    }

    // Trả về file ảnh
    res.sendFile(avatarPath);

  } catch (error) {
    console.error('💥 Error getting avatar:', error);
    res.status(500).json({
      success: false,
      error: 'Lỗi server khi lấy avatar: ' + error.message
    });
  }
});

// 🗑️ DELETE /api/avatar/:userId - Xóa avatar
router.delete('/:userId', async (req, res) => {
  try {
    const { userId } = req.params;

    // Lấy đường dẫn avatar hiện tại
    const result = await pool.query(
      'SELECT avatar_path FROM user_item WHERE user_id = $1',
      [userId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({
        success: false,
        error: 'User không tồn tại'
      });
    }

    let deleteMessage = 'Không có avatar để xóa';

    if (result.rows[0].avatar_path) {
      const oldAvatarPath = path.join(__dirname, '../public', result.rows[0].avatar_path);

      // Xóa file vật lý
      if (fs.existsSync(oldAvatarPath)) {
        fs.unlinkSync(oldAvatarPath);
        deleteMessage = 'Xóa avatar thành công';
      }
    }

    // Cập nhật database - set avatar_path thành null
    await pool.query(
      'UPDATE user_item SET avatar_path = NULL WHERE user_id = $1',
      [userId]
    );

    res.json({
      success: true,
      message: deleteMessage
    });

  } catch (error) {
    console.error('💥 Error deleting avatar:', error);
    res.status(500).json({
      success: false,
      error: 'Lỗi server khi xóa avatar: ' + error.message
    });
  }
});

// 🔄 GET /api/avatar/user/:userId - Lấy thông tin avatar (URL)
router.get('/user/:userId', async (req, res) => {
  try {
    const { userId } = req.params;

    const result = await pool.query(
      'SELECT user_id, full_name, avatar_path FROM user_item WHERE user_id = $1',
      [userId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({
        success: false,
        error: 'User không tồn tại'
      });
    }

    const user = result.rows[0];

    res.json({
      success: true,
      user: {
        userId: user.user_id,
        fullName: user.full_name,
        avatarUrl: user.avatar_path,
        hasAvatar: !!user.avatar_path
      }
    });

  } catch (error) {
    console.error('💥 Error getting avatar info:', error);
    res.status(500).json({
      success: false,
      error: 'Lỗi server khi lấy thông tin avatar: ' + error.message
    });
  }
});

export default router;