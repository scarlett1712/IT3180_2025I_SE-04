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
    private Long price; // Đây là "Định mức" hoặc "Số tiền gốc"

    // 🔥 Trường Status thay cho boolean isPaid
    private String status;
    private String room;

    // 🧮 Dành cho admin thống kê
    private int paidRooms;     // Số phòng đã thanh toán
    private int totalRooms;    // Tổng số phòng áp dụng

    // 🔥 MỚI: Số tiền thực tế thu được (từ bảng Invoice)
    // Dùng để xử lý trường hợp khoản thu tự nguyện (price = null/0 nhưng thực thu > 0)
    private double realRevenue;

    // 🔹 Constructor trống
    public FinanceItem() {}

    // 🔹 Constructor đầy đủ
    public FinanceItem(String title, String content, String date, String type, String sender, @Nullable Long price) {
        this.title = (title != null) ? title : "Không rõ";
        this.content = (content != null) ? content : "";
        this.date = (date != null) ? date : "";
        this.type = (type != null) ? type : "Khác";
        this.sender = (sender != null) ? sender : "Ban quản lý";
        this.price = price;
        this.status = "chua_thanh_toan";
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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public int getTotalRooms() { return totalRooms; }
    public void setTotalRooms(int totalRooms) { this.totalRooms = totalRooms; }

    public int getPaidRooms() { return paidRooms; }
    public void setPaidRooms(int paidRooms) { this.paidRooms = paidRooms; }

    // 🔥 GETTER & SETTER CHO REAL REVENUE (MỚI)
    public double getRealRevenue() {
        return realRevenue;
    }

    public void setRealRevenue(double realRevenue) {
        this.realRevenue = realRevenue;
    }

    @Override
    public String toString() {
        return "FinanceItem{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", price=" + price +
                ", realRevenue=" + realRevenue +
                ", paidRooms=" + paidRooms +
                ", totalRooms=" + totalRooms +
                '}';
    }
}