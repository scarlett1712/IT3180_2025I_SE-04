package com.se_04.enoti.models;

public class Notification {
    private int notification_id;
    private String title;
    private String content;
    private String created_at;
    private String expired_date;
    private String type;
    private String status;
    private String created_by;

    // 🔹 Trạng thái riêng của từng người dùng
    private boolean is_read;

    public int getNotification_id() {
        return notification_id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getCreated_at() {
        return created_at;
    }

    public boolean isRead() {
        return is_read;
    }

    public void setRead(boolean isRead) {
        this.is_read = isRead;
    }
}
