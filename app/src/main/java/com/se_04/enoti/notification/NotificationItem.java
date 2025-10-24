package com.se_04.enoti.notification;

public class NotificationItem {
    private final long id;
    private final String title;
    private final String date;      // raw date string (server)
    private final String type;
    private final String content;
    private final String sender;
    private boolean isRead;

    public NotificationItem(long id, String title, String date, String type, String sender, String content, boolean isRead) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.type = type;
        this.content = content;
        this.sender = sender;
        this.isRead = isRead;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getDate() { return date; }
    public String getType() { return type; }
    public String getContent() { return content; }
    public String getSender() { return sender; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}