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

    // 🔥 THAY ĐỔI 1: Xóa isPaid và thay bằng status để đồng bộ với API
    private String status;
    private String room; // Thêm trường room để xử lý nhóm

    // 🧮 Dành cho admin thống kê theo PHÒNG
    private int paidRooms;     // Số phòng đã thanh toán
    private int totalRooms;    // Tổng số phòng áp dụng

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
        this.status = "chua_thanh_toan"; // Mặc định là chưa thanh toán
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

    // 🔥 THAY ĐỔI 2: Getters & Setters cho Room
    public int getTotalRooms() { return totalRooms; }
    public void setTotalRooms(int totalRooms) { this.totalRooms = totalRooms; }

    public int getPaidRooms() { return paidRooms; }
    public void setPaidRooms(int paidRooms) { this.paidRooms = paidRooms; }

    // --- Debug / Log tiện dụng ---
    @Override
    public String toString() {
        return "FinanceItem{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", price=" + price +
                ", paidRooms=" + paidRooms +
                ", totalRooms=" + totalRooms +
                '}';
    }
}
