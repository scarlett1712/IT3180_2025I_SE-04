package com.se_04.enoti.finance.admin;

public class RateItem {
    private String name;
    private int min;
    private Integer max; // Có thể null (vô cùng)
    private double price;

    public RateItem(String name, int min, Integer max, double price) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.price = price;
    }

    // Getters & Setters
    public String getName() { return name; }
    public int getMin() { return min; }
    public Integer getMax() { return max; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}