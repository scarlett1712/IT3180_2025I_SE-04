package com.se_04.enoti.finance;

import androidx.annotation.Nullable;

public class FinanceItem {

    private int id;
    private String title;
    private String content;
    private String date;
    private String type;
    private String sender;
    @Nullable
    private Long price;
    private boolean isPaid;

    // 🧮 Dành cho admin thống kê
    private int totalUsers;   // Tổng cư dân trong bill
    private int paidUsers;    // Số người đã thanh toán
    private int unpaidUsers;  // Số người chưa thanh toán

    // 🔹 Constructor trống (cần thiết cho khi parse từ JSON)
    public FinanceItem() {}

    // 🔹 Constructor đầy đủ (dùng khi tạo thủ công)
    public FinanceItem(String title, String content, String date, String type, String sender, @Nullable Long price) {
        this.title = (title != null) ? title : "Không rõ";
        this.content = (content != null) ? content : "";
        this.date = (date != null) ? date : "";
        this.type = (type != null) ? type : "Khác";
        this.sender = (sender != null) ? sender : "Ban quản lý";
        this.price = price;
        this.isPaid = false;
    }

    // --- GETTERS & SETTERS ---

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    @Nullable
    public Long getPrice() { return price; }
    public void setPrice(@Nullable Long price) { this.price = price; }

    public boolean isPaid() { return isPaid; }
    public void setPaid(boolean paid) { this.isPaid = paid; }

    public int getTotalUsers() { return totalUsers; }
    public void setTotalUsers(int totalUsers) { this.totalUsers = totalUsers; }

    public int getPaidUsers() { return paidUsers; }
    public void setPaidUsers(int paidUsers) { this.paidUsers = paidUsers; }

    public int getUnpaidUsers() { return unpaidUsers; }
    public void setUnpaidUsers(int unpaidUsers) { this.unpaidUsers = unpaidUsers; }

    // ✅ Tính tự động số chưa thanh toán (nếu có dữ liệu)
    public void calculateUnpaidUsers() {
        if (totalUsers > 0 && paidUsers >= 0) {
            this.unpaidUsers = Math.max(totalUsers - paidUsers, 0);
        }
    }

    // --- Debug / Log tiện dụng ---
    @Override
    public String toString() {
        return "FinanceItem{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", price=" + price +
                ", paidUsers=" + paidUsers +
                ", totalUsers=" + totalUsers +
                '}';
    }
}