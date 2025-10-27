import express from "express";
import {
  createFeedback,
  getAllFeedback,
  getFeedbackByUser,
  markAsRead
} from "../controllers/feedbackController.js";

const router = express.Router();

router.post("/", createFeedback);
router.get("/", getAllFeedback);
router.get("/user/:userId", getFeedbackByUser);
router.put("/:feedback_id/read", markAsRead);

// 🟡 Lấy danh sách feedback theo notification
router.get("/notification/:notification_id", async (req, res) => {
  const { notification_id } = req.params;
  const result = await pool.query(
    `SELECT f.feedback_id, f.content, f.created_at, ui.full_name
     FROM feedback f
     LEFT JOIN user_item ui ON f.user_id = ui.user_id
     WHERE f.notification_id = $1
     ORDER BY f.created_at DESC`,
    [notification_id]
  );
  res.json(result.rows);
});

export default router;
