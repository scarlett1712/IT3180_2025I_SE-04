package com.se_04.enoti.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.se_04.enoti.account.Gender;
import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;

public class UserManager {
    private static final String PREF_NAME = "UserPrefs";
    private static UserManager instance;
    private final SharedPreferences sharedPreferences;

    private UserManager(Context context) {
        sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized UserManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserManager(context);
        }
        return instance;
    }

    // 🔹 Lưu thông tin user đầy đủ
    public void saveCurrentUser(UserItem user) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("id", user.getId());
        editor.putString("familyId", user.getFamilyId());
        editor.putString("email", user.getEmail());
        editor.putString("name", user.getName());
        editor.putString("dob", user.getDob());
        editor.putString("gender", user.getGender().name());
        editor.putString("relationship", user.getRelationship());
        editor.putString("phone", user.getPhone());
        editor.putString("role", user.getRole().name());
        editor.apply();

        android.util.Log.d("USER_MANAGER", "✅ Saved user role: " + user.getRole().name());
    }

    // 🔹 Lấy thông tin user
    public UserItem getCurrentUser() {
        if (!sharedPreferences.contains("id")) return null;
        return new UserItem(
                sharedPreferences.getString("id", ""),
                sharedPreferences.getString("familyId", ""),
                sharedPreferences.getString("email", ""),
                sharedPreferences.getString("name", ""),
                sharedPreferences.getString("dob", ""),
                Gender.valueOf(sharedPreferences.getString("gender", Gender.MALE.name())),
                sharedPreferences.getString("relationship", ""),
                Role.valueOf(sharedPreferences.getString("role", Role.USER.name()).toUpperCase()),
                sharedPreferences.getString("phone", "")
        );
    }

    // 🔹 Xóa user (khi đăng xuất)
    public void clearUser() {
        sharedPreferences.edit().clear().apply();
    }

    public String getUsername() {
        UserItem user = getCurrentUser();
        return user != null ? user.getName() : null;
    }

    public void setLoggedIn(boolean loggedIn) {
        sharedPreferences.edit().putBoolean("isLoggedIn", loggedIn).apply();
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean("isLoggedIn", false);
    }

}
