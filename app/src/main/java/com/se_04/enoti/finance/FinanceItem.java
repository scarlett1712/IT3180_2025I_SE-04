package com.se_04.enoti.finance;

import javax.annotation.Nullable;

public class FinanceItem {
    private int id;
    private final String title;
    private final String content;
    private final String date;
    private final String type;
    @Nullable
    private Long price;
    private final String sender;
    private boolean isPaid;

    /**
     * The single, official constructor for creating a FinanceItem.
     * This ensures consistency across the entire application.
     */
    public FinanceItem(String title, String content, String date, String type, String sender, @Nullable Long price) {
        this.title = title;
        this.content = (content == null) ? "" : content; // Prevent null content
        this.date = date;
        this.type = type;
        this.sender = sender;
        this.price = price;
        this.isPaid = false; // Default value
    }

    // --- Getters and Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getDate() { return date; }
    public String getType() { return type; }
    public String getSender() { return sender; }

    @Nullable
    public Long getPrice() { return price; }
    public void setPrice(@Nullable Long price) { this.price = price; }

    public boolean isPaid() { return isPaid; }
    public void setPaid(boolean paid) { this.isPaid = paid; }
}
