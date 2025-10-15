package com.se_04.enoti.finance;

import javax.annotation.Nullable;

public class FinanceItem {
    private final String title;
    private final String date;
    private final String type;
    @Nullable
    private Long price;
    private final String sender;
    private boolean isPaid;

    public FinanceItem(String title, String date, String type, String sender){
        this.title = title;
        this.date = date;
        this.type = type;
        this.sender = sender;
        this.price = null;
        this.isPaid = false;
    }

    public FinanceItem(String title, String date, String type, String sender, long price) {
        this.title = title;
        this.date = date;
        this.type = type;
        this.sender = sender;
        this.price = price; // auto-box sang Long
        this.isPaid = false;
    }

    public String getTitle() { return title; }
    public String getDate() { return date; }
    public String getType() { return type; }

    @Nullable
    public Long getPrice() { return price; }
    public void setPrice(@Nullable long price) { this.price = price; }

    public String getSender() { return sender; }
    public boolean isPaid() { return isPaid; }
    public void setPaid(boolean paid) { isPaid = paid; }
}
