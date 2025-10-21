package com.se_04.enoti.residents;

public class ResidentItem {
    private String name;
    private String floor;
    private String room;
    private String gender;
    private String dob;
    private String email;
    private String phone;
    private String relationship;
    private String role;
    private String familyId;
    private boolean isLiving;

    public ResidentItem(String name, String floor, String room) {
        this(name, floor, room, "Nam", "01/01/1990", "example@gmail.com",
                "0123456789", "Chủ hộ", "Cư dân", "HGD01", true);
    }

    public ResidentItem(String name, String floor, String room, String gender, String dob, String email,
                        String phone, String relationship, String role, String familyId, boolean isLiving) {
        this.name = name;
        this.floor = floor;
        this.room = room;
        this.gender = gender;
        this.dob = dob;
        this.email = email;
        this.phone = phone;
        this.relationship = relationship;
        this.role = role;
        this.familyId = familyId;
        this.isLiving = isLiving;
    }

    // Getter
    public String getName() { return name; }
    public String getFloor() { return floor; }
    public String getRoom() { return room; }
    public String getGender() { return gender; }
    public String getDob() { return dob; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getRelationship() { return relationship; }
    public String getRole() { return role; }
    public String getFamilyId() { return familyId; }
    public boolean isLiving() { return isLiving; }
}
