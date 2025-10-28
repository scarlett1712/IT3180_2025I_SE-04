package com.se_04.enoti.account_related;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.chaos.view.PinView;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class EnterOTPActivity extends AppCompatActivity {

    public static final String EXTRA_PREVIOUS_ACTIVITY = "previous_activity";
    public static final String FROM_REGISTER_PHONE = "from_register_phone";
    public static final String FROM_FORGOT_PASSWORD = "from_forgot_password";

    private PinView pinView;
    private static final String API_CREATE_ADMIN_URL = ApiConfig.BASE_URL + "/api/users/create_admin";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_otp);

        TextView txtOtpMessage = findViewById(R.id.textViewOTPSentToPhoneNumber);
        TextView txtPhoneNumber = findViewById(R.id.textViewPhoneNumber);
        Button btnVerify = findViewById(R.id.buttonConfirm);
        pinView = findViewById(R.id.pinviewEnterOTP);

        String phone = getIntent().getStringExtra("phone");
        if (phone != null) {
            txtOtpMessage.setText("Mã OTP (demo) đã được gửi đến số ");
            txtPhoneNumber.setText(phone);
        }

        btnVerify.setOnClickListener(v -> {
            String otp = pinView.getText() != null ? pinView.getText().toString().trim() : "";
            if (otp.length() == 6) {
                handleOTPVerification();
            } else {
                Toast.makeText(this, "Vui lòng nhập đủ 6 số OTP (demo).", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleOTPVerification() {
        Intent intent = getIntent();
        String previousActivity = intent.getStringExtra(EXTRA_PREVIOUS_ACTIVITY);

        if (FROM_FORGOT_PASSWORD.equals(previousActivity)) {
            // 👉 Nếu đến từ màn quên mật khẩu → mở CreateNewPasswordActivity
            Intent createPasswordIntent = new Intent(this, CreateNewPasswordActivity.class);
            createPasswordIntent.putExtra("phone", intent.getStringExtra("phone"));
            startActivity(createPasswordIntent);
            finish();
        } else if (FROM_REGISTER_PHONE.equals(previousActivity)) {
            // 👉 Nếu đến từ đăng ký admin → gọi API tạo tài khoản admin
            createAdminAccount();
        } else {
            Toast.makeText(this, "Không xác định được nguồn mở OTP.", Toast.LENGTH_SHORT).show();
        }
    }


    private void createAdminAccount() {
        Intent intent = getIntent();
        JSONObject requestBody = new JSONObject();

        try {
            requestBody.put("phone", intent.getStringExtra("phone"));
            requestBody.put("password", intent.getStringExtra("password"));
            requestBody.put("full_name", intent.getStringExtra("fullName"));
            requestBody.put("dob", intent.getStringExtra("dob"));

            // 👇 Thêm giới tính (đã được truyền từ RegisterActivity)
            String gender = intent.getStringExtra("gender");
            if (gender != null && !gender.isEmpty()) {
                requestBody.put("gender", gender);
            }

        } catch (JSONException e) {
            Toast.makeText(this, "Lỗi tạo dữ liệu người dùng.", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, API_CREATE_ADMIN_URL, requestBody,
                response -> {
                    String msg = "Đăng ký tài khoản BQT thành công!";
                    try {
                        msg = response.optString("message", msg);
                    } catch (Exception ignored) {}

                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

                    // Quay về màn hình đăng nhập
                    Intent loginIntent = new Intent(this, LogInActivity.class);
                    loginIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(loginIntent);
                    finish();
                },
                error -> {
                    String errorMsg = "Đăng ký thất bại, vui lòng thử lại.";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            try {
                                JSONObject obj = new JSONObject(responseBody);
                                errorMsg = obj.optString("error", obj.optString("message", responseBody));
                            } catch (Exception e) {
                                errorMsg = responseBody;
                            }
                        } catch (Exception e) { /* ignore */ }
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                }) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("Content-Type", "application/json; charset=UTF-8");
                return headers;
            }
        };

        queue.add(jsonObjectRequest);
    }
}
