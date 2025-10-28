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

    public UserItem(String id, @Nullable String familyId, String email, String name, String dob, Gender gender, String relationship, int room, Role role, String phone) {
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
    }

    /**
     * [FIX] Creates a UserItem object from a JSON object received from the login API.
     * This method centralizes parsing logic and resolves the "cannot find symbol" error.
     * @param userJson The JSONObject for the "user" key.
     * @return A new UserItem instance.
     * @throws Exception if parsing fails.
     */
    public static UserItem fromJson(JSONObject userJson) throws Exception {
        String id = userJson.getString("id");
        String phone = userJson.getString("phone");
        String roleStr = userJson.getString("role");
        String name = userJson.getString("name");
        String genderStr = userJson.optString("gender", "Khác");
        String dob = userJson.optString("dob", "01-01-2000");
        String roomStr = userJson.optString("room", "0");
        String relationship = userJson.optString("relationship", "Thành viên");
        String email = userJson.optString("email", "");

        // --- Xử lý Role ---
        Role role;
        try {
            role = Role.valueOf(roleStr.trim().toUpperCase());
        } catch (Exception e) {
            role = Role.USER; // default
        }

        // --- Xử lý Gender (chuyển từ "Nam"/"Nữ"/"Khác" sang enum) ---
        Gender gender;
        switch (genderStr.trim().toLowerCase()) {
            case "nam":
                gender = Gender.MALE;
                break;
            case "nữ":
            case "nu":
                gender = Gender.FEMALE;
                break;
            default:
                gender = Gender.OTHER;
                break;
        }

        // --- Xử lý room ---
        int room;
        try {
            room = Integer.parseInt(roomStr);
        } catch (NumberFormatException e) {
            room = 0;
        }

        return new UserItem(id, null, email, name, dob, gender, relationship, room, role, phone);
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
}
