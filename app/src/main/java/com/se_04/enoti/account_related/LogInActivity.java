package com.se_04.enoti.account_related;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.R;
import com.se_04.enoti.account.Role; // 🔥 Import Role
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.home.accountant.MainActivity_Accountant; // 🔥 Import màn hình kế toán
import com.se_04.enoti.home.admin.MainActivity_Admin;
import com.se_04.enoti.home.agency.MainActivity_Agency;
import com.se_04.enoti.home.user.MainActivity_User;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.isValidVietnamesePhoneNumber;
import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.normalizePhoneNumber;

public class LogInActivity extends BaseActivity {

    private static final String API_LOGIN_URL = ApiConfig.BASE_URL + "/api/users/login";

    private Handler pollingHandler;
    private Runnable pollingRunnable;
    private boolean isPolling = false;

    private Dialog customWaitingDialog;
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
                        // 1. TRƯỜNG HỢP CẦN DUYỆT (THIẾT BỊ MỚI)
                        if (response.has("require_approval") && response.getBoolean("require_approval")) {
                            int requestId = response.getInt("request_id");
                            if (customWaitingDialog != null) customWaitingDialog.dismiss();
                            showWaitingForApprovalDialog(requestId, normalizedPhone);
                            startPolling(requestId);
                            return;
                        }

                        // 2. ĐĂNG NHẬP THÀNH CÔNG
                        if (customWaitingDialog != null) customWaitingDialog.dismiss();
                        processLoginSuccess(response);

                    } catch (Exception e) {
                        if (customWaitingDialog != null) customWaitingDialog.dismiss();
                        Toast.makeText(this, "Lỗi xử lý: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    if (customWaitingDialog != null) customWaitingDialog.dismiss();
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

    private void showWaitingForApprovalDialog(int requestId, String phone) {
        if (customWaitingDialog != null && customWaitingDialog.isShowing()) customWaitingDialog.dismiss();

        customWaitingDialog = new Dialog(this);
        customWaitingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        customWaitingDialog.setContentView(R.layout.dialog_waiting_approval); // Đảm bảo layout này tồn tại
        customWaitingDialog.setCancelable(false);

        if (customWaitingDialog.getWindow() != null) {
            customWaitingDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvMessage = customWaitingDialog.findViewById(R.id.tvMessage);
        TextView tvTimer = customWaitingDialog.findViewById(R.id.tvTimer);
        ProgressBar progressBar = customWaitingDialog.findViewById(R.id.progressBarWaiting);
        Button btnCancel = customWaitingDialog.findViewById(R.id.btnCancel);
        Button btnLostDevice = customWaitingDialog.findViewById(R.id.btnLostDevice);

        btnCancel.setOnClickListener(v -> {
            stopPolling();
            customWaitingDialog.dismiss();
        });

        btnLostDevice.setOnClickListener(v -> {
            stopPolling();
            customWaitingDialog.dismiss();
            Intent intent = new Intent(LogInActivity.this, EnterOTPActivity.class);
            intent.putExtra(EnterOTPActivity.EXTRA_PREVIOUS_ACTIVITY, EnterOTPActivity.FROM_FORCE_LOGIN);
            intent.putExtra("phone", phone);
            startActivity(intent);
        });

        customWaitingDialog.show();

        countDownTimer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (customWaitingDialog != null && customWaitingDialog.isShowing()) {
                    tvTimer.setText("Chờ phản hồi: " + (millisUntilFinished / 1000) + "s");
                }
            }

            @Override
            public void onFinish() {
                if (customWaitingDialog != null && customWaitingDialog.isShowing()) {
                    tvTimer.setText("Không có phản hồi!");
                    tvMessage.setText("Không nhận được phản hồi từ thiết bị cũ.\nBạn có thể đăng nhập bằng mã OTP.");
                    progressBar.setVisibility(View.GONE);
                    btnLostDevice.setVisibility(View.VISIBLE);
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
                        if (customWaitingDialog != null) customWaitingDialog.dismiss();
                        processLoginSuccess(response);
                    } else if ("rejected".equals(status)) {
                        stopPolling();
                        if (customWaitingDialog != null) customWaitingDialog.dismiss();
                        Toast.makeText(this, "Yêu cầu đăng nhập bị từ chối.", Toast.LENGTH_LONG).show();
                    }
                },
                error -> { }
        );
        Volley.newRequestQueue(this).add(request);
    }

    // 🔥 HÀM ĐÃ SỬA: Điều hướng theo Role
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

            Intent intent;

            // 🔥 LOGIC PHÂN QUYỀN MỚI
            if (user.getRole() == Role.ADMIN) {
                intent = new Intent(this, MainActivity_Admin.class);
            } else if (user.getRole() == Role.ACCOUNTANT) {
                intent = new Intent(this, MainActivity_Accountant.class); // Màn hình kế toán
            } else if (user.getRole() == Role.AGENCY) {
                intent = new Intent(this, MainActivity_Agency.class); // Màn hình cqcn
            } else {
                intent = new Intent(this, MainActivity_User.class);
            }

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
        if (customWaitingDialog != null) customWaitingDialog.dismiss();
    }
}