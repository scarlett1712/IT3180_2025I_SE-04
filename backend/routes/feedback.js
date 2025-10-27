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

export default router;
