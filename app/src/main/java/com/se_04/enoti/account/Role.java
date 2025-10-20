package com.se_04.enoti.account;

public enum Role {
    Admin("Quản trị viên"),
    User("Người dùng");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}