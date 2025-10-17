package com.se_04.enoti.notification;

public class NotificationItem {
    private final String title;
    private final String date;
    private final String content;
    private final String sender;
    private boolean isRead;

    public NotificationItem(String title, String date, String sender, String content) {
        this.title = title;
        this.date = date;
        this.content = content;
        this.sender = sender;
        this.isRead = false;

    }

    public String getTitle() { return title; }
    public String getDate() { return date; }
    public String getContent() { return content; }
    public String getSender() { return sender; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

}