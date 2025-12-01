package com.se_04.enoti.account_related;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

    // Biến cho Polling
    private Handler pollingHandler;
    private Runnable pollingRunnable;
    private ProgressDialog waitingDialog;
    private boolean isPolling = false;

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
        phone = normalizePhoneNumber(phone);

        if (password.isEmpty()) {
            Toast.makeText(this, R.string.error_password_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        // Hiện loading
        waitingDialog = new ProgressDialog(this);
        waitingDialog.setMessage("Đang đăng nhập...");
        waitingDialog.setCancelable(false);
        waitingDialog.show();

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("phone", phone);
            requestBody.put("password", password);
        } catch (Exception e) { return; }

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST, API_LOGIN_URL, requestBody,
                response -> {
                    try {
                        // 🔥 1. Trường hợp cần duyệt thiết bị (Bảo mật)
                        if (response.has("require_approval") && response.getBoolean("require_approval")) {
                            int requestId = response.getInt("request_id");
                            String msg = response.optString("message", "Vui lòng xác nhận trên thiết bị cũ.");

                            // Cập nhật Dialog để người dùng biết phải chờ
                            waitingDialog.setMessage(msg + "\nĐang chờ phản hồi...");
                            waitingDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, "Hủy", (dialog, which) -> stopPolling());

                            // Bắt đầu vòng lặp kiểm tra (Polling)
                            startPolling(requestId);
                            return;
                        }

                        // 🔥 2. Trường hợp đăng nhập thành công ngay
                        processLoginSuccess(response);

                    } catch (Exception e) {
                        if(waitingDialog.isShowing()) waitingDialog.dismiss();
                        Toast.makeText(this, "Lỗi xử lý: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    if(waitingDialog.isShowing()) waitingDialog.dismiss();
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

    // 🔥 HÀM POLLING: Kiểm tra trạng thái duyệt mỗi 3 giây
    private void startPolling(int requestId) {
        isPolling = true;
        pollingHandler = new Handler(Looper.getMainLooper());

        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPolling) return;
                checkLoginStatus(requestId);
                pollingHandler.postDelayed(this, 3000); // Check mỗi 3 giây
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void stopPolling() {
        isPolling = false;
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.dismiss();
        }
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
                        stopPolling(); // Dừng check
                        processLoginSuccess(response); // Đăng nhập
                    } else if ("pending".equals(status)) {
                        // Vẫn chờ, không làm gì cả
                    } else {
                        // Bị từ chối hoặc lỗi
                        stopPolling();
                        Toast.makeText(this, "Yêu cầu đăng nhập bị từ chối.", Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    // Nếu lỗi mạng, có thể thử lại hoặc dừng
                    // Ở đây ta cứ để nó chạy tiếp hoặc dừng tùy logic
                }
        );
        Volley.newRequestQueue(this).add(request);
    }

    // Xử lý khi đăng nhập thành công (Dùng chung cho cả 2 trường hợp)
    private void processLoginSuccess(JSONObject response) {
        try {
            if (waitingDialog != null && waitingDialog.isShowing()) waitingDialog.dismiss();

            if (!response.has("user")) {
                Toast.makeText(this, "Phản hồi không hợp lệ từ server.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Lưu Token
            String sessionToken = response.optString("session_token", "");
            if (!sessionToken.isEmpty()) {
                UserManager.getInstance(getApplicationContext()).saveAuthToken(sessionToken);
            }

            // Lưu User
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

        } catch (Exception e) {
            e.printStackTrace();
        }
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