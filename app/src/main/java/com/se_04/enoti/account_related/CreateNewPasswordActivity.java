package com.se_04.enoti.account_related;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CreateNewPasswordActivity extends BaseActivity {

    private EditText editTextNewPassword;
    private EditText editTextConfirmPassword;
    private Button buttonConfirm;

    private static final String API_RESET_PASSWORD_URL = ApiConfig.BASE_URL + "/api/users/reset_password";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_new_password);

        editTextNewPassword = findViewById(R.id.editTextNewPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmNewPassword);
        buttonConfirm = findViewById(R.id.buttonConfirm);

        buttonConfirm.setOnClickListener(v -> handleCreateNewPassword());
    }

    private void handleCreateNewPassword() {
        String newPassword = editTextNewPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();
        int minLen = 6, maxLen = 16;

        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Không được để trống mật khẩu.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPassword.length() < minLen || newPassword.length() > maxLen) {
            editTextNewPassword.setError("Độ dài mật khẩu từ " + minLen + "–" + maxLen + " ký tự");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            editTextConfirmPassword.setError("Mật khẩu xác nhận không khớp");
            return;
        }

        String phone = getIntent().getStringExtra("phone");
        if (phone == null || phone.isEmpty()) {
            Toast.makeText(this, "Thiếu số điện thoại, vui lòng thử lại.", Toast.LENGTH_SHORT).show();
            return;
        }

        sendResetPasswordRequest(phone, newPassword);
    }

    private void sendResetPasswordRequest(String phone, String newPassword) {
        JSONObject body = new JSONObject();
        try {
            body.put("phone", phone);
            body.put("new_password", newPassword);
        } catch (JSONException e) {
            Toast.makeText(this, "Lỗi tạo dữ liệu yêu cầu.", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API_RESET_PASSWORD_URL, body,
                response -> {
                    Toast.makeText(this, "Cập nhật mật khẩu thành công! Vui lòng đăng nhập lại.", Toast.LENGTH_LONG).show();

                    // Chuyển về màn hình đăng nhập và xóa hết các màn hình trước đó
                    Intent intent = new Intent(this, LogInActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                },
                error -> {
                    String msg = "Đặt lại mật khẩu thất bại.";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            JSONObject obj = new JSONObject(responseBody);
                            msg = obj.optString("error", msg);
                        } catch (Exception ignored) {}
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json; charset=UTF-8");
                return headers;
            }
        };

        queue.add(request);
    }
}