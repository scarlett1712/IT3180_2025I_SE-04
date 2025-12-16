package com.se_04.enoti.notification;

import android.util.Log;

public class NotificationItem {
    private static final String TAG = "NotificationItem";

    private final long id;
    private final String title;
    private final String date;      // raw date string (server)
    private final String expired_date;
    private final String type;
    private final String content;
    private final String sender;
    private boolean isRead; // 🔥 NOT final so it can be updated

    // 🔥 1. THÊM BIẾN MỚI CHO FILE
    private String fileUrl;
    private String fileType;

    // 🔥 2. CẬP NHẬT CONSTRUCTOR (Nhận thêm fileUrl, fileType)
    // Lưu ý: Bạn cần cập nhật nơi gọi new NotificationItem(...) (thường là trong Repository hoặc Fragment)
    // để truyền thêm 2 tham số này vào, hoặc dùng Setter bên dưới.
    public NotificationItem(long id, String title, String date, String expired_date,
                            String type, String sender, String content, boolean isRead,
                            String fileUrl, String fileType) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.expired_date = expired_date;
        this.type = type;
        this.content = content;
        this.sender = sender;
        this.isRead = isRead;
        this.fileUrl = fileUrl;
        this.fileType = fileType;

        Log.d(TAG, "📦 Created NotificationItem: id=" + id + ", hasFile=" + (fileUrl != null));
    }

    // Constructor cũ (để tránh lỗi code cũ, nhưng bạn nên dùng cái trên hoặc dùng Setter)
    public NotificationItem(long id, String title, String date, String expired_date,
                            String type, String sender, String content, boolean isRead) {
        this(id, title, date, expired_date, type, sender, content, isRead, null, null);
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDate() {
        return date;
    }

    public String getExpired_date() {
        return expired_date;
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getSender() {
        return sender;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    // 🔥 3. GETTER & SETTER CHO FILE (Quan trọng)
    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    @Override
    public String toString() {
        return "NotificationItem{id=" + id + ", title='" + title + "', isRead=" + isRead + ", fileType=" + fileType + "}";
    }
}