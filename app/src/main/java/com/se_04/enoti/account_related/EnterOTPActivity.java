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

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class EnterOTPActivity extends AppCompatActivity {

    // Constants for identifying the previous activity's flow
    public static final String EXTRA_PREVIOUS_ACTIVITY = "previous_activity";
    public static final String FROM_REGISTER_PHONE = "from_register_phone";
    public static final String FROM_FORGOT_PASSWORD = "from_forgot_password";

    private PinView pinView;
    // API create admin (unchanged)
    private static final String API_CREATE_ADMIN_URL = "http://10.0.2.2:5000/api/users/create_admin";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_otp);

        TextView txtOtpMessage = findViewById(R.id.textViewOTPSentToPhoneNumber);
        Button btnVerify = findViewById(R.id.buttonConfirm);
        pinView = findViewById(R.id.pinviewEnterOTP);

        String phone = getIntent().getStringExtra("phone");
        if (phone != null) {
            txtOtpMessage.setText("Mã OTP (demo) đã được gửi đến số " + phone + " — (demo: nhập bất kỳ 6 chữ số)");
        }

        btnVerify.setOnClickListener(v -> {
            String otp = pinView.getText() != null ? pinView.getText().toString().trim() : "";
            // For demo: accept any 6-digit value (no backend verification)
            if (otp.length() == 6) {
                // Directly proceed without contacting any OTP verification endpoint
                handleOTPVerification();
            } else {
                Toast.makeText(this, "Vui lòng nhập đủ 6 số OTP (demo).", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleOTPVerification() {
        Intent intent = getIntent();
        boolean isAdminRegistration = intent.getBooleanExtra("is_admin_registration", false);

        if (isAdminRegistration) {
            // Directly create admin account (no OTP verify step)
            createAdminAccount();
        } else {
            // For forgot-password flow, allow user to create new password immediately
            Intent createPasswordIntent = new Intent(this, CreateNewPasswordActivity.class);
            createPasswordIntent.putExtra("phone", intent.getStringExtra("phone"));
            startActivity(createPasswordIntent);
            finish();
        }
    }

    private void createAdminAccount() {
        Intent intent = getIntent();
        JSONObject requestBody = new JSONObject();
        try {
            // Note: use snake_case keys if your backend expects them (matching earlier backend)
            requestBody.put("phone", intent.getStringExtra("phone"));
            requestBody.put("password", intent.getStringExtra("password"));
            // match backend field names (user_item uses full_name)
            requestBody.put("full_name", intent.getStringExtra("fullName"));
            requestBody.put("dob", intent.getStringExtra("dob"));
        } catch (JSONException e) {
            Toast.makeText(this, "Lỗi tạo dữ liệu người dùng.", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, API_CREATE_ADMIN_URL, requestBody,
                response -> {
                    // show server message if exists
                    String msg = "Đăng ký tài khoản BQT thành công!";
                    try {
                        msg = response.optString("message", msg);
                    } catch (Exception ignored) {}
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

                    // go back to login
                    Intent loginIntent = new Intent(this, LogInActivity.class);
                    loginIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(loginIntent);
                    finish();
                },
                error -> {
                    // show error text from server if available
                    String errorMsg = "Đăng ký thất bại, vui lòng thử lại.";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            // try to parse JSON error
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
