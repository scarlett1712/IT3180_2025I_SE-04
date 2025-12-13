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

    public NotificationItem(long id, String title, String date, String expired_date,
                            String type, String sender, String content, boolean isRead) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.expired_date = expired_date;
        this.type = type;
        this.content = content;
        this.sender = sender;
        this.isRead = isRead;

        Log.d(TAG, "📦 Created NotificationItem: id=" + id + ", isRead=" + isRead);
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

    // 🔥 FIXED: Actually update the isRead field
    public void setRead(boolean read) {
        isRead = read;
    }

    @Override
    public String toString() {
        return "NotificationItem{id=" + id + ", title='" + title + "', isRead=" + isRead + "}";
    }
}