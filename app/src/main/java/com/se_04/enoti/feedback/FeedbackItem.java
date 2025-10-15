package com.se_04.enoti.feedback;

public class FeedbackItem{

    private final String title;
    private final String type;
    private final String date;
    private final String content;
    private final String repliedNotification;
    private boolean isSent;
    private boolean isRead;
    private boolean isReplied;


    public FeedbackItem(String title, String type, String date, String content, String repliedNotification) {
        this.title = title;
        this.type = type;
        this.date = date;
        this.content = content;
        this.repliedNotification = repliedNotification;
        this.isSent = false;
        this.isRead = false;
        this.isReplied = false;
    }

    public String getTitle() { return title; }
    public String getType() { return type; }
    public String getDate() { return date; }
    public String getContent() { return content; }
    public String getRepliedNotification() { return repliedNotification; }
    public boolean isSent() { return isSent; }
    public boolean isRead() { return isRead; }
    public boolean isReplied() { return isReplied; }
    public void setSent(boolean sent) { isSent = sent; }
    public void setRead(boolean read) { isRead = read; }
    public void setReplied(boolean replied) { isReplied = replied; }



}