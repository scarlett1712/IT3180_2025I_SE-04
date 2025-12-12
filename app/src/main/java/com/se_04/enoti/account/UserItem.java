package com.se_04.enoti.account;

import org.json.JSONObject;

import javax.annotation.Nullable;

public class UserItem {
    private final String id;
    private final String familyId;
    private final String email;
    private final String name;
    private final String dob;
    private final Gender gender;
    private final String relationship;
    private final int room;
    private final Role role;
    private final String phone;
    private final String identityCard;
    private final String homeTown;

    public UserItem(String id, @Nullable String familyId, String email, String name, String dob,
                    Gender gender, String relationship, int room, Role role, String phone,
                    String identityCard, String homeTown) {
        this.id = id;
        this.familyId = familyId;
        this.email = email;
        this.name = name;
        this.dob = dob;
        this.gender = gender;
        this.relationship = relationship;
        this.room = room;
        this.role = role;
        this.phone = phone;
        this.identityCard = identityCard;
        this.homeTown = homeTown;
    }

    public static UserItem fromJson(JSONObject userJson) throws Exception {
        String id = userJson.optString("id");
        String phone = userJson.optString("phone");
        String name = userJson.optString("name");
        String genderStr = userJson.optString("gender", "Khác");
        String dob = userJson.optString("dob", "01-01-2000");
        String roomStr = userJson.optString("room", "0");
        String relationship = userJson.optString("relationship", "Thành viên");
        String email = userJson.optString("email", "");
        String identityCard = userJson.optString("identity_card", "");
        String homeTown = userJson.optString("home_town", "");

        // --- 🔥 LOGIC XỬ LÝ VAI TRÒ MỚI, ĐÁNG TIN CẬY HƠN ---
        int roleId = userJson.optInt("role_id", 1); // Mặc định là 1 (USER)
        Role role;
        switch (roleId) {
            case 2:
                role = Role.ADMIN;
                break;
            case 3:
                role = Role.ACCOUNTANT;
                break;
            case 4:
                role = Role.AGENCY;
                break;
            case 1: // Dành cho người dùng thông thường
            default: // Bất kỳ role_id nào khác không xác định cũng sẽ là USER
                role = Role.USER;
                break;
        }
        // -------------------------------------------------------------

        // --- Xử lý Gender ---
        Gender gender;
        switch (genderStr.trim().toLowerCase()) {
            case "nam": gender = Gender.MALE; break;
            case "nữ":
            case "nu": gender = Gender.FEMALE; break;
            default: gender = Gender.OTHER; break;
        }

        // --- Xử lý room ---
        int room;
        try {
            room = Integer.parseInt(roomStr);
        } catch (NumberFormatException e) {
            room = 0;
        }

        return new UserItem(id, null, email, name, dob, gender, relationship, room, role, phone, identityCard, homeTown);
    }

    // --- Getters ---
    public String getId() { return id; }
    public String getFamilyId() { return familyId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getDob() { return dob; }
    public Gender getGender() { return gender; }
    public String getRelationship() { return relationship; }
    public int getRoom() { return room; }
    public Role getRole() { return role; }
    public String getPhone() { return phone; }
    public String getIdentityCard() { return identityCard; }
    public String getHomeTown() { return homeTown; }
}