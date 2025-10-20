package com.se_04.enoti.notification;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationItem {
    private final String title;
    private final String date;
    private final String type;
    private final String content;
    private final String sender;
    private boolean isRead;

    public NotificationItem(String title, String date, String type, String sender, String content) {
        this.title = title;
        this.date = date;
        this.type = type;
        this.content = content;
        this.sender = sender;
        this.isRead = false;

    }

    public String getTitle() { return title; }
    public String getDate() { return date; }
    public String getType() { return type; }
    public String getContent() { return content; }
    public String getSender() { return sender; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public long getTimestamp() {
        if (date == null || date.isEmpty()) return 0;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        try {
            String targetDate = date;
            // Nếu có khoảng ngày, lấy ngày cuối
            if (date.contains("-")) {
                String[] parts = date.split("-");
                targetDate = parts[parts.length - 1].trim();
            }

            Date parsedDate = sdf.parse(targetDate);
            return parsedDate != null ? parsedDate.getTime() : 0;

        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
    }
}