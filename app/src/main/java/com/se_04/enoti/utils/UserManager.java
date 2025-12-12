package com.se_04.enoti.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.VolleyError;
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

    public interface LogoutCallback {
        void onLogoutComplete();
    }

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

    // -------------------------------
    // TOKEN MANAGEMENT
    // -------------------------------

    public void saveAuthToken(String token) {
        sharedPreferences.edit().putString(KEY_AUTH_TOKEN, token).commit();
    }

    public String getAuthToken() {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null);
    }

    // -------------------------------
    // LOGOUT LOGIC
    // -------------------------------

    public void logout(LogoutCallback callback) {
        String url = ApiConfig.BASE_URL + "/api/users/logout";
        JSONObject body = new JSONObject();

        try {
            UserItem user = getCurrentUser();
            if (user != null) {
                body.put("user_id", user.getId());
            }
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Log.d("UserManager", "Server logout success");
                    forceLogout();
                    if (callback != null) callback.onLogoutComplete();
                },
                error -> {
                    Log.e("UserManager", "Server logout failed: " + error.toString());
                    forceLogout();
                    if (callback != null) callback.onLogoutComplete();
                }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                String token = getAuthToken();
                if (token != null && !token.isEmpty()) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                5000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Volley.newRequestQueue(context).add(request);
    }

    public void checkAndForceLogout(VolleyError error) {
        if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
            Log.e("UserManager", "Token expired (401). Force logging out...");
            Toast.makeText(context, "Phiên đăng nhập đã hết hạn.", Toast.LENGTH_LONG).show();
            forceLogout();
        }
    }

    // -------------------------------
    // FORCE LOGOUT (LOCAL)
    // -------------------------------

    public void forceLogout() {

        // 🔥 ĐÚNG CHỖ QUY ĐỊNH: XÓA TOKEN TẠI ĐÂY
        sharedPreferences.edit().remove(KEY_AUTH_TOKEN).apply();

        // Xóa dữ liệu user nhưng GIỮ token (vì token đã xóa riêng ở trên)
        clearUser();

        DataCacheManager.getInstance(context).clearAllCache();

        Intent intent = new Intent(context, LogInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    // -------------------------------
    // USER DATA MANAGEMENT
    // -------------------------------

    public void saveCurrentUser(UserItem user) {
        if (user == null) return;
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString("id", user.getId());
        editor.putString("familyId", user.getFamilyId());
        editor.putString("email", user.getEmail());
        editor.putString("name", user.getName());
        editor.putString("dob", user.getDob());

        if (user.getGender() != null)
            editor.putString("gender", user.getGender().name());

        editor.putString("relationship_with_the_head_of_household", user.getRelationship());
        editor.putInt("apartment_number", user.getRoom());
        editor.putString("phone", user.getPhone());

        if (user.getRole() != null)
            editor.putString("role", user.getRole().name());

        editor.putString("identity_card", user.getIdentityCard());
        editor.putString("home_town", user.getHomeTown());

        editor.commit();
    }

    public UserItem getCurrentUser() {
        if (!sharedPreferences.contains("id")) return null;

        Gender gender = Gender.MALE;
        try {
            gender = Gender.valueOf(sharedPreferences.getString("gender", Gender.MALE.name()));
        } catch (Exception ignored) {}

        Role role = Role.USER;
        try {
            role = Role.valueOf(sharedPreferences.getString("role", Role.USER.name()));
        } catch (Exception ignored) {}

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
                sharedPreferences.getString("phone", ""),
                sharedPreferences.getString("identity_card", ""),
                sharedPreferences.getString("home_town", "")
        );
    }

    // -------------------------------
    // FIXED CLEAR USER (KHÔNG XÓA TOKEN)
    // -------------------------------

    public void clearUser() {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.remove("id");
        editor.remove("familyId");
        editor.remove("email");
        editor.remove("name");
        editor.remove("dob");
        editor.remove("gender");
        editor.remove("relationship_with_the_head_of_household");
        editor.remove("apartment_number");
        editor.remove("phone");
        editor.remove("role");
        editor.remove("identity_card");
        editor.remove("home_town");
        editor.remove("isLoggedIn");
        editor.commit();
    }

    // -------------------------------
    // LOGIN STATE
    // -------------------------------

    public void setLoggedIn(boolean loggedIn) {
        sharedPreferences.edit().putBoolean("isLoggedIn", loggedIn).commit();
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean("isLoggedIn", false);
    }

    // -------------------------------
    // UTILS
    // -------------------------------

    public String getID() { return sharedPreferences.getString("id", ""); }

    public boolean isAdmin() {
        return "ADMIN".equals(sharedPreferences.getString("role", ""));
    }
}
