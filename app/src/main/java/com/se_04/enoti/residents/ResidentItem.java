package com.se_04.enoti.residents;

public class ResidentItem {
    private int userItemId;
    private int userId;
    private String name;
    private String gender;
    private String dob;
    private String email;
    private String phone;
    private String relationship;
    private String familyId;
    private boolean isLiving;
    private String apartmentId;

    public ResidentItem(int userItemId, int userId, String name, String gender, String dob,
                        String email, String phone, String relationship,
                        String familyId, boolean isLiving, String apartmentId) {
        this.userItemId = userItemId;
        this.userId = userId;
        this.name = name;
        this.gender = gender;
        this.dob = dob;
        this.email = email;
        this.phone = phone;
        this.relationship = relationship;
        this.familyId = familyId;
        this.isLiving = isLiving;
        this.apartmentId = apartmentId;
    }

    public int getUserItemId() { return userItemId; }
    public int getUserId() { return userId; }
    public String getName() { return name; }
    public String getGender() { return gender; }
    public String getDob() { return dob; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getRelationship() { return relationship; }
    public String getFamilyId() { return familyId; }
    public boolean isLiving() { return isLiving; }
    public String getApartmentId() { return apartmentId; }

    public String getFloor() {
        if (apartmentId == null || apartmentId.isEmpty()) {
            return "Không rõ";
        }

        try {
            // Loại bỏ ký tự không phải số
            String digitsOnly = apartmentId.replaceAll("\\D+", "");
            if (digitsOnly.length() >= 3) {
                // Lấy tất cả trừ 2 số cuối (VD: 101 -> 1, 1005 -> 10)
                String floorNumber = digitsOnly.substring(0, digitsOnly.length() - 2);
                return "Tầng " + Integer.parseInt(floorNumber);
            } else if (digitsOnly.length() == 2) {
                // Nếu chỉ có 2 số (VD: 05 -> tầng 0)
                return "Tầng " + digitsOnly.charAt(0);
            } else {
                return "Không rõ";
            }
        } catch (Exception e) {
            return "Không rõ";
        }
    }

    public String getRoom() {
        return (apartmentId != null) ? "Phòng " + apartmentId : "Không rõ";
    }
}
