package com.se_04.enoti.residents;

public class ResidentItem {
    private int id; // Corresponds to user_item_id in some contexts
    private final int userId;
    private final String name;
    private final String room;
    // Other fields can be kept for other parts of the app if needed
    private String gender;
    private String dob;
    private String email;
    private String phone;
    private String relationship;
    private String familyId;
    private boolean isLiving;

    /**
     * Full constructor for detailed views
     */
    public ResidentItem(int id, int userId, String name, String gender, String dob, String email, String phone, String relationship, String familyId, boolean isLiving, String room) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.gender = gender;
        this.dob = dob;
        this.email = email;
        this.phone = phone;
        this.relationship = relationship;
        this.familyId = familyId;
        this.isLiving = isLiving;
        this.room = room;
    }

    /**
     * A simpler constructor, specifically for the resident selection list in CreateFinanceActivity.
     * It only populates fields that are actually needed for that screen.
     */
    public ResidentItem(int userId, String name, String room) {
        this.userId = userId;
        this.name = name;
        this.room = room;
        // Set default values for other fields to prevent null pointer exceptions
        this.id = 0;
        this.gender = "";
        this.dob = "";
        this.email = "";
        this.phone = "";
        this.relationship = "";
        this.familyId = "";
        this.isLiving = true;
    }

    // --- Getters ---
    public int getId() { return id; }
    public int getUserId() { return userId; }
    public String getName() { return name; }
    public String getRoom() { return room; }
    public String getGender() { return gender; }
    public String getDob() { return dob; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getRelationship() { return relationship; }
    public String getFamilyId() { return familyId; }
    public boolean isLiving() { return isLiving; }

    public String getFloor() {
        if (room != null && room.length() > 1) {
            return room.substring(0, room.length() - 2);
        } else {
            return "N/A";
        }
    }
}
