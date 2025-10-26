import express from "express";
import {
  createFeedback,
  getAllFeedback,
  markAsRead
} from "../controllers/feedbackController.js";

const router = express.Router();

router.post("/", createFeedback);
router.get("/", getAllFeedback);
router.put("/:feedback_id/read", markAsRead);

export default router;
