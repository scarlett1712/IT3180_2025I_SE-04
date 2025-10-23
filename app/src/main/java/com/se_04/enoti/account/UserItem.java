package com.se_04.enoti.account;

public class UserItem {

    private final String id;
    private final String familyID;
    private final String email;
    private final String name;
    private final String dob;
    private final Gender gender;
    private final String relationship;
    private final int room;
    private final boolean isLiving;
    private final Role role;
    private final String phone;

    public UserItem(String id, String familyID, String email, String name, String dob, Gender gender, String relationship, int room, Role role, String phone) {
        this.id = id;
        this.familyID = familyID;
        this.email = email;
        this.name = name;
        this.dob = dob;
        this.gender = gender;
        this.relationship = relationship;
        this.room = room;
        this.role = role;
        this.isLiving = true;
        this.phone = phone;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }

    public String getName() { return name; }
    public String getDob() { return dob; }
    public Gender getGender() { return gender; }
    public String getRelationship() { return relationship; }
    public int getRoom() { return room; }
    public Role getRole() { return role; }
    public boolean getIsLiving() { return isLiving; }
    public String getPhone() { return phone; }
    public String getFamilyId() { return familyID; }
}

