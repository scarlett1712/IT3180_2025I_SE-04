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

    public void saveCurrentUser(UserItem user) {
        if (user == null) {
            android.util.Log.e("USER_MANAGER", "⚠️ User is null, cannot save!");
            return;
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("id", user.getId());
        editor.putString("familyId", user.getFamilyId());
        editor.putString("email", user.getEmail());
        editor.putString("name", user.getName());
        editor.putString("dob", user.getDob());
        editor.putString("gender", user.getGender().name());
        editor.putString("relationship_with_the_head_of_household", user.getRelationship());
        editor.putInt("apartment_number", user.getRoom());
        editor.putString("phone", user.getPhone());
        editor.putString("role", user.getRole().name());
        editor.apply();

        android.util.Log.d("USER_MANAGER", "✅ Saved user: " + user.getName() + " | Role: " + user.getRole());
    }

    public UserItem getCurrentUser() {
        if (!sharedPreferences.contains("id")) {
            android.util.Log.w("USER_MANAGER", "⚠️ No user found in SharedPreferences");
            return null;
        }

        return new UserItem(
                sharedPreferences.getString("id", ""),
                sharedPreferences.getString("familyId", ""),
                sharedPreferences.getString("email", ""),
                sharedPreferences.getString("name", ""),
                sharedPreferences.getString("dob", ""),
                Gender.valueOf(sharedPreferences.getString("gender", Gender.MALE.name())),
                sharedPreferences.getString("relationship_with_the_head_of_household", ""),
                sharedPreferences.getInt("apartment_number", 0),
                Role.valueOf(sharedPreferences.getString("role", Role.USER.name())),
                sharedPreferences.getString("phone", "")
        );
    }

    public void clearUser() {
        sharedPreferences.edit().clear().apply();
    }

    public void setLoggedIn(boolean loggedIn) {
        sharedPreferences.edit().putBoolean("isLoggedIn", loggedIn).apply();
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean("isLoggedIn", false);
    }
}
