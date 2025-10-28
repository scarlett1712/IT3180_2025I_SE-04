package com.se_04.enoti.account_related;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.home.admin.MainActivity_Admin;
import com.se_04.enoti.home.user.MainActivity_User;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONObject;

import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.isValidVietnamesePhoneNumber;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LogInActivity extends AppCompatActivity {

    private static final String API_LOGIN_URL = "http://10.0.2.2:5000/api/users/login";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_in);

        final EditText editTextPhone = findViewById(R.id.enterPhoneNumber);
        final EditText editTextPassword = findViewById(R.id.enterPassword);
        Button loginButton = findViewById(R.id.buttonSignIn);
        TextView textViewForgotPassword = findViewById(R.id.forgetPassword);
        TextView textViewRegister = findViewById(R.id.textViewRegister);

        loginButton.setOnClickListener(v -> handleLogin(
                editTextPhone.getText().toString().trim(),
                editTextPassword.getText().toString().trim()
        ));

        textViewForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LogInActivity.this, ForgetPasswordEnterPhoneActivity.class);
            startActivity(intent);
        });

        textViewRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LogInActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void handleLogin(String phone, String password) {
        if (!isValidVietnamesePhoneNumber(phone)) {
            Toast.makeText(this, R.string.error_invalid_phone, Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.isEmpty()) {
            Toast.makeText(this, R.string.error_password_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "🔄 Đang đăng nhập...", Toast.LENGTH_SHORT).show();

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("phone", phone);
            requestBody.put("password", password);
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi tạo dữ liệu đăng nhập.", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                API_LOGIN_URL,
                requestBody,
                response -> {
                    try {
                        // ✅ Server trả về JSON chứa user
                        if (!response.has("user")) {
                            Toast.makeText(this, "Phản hồi không hợp lệ từ server.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        JSONObject userJson = response.getJSONObject("user");
                        UserItem user = UserItem.fromJson(userJson);

                        // ✅ Lưu user vào SharedPreferences
                        UserManager.getInstance(getApplicationContext()).saveCurrentUser(user);
                        UserManager.getInstance(getApplicationContext()).setLoggedIn(true);

                        Toast.makeText(this, "✅ Đăng nhập thành công!", Toast.LENGTH_SHORT).show();

                        // ✅ Chuyển trang tương ứng
                        Intent intent = user.getRole() == com.se_04.enoti.account.Role.ADMIN
                                ? new Intent(this, MainActivity_Admin.class)
                                : new Intent(this, MainActivity_User.class);
                        startActivity(intent);
                        finish();

                    } catch (Exception e) {
                        Toast.makeText(this, "Lỗi xử lý phản hồi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    // ❌ Xử lý lỗi khi đăng nhập thất bại
                    String message = "Đăng nhập thất bại.";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            JSONObject data = new JSONObject(responseBody);
                            message = data.optString("error", message);
                        } catch (Exception e) {
                            // ignore parse error
                        }
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
        ) {
            // 🧩 Thêm Content-Type cho đúng
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json; charset=UTF-8");
                return headers;
            }
        };

        queue.add(jsonObjectRequest);
    }
}
