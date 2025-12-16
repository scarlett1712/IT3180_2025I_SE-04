package com.se_04.enoti.authority;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class AuthorityMessage implements Serializable {

    @SerializedName("id")
    private int id;

    @SerializedName("title")
    private String title;

    @SerializedName("content")
    private String content;

    @SerializedName("category")
    private String category;

    @SerializedName("priority")
    private String priority;

    @SerializedName("sender_name")
    private String senderName;

    @SerializedName("attachment_url")
    private String attachmentUrl;

    @SerializedName("receiver_admin_id")
    private int receiverAdminId;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("is_read")
    private int isRead; // 0 for false, 1 for true

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
    }

    public int getReceiverAdminId() {
        return receiverAdminId;
    }

    public void setReceiverAdminId(int receiverAdminId) {
        this.receiverAdminId = receiverAdminId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return isRead == 1;
    }

    public void setRead(boolean read) {
        this.isRead = read ? 1 : 0;
    }

    public boolean isUrgent() {
        return "Urgent".equalsIgnoreCase(priority);
    }
}
