package com.se_04.enoti.account_related;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.se_04.enoti.R;
import com.se_04.enoti.account.ChangePasswordActivity;
import com.se_04.enoti.account.Gender;
import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.home.admin.MainActivity_Admin;
import com.se_04.enoti.home.user.MainActivity_User;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.isValidVietnamesePhoneNumber;
import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.normalizePhoneNumber;

public class LogInActivity extends AppCompatActivity {
    private static final String TAG = "LOGIN_DEBUG";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_in);

        final EditText editTextPhone = findViewById(R.id.enterPhoneNumber);
        final EditText editTextPassword = findViewById(R.id.enterPassword);
        Button loginButton = findViewById(R.id.buttonSignIn);
        TextView textViewForgotPassword = findViewById(R.id.forgetPassword);
        TextView textViewChangePassword = findViewById(R.id.changePassword);
        TextView textViewRegister = findViewById(R.id.textViewRegister);

        // Đăng nhập
        loginButton.setOnClickListener(v -> handleLogin(editTextPhone, editTextPassword));

        // Quên mật khẩu
        textViewForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LogInActivity.this, ForgetPasswordEnterPhoneActivity.class);
            startActivity(intent);
        });

        // Đổi mật khẩu
        textViewChangePassword.setOnClickListener(v -> {
            Intent intent = new Intent(LogInActivity.this, ChangePasswordActivity.class);
            startActivity(intent);
        });

        // Đăng ký
        textViewRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LogInActivity.this, RegisterEnterPhoneActivity.class);
            startActivity(intent);
        });
    }

    private void handleLogin(EditText phoneField, EditText passwordField) {
        String phone = phoneField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();
        int minPasswordLength = 6, maxPasswordLength = 16;

        Log.d(TAG, "handleLogin start");

        if (!isValidVietnamesePhoneNumber(phone)) {
            phoneField.setError("Nhập số điện thoại chính xác.");
            phoneField.requestFocus();
            return;
        } else {
            phone = normalizePhoneNumber(phone);
        }

        if (password.isEmpty()) {
            passwordField.setError("Mật khẩu không thể để trống");
            passwordField.requestFocus();
            return;
        }

        if (password.length() < minPasswordLength || password.length() > maxPasswordLength) {
            passwordField.setError("Mật khẩu phải dài từ " + minPasswordLength + " đến " + maxPasswordLength + " ký tự");
            passwordField.requestFocus();
            return;
        }

        final String finalPhone = phone;
        final String finalPassword = password;
        final String apiUrl = ApiConfig.BASE_URL + "/api/users/login";

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                JSONObject body = new JSONObject();
                body.put("phone", finalPhone);
                body.put("password", finalPassword);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = body.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                InputStream responseStream = (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(responseStream, "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                String responseString = response.toString();
                Log.d(TAG, "Raw response: " + responseString);

                final int finalCode = code;
                runOnUiThread(() -> {
                    if (finalCode >= 200 && finalCode < 300) {
                        try {
                            // ✅ Đoạn parse JSON mới được thêm
                            JSONObject json = new JSONObject(responseString);
                            JSONObject userJson = json.getJSONObject("user");

                            String id = userJson.optString("user_id", "");
                            String familyId = userJson.optString("family_id", "");
                            String email = userJson.optString("email", "");
                            String name = userJson.optString("username",
                                    userJson.optString("full_name",
                                            userJson.optString("display_name",
                                                    userJson.optString("name", finalPhone))));

                            String dob = userJson.optString("dob", "");
                            String genderStr = userJson.optString("gender", "MALE").toUpperCase();
                            String relationship = userJson.optString("relationship_with_the_head_of_household", "");
                            if ("0".equals(relationship) || "Quản trị viên".equalsIgnoreCase(relationship)) {
                                relationship = "Không thuộc hộ gia đình nào";
                            }
                            String aptStr = userJson.optString("apartment_number", "0");
                            int room = 0;
                            try {
                                if (aptStr != null && !aptStr.isEmpty() && !"null".equalsIgnoreCase(aptStr)) {
                                    room = Integer.parseInt(aptStr);
                                }
                            } catch (NumberFormatException e) {
                                room = 0;
                            }
                            String phoneResp = userJson.optString("phone", "");
                            String roleStr = userJson.optString("role", "USER").toUpperCase();

                            if (genderStr.equals("NAM")) genderStr = "MALE";
                            else if (genderStr.equals("NU") || genderStr.equals("NỮ")) genderStr = "FEMALE";

                            Gender gender = Gender.valueOf(genderStr);
                            Role role = Role.valueOf(roleStr);

                            UserItem user = new UserItem(
                                    id, familyId, email, name, dob, gender, relationship, room, role, phoneResp
                            );

                            UserManager manager = UserManager.getInstance(getApplicationContext());
                            manager.saveCurrentUser(user);
                            manager.setLoggedIn(true);

                            Log.d("LOGIN", "✅ Saved user: " + user.getName() + " | Role: " + user.getRole());
                            Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();

                            Intent intent = (role == Role.ADMIN)
                                    ? new Intent(this, MainActivity_Admin.class)
                                    : new Intent(this, MainActivity_User.class);
                            startActivity(intent);
                            finish();

                        } catch (Exception e) {
                            Log.e("LOGIN", "❌ JSON parse error: " + e.getMessage());
                            Toast.makeText(this, "Lỗi khi xử lý phản hồi server", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        String msg = "Đăng nhập thất bại";
                        try {
                            JSONObject errJson = new JSONObject(responseString);
                            if (errJson.has("error")) msg = errJson.optString("error", msg);
                            else if (errJson.has("message")) msg = errJson.optString("message", msg);
                        } catch (Exception ignored) {}
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Network/login exception: " + e.getMessage(), e);
                runOnUiThread(() ->
                        Toast.makeText(this, "Không thể kết nối đến máy chủ: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
