package com.se_04.enoti.residents;

public class ResidentItem {
    private String name;
    private String floor;
    private String room;

    public ResidentItem(String name, String floor, String room) {
        this.name = name;
        this.floor = floor;
        this.room = room;
    }

    public String getName() { return name; }
    public String getFloor() { return floor; }
    public String getRoom() { return room; }
}
