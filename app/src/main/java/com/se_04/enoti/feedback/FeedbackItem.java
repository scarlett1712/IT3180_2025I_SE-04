package com.se_04.enoti.feedback;

import java.io.Serializable;

public class FeedbackItem implements Serializable {
    private long feedbackId;
    private long notificationId;
    private long userId;
    private String content;
    private String fileUrl;
    private String status;
    private String createdAt;

    public FeedbackItem(long feedbackId, long notificationId, long userId,
                        String content, String fileUrl, String status, String createdAt) {
        this.feedbackId = feedbackId;
        this.notificationId = notificationId;
        this.userId = userId;
        this.content = content;
        this.fileUrl = fileUrl;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters & Setters
    public long getFeedbackId() { return feedbackId; }
    public void setFeedbackId(long feedbackId) { this.feedbackId = feedbackId; }

    public long getNotificationId() { return notificationId; }
    public void setNotificationId(long notificationId) { this.notificationId = notificationId; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    // Convenience helpers
    public boolean isRead() {
        return status != null && status.equalsIgnoreCase("read");
    }

    public boolean isPending() {
        return status != null && status.equalsIgnoreCase("pending");
    }
}
