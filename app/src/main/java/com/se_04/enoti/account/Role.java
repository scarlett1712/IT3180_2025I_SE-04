package com.se_04.enoti.account;

public enum Role {
    ADMIN("Quản trị viên"),
    USER("Người dùng"),
    ACCOUNTANT("Kế toán"),
    AGENCY("Cơ quan chức năng");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}