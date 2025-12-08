package com.se_04.enoti.finance.admin;

public class UtilityInputItem {
    private String roomNumber;
    private String oldIndex = "";
    private String newIndex = "";

    public UtilityInputItem(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getRoomNumber() { return roomNumber; }
    public String getOldIndex() { return oldIndex; }
    public void setOldIndex(String oldIndex) { this.oldIndex = oldIndex; }
    public String getNewIndex() { return newIndex; }
    public void setNewIndex(String newIndex) { this.newIndex = newIndex; }
}