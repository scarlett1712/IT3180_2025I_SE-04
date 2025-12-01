package com.se_04.enoti.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.account.Gender;
import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.account_related.LogInActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class UserManager {
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_AUTH_TOKEN = "auth_token";

    private static UserManager instance;
    private final SharedPreferences sharedPreferences;
    private final Context context;

    private UserManager(Context context) {
        this.context = context.getApplicationContext();
        sharedPreferences = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized UserManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserManager(context);
        }
        return instance;
    }

    public void saveAuthToken(String token) {
        sharedPreferences.edit().putString(KEY_AUTH_TOKEN, token).apply();
    }

    public String getAuthToken() {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null);
    }

    // 🔥 HÀM LOGOUT ĐÃ ĐƯỢC NÂNG CẤP
    public void logout() {
        // 1. Gọi API báo Server xóa Session (Fire-and-forget)
        String url = ApiConfig.BASE_URL + "/api/users/logout";
        JSONObject body = new JSONObject();
        try {
            UserItem user = getCurrentUser();
            if (user != null) {
                body.put("user_id", user.getId());
            }
        } catch (JSONException e) { e.printStackTrace(); }

        // Gửi request không cần chờ kết quả UI
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> Log.d("UserManager", "Server logout success"),
                error -> Log.e("UserManager", "Server logout failed")
        );
        Volley.newRequestQueue(context).add(request);

        // 2. Xóa dữ liệu Local
        clearUser();

        // 3. Chuyển về màn hình đăng nhập
        Intent intent = new Intent(context, LogInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    // --- Các hàm Getter/Setter giữ nguyên ---

    public void saveCurrentUser(UserItem user) {
        if (user == null) return;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("id", user.getId());
        editor.putString("familyId", user.getFamilyId());
        editor.putString("email", user.getEmail());
        editor.putString("name", user.getName());
        editor.putString("dob", user.getDob());
        if (user.getGender() != null) editor.putString("gender", user.getGender().name());
        editor.putString("relationship_with_the_head_of_household", user.getRelationship());
        editor.putInt("apartment_number", user.getRoom());
        editor.putString("phone", user.getPhone());
        if (user.getRole() != null) editor.putString("role", user.getRole().name());
        editor.apply();
    }

    public UserItem getCurrentUser() {
        if (!sharedPreferences.contains("id")) return null;
        Gender gender = Gender.MALE;
        try {
            String g = sharedPreferences.getString("gender", Gender.MALE.name());
            gender = Gender.valueOf(g);
        } catch (Exception e) {}

        Role role = Role.USER;
        try {
            String r = sharedPreferences.getString("role", Role.USER.name());
            role = Role.valueOf(r);
        } catch (Exception e) {}

        return new UserItem(
                sharedPreferences.getString("id", ""),
                sharedPreferences.getString("familyId", ""),
                sharedPreferences.getString("email", ""),
                sharedPreferences.getString("name", ""),
                sharedPreferences.getString("dob", ""),
                gender,
                sharedPreferences.getString("relationship_with_the_head_of_household", ""),
                sharedPreferences.getInt("apartment_number", 0),
                role,
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

    public String getID(){ return sharedPreferences.getString("id", "");}

    public boolean isAdmin(){ return sharedPreferences.getString("role", "").equals("ADMIN");}
}