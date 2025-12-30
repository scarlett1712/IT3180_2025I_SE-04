package com.se_04.enoti.apartment;

import java.io.Serializable;

public class Apartment implements Serializable {
    private int apartment_id;
    private String apartment_number;
    private int floor;
    private double area;
    private String status;

    public Apartment(int apartment_id, String apartment_number, int floor, double area, String status) {
        this.apartment_id = apartment_id;
        this.apartment_number = apartment_number;
        this.floor = floor;
        this.area = area;
        this.status = status;
    }

    public int getId() { return apartment_id; }
    public String getApartmentNumber() { return apartment_number; }
    public int getFloor() { return floor; }
    public double getArea() { return area; }
    public String getStatus() { return status; }

    public void setApartmentNumber(String apartment_number) {
        this.apartment_number = apartment_number;
    }
    public void setFloor(int floor) {
        this.floor = floor;
    }
    public void setArea(double area) {
        this.area = area;
    }
}