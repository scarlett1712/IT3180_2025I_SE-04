import express from "express";
import {
  replyToFeedback,
  getRepliesByFeedbackId
} from "../controllers/feedbackReplyController.js";

const router = express.Router();

router.post("/:feedback_id/reply", replyToFeedback);
router.get("/:feedback_id/replies", getRepliesByFeedbackId);

export default router;