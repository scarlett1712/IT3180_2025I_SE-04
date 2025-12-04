package com.se_04.enoti.residents;

public class ResidentItem {
    private int id;
    private final int userId;
    private final String name;
    private final String room;

    private String gender;
    private String dob;
    private String email;
    private String phone;
    private String relationship;
    private String familyId;
    private boolean isLiving;

    // 🔥 Thêm 2 trường mới
    private String identityCard;
    private String homeTown;

    /**
     * Constructor đầy đủ (Đã cập nhật)
     */
    public ResidentItem(int id, int userId, String name, String gender, String dob, String email, String phone,
                        String relationship, String familyId, boolean isLiving, String room,
                        String identityCard, String homeTown) { // 🔥 Thêm tham số vào constructor
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
        this.identityCard = identityCard;
        this.homeTown = homeTown;
    }

    /**
     * Constructor rút gọn (Giữ nguyên để tránh lỗi ở các chỗ chưa cần update)
     */
    public ResidentItem(int userId, String name, String room) {
        this.userId = userId;
        this.name = name;
        this.room = room;
        this.id = 0;
        this.gender = "";
        this.dob = "";
        this.email = "";
        this.phone = "";
        this.relationship = "";
        this.familyId = "";
        this.isLiving = true;
        this.identityCard = "";
        this.homeTown = "";
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

    // 🔥 Getters mới
    public String getIdentityCard() { return identityCard; }
    public String getHomeTown() { return homeTown; }

    public String getFloor() {
        if (room != null && room.length() > 1) {
            return room.substring(0, room.length() - 2);
        } else {
            return "N/A";
        }
    }
}