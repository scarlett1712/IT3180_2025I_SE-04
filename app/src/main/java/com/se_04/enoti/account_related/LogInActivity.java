package com.se_04.enoti.account_related;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer; // 🔥 Import CountDownTimer
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.isValidVietnamesePhoneNumber;
import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.normalizePhoneNumber;

public class LogInActivity extends AppCompatActivity {

    private static final String API_LOGIN_URL = ApiConfig.BASE_URL + "/api/users/login";
    private static final String TAG = "LogInActivity";

    private Handler pollingHandler;
    private Runnable pollingRunnable;
    private boolean isPolling = false;

    // Dialog chờ duyệt
    private AlertDialog waitingDialog;
    private CountDownTimer countDownTimer;

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
        String normalizedPhone = normalizePhoneNumber(phone);

        if (password.isEmpty()) {
            Toast.makeText(this, R.string.error_password_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        showLoadingDialog(); // Hiện loading ban đầu

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("phone", normalizedPhone);
            requestBody.put("password", password);
        } catch (Exception e) { return; }

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST, API_LOGIN_URL, requestBody,
                response -> {
                    try {
                        // 🔥 1. TRƯỜNG HỢP CẦN DUYỆT (MÁY CŨ ĐANG ONLINE)
                        if (response.has("require_approval") && response.getBoolean("require_approval")) {
                            int requestId = response.getInt("request_id");

                            // Chuyển sang giao diện chờ đếm ngược
                            showWaitingForApprovalDialog(requestId, normalizedPhone);

                            // Bắt đầu Polling kiểm tra máy cũ
                            startPolling(requestId);
                            return;
                        }

                        // 🔥 2. ĐĂNG NHẬP THÀNH CÔNG NGAY
                        if (waitingDialog != null) waitingDialog.dismiss();
                        processLoginSuccess(response);

                    } catch (Exception e) {
                        if (waitingDialog != null) waitingDialog.dismiss();
                        Toast.makeText(this, "Lỗi xử lý: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    if (waitingDialog != null) waitingDialog.dismiss();
                    handleLoginError(error);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json; charset=UTF-8");
                return headers;
            }
        };

        queue.add(jsonObjectRequest);
    }

    private void showLoadingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Đang đăng nhập");
        builder.setMessage("Vui lòng đợi...");
        builder.setCancelable(false);
        waitingDialog = builder.create();
        waitingDialog.show();
    }

    // 🔥 HIỂN THỊ DIALOG CHỜ DUYỆT + ĐẾM NGƯỢC
    private void showWaitingForApprovalDialog(int requestId, String phone) {
        if (waitingDialog != null && waitingDialog.isShowing()) waitingDialog.dismiss();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Xác thực thiết bị");
        builder.setMessage("Tài khoản đang đăng nhập nơi khác.\nVui lòng mở thiết bị cũ và bấm 'Cho phép'.\n\nChờ phản hồi: 30s");
        builder.setCancelable(false);

        // Nút Hủy
        builder.setNegativeButton("Hủy", (dialog, which) -> stopPolling());

        // Nút Force Login (Ban đầu ẩn, sẽ hiện sau 30s)
        builder.setNeutralButton("Tôi bị mất máy cũ", (dialog, which) -> {
            stopPolling();
            // Chuyển sang EnterOTPActivity với cờ FORCE_LOGIN
            Intent intent = new Intent(LogInActivity.this, EnterOTPActivity.class);
            intent.putExtra(EnterOTPActivity.EXTRA_PREVIOUS_ACTIVITY, EnterOTPActivity.FROM_FORCE_LOGIN);
            intent.putExtra("phone", phone);
            startActivity(intent);
        });

        waitingDialog = builder.create();
        waitingDialog.show();

        // Ẩn nút "Mất máy" lúc đầu
        Button btnLost = waitingDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (btnLost != null) btnLost.setVisibility(View.GONE);

        // 🔥 Đếm ngược 30 giây
        countDownTimer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (waitingDialog != null && waitingDialog.isShowing()) {
                    waitingDialog.setMessage("Tài khoản đang đăng nhập nơi khác.\nVui lòng mở thiết bị cũ và bấm 'Cho phép'.\n\nChờ phản hồi: " + (millisUntilFinished / 1000) + "s");
                }
            }

            @Override
            public void onFinish() {
                if (waitingDialog != null && waitingDialog.isShowing()) {
                    waitingDialog.setMessage("Không nhận được phản hồi.\nBạn có thể dùng mã OTP để đăng nhập.");
                    // Hiện nút "Mất máy"
                    if (btnLost != null) btnLost.setVisibility(View.VISIBLE);
                }
            }
        }.start();
    }

    private void startPolling(int requestId) {
        isPolling = true;
        pollingHandler = new Handler(Looper.getMainLooper());
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPolling) return;
                checkLoginStatus(requestId);
                pollingHandler.postDelayed(this, 3000);
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void stopPolling() {
        isPolling = false;
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
        if (countDownTimer != null) countDownTimer.cancel();
    }

    private void checkLoginStatus(int requestId) {
        JSONObject body = new JSONObject();
        try {
            body.put("is_polling", true);
            body.put("request_id", requestId);
        } catch (Exception e) { return; }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API_LOGIN_URL, body,
                response -> {
                    String status = response.optString("status");
                    if ("approved".equals(status)) {
                        stopPolling();
                        if (waitingDialog != null) waitingDialog.dismiss();
                        processLoginSuccess(response);
                    } else if ("rejected".equals(status)) {
                        stopPolling();
                        if (waitingDialog != null) waitingDialog.dismiss();
                        Toast.makeText(this, "Yêu cầu đăng nhập bị từ chối.", Toast.LENGTH_LONG).show();
                    }
                },
                error -> { /* Log error silently */ }
        );
        Volley.newRequestQueue(this).add(request);
    }

    private void processLoginSuccess(JSONObject response) {
        try {
            if (!response.has("user")) {
                Toast.makeText(this, "Lỗi dữ liệu server.", Toast.LENGTH_SHORT).show();
                return;
            }
            String sessionToken = response.optString("session_token", "");
            if (!sessionToken.isEmpty()) UserManager.getInstance(getApplicationContext()).saveAuthToken(sessionToken);

            JSONObject userJson = response.getJSONObject("user");
            UserItem user = UserItem.fromJson(userJson);
            UserManager.getInstance(getApplicationContext()).saveCurrentUser(user);
            UserManager.getInstance(getApplicationContext()).setLoggedIn(true);

            Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();

            Intent intent = user.getRole() == com.se_04.enoti.account.Role.ADMIN
                    ? new Intent(this, MainActivity_Admin.class)
                    : new Intent(this, MainActivity_User.class);
            startActivity(intent);
            finish();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleLoginError(com.android.volley.VolleyError error) {
        String message = "Đăng nhập thất bại.";
        if (error.networkResponse != null && error.networkResponse.data != null) {
            try {
                String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                JSONObject data = new JSONObject(responseBody);
                message = data.optString("error", message);
            } catch (Exception e) { }
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }
}