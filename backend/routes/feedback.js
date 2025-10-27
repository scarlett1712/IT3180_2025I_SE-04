import express from "express";
import {
  createFeedback,
  getAllFeedback,
  getFeedbackByUser,
  markAsRead,
  getFeedbackByNotification, // ✅ thêm
} from "../controllers/feedbackController.js";

const router = express.Router();

router.post("/", createFeedback);
router.get("/", getAllFeedback);
router.get("/user/:userId", getFeedbackByUser);
router.put("/:feedback_id/read", markAsRead);

// ✅ Admin lấy danh sách feedback của 1 thông báo
router.get("/notification/:notification_id", getFeedbackByNotification);

export default router;
