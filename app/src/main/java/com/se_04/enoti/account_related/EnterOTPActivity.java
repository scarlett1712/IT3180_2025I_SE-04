package com.se_04.enoti.account_related;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.chaos.view.PinView;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EnterOTPActivity extends AppCompatActivity {

    public static final String EXTRA_PREVIOUS_ACTIVITY = "previous_activity";
    public static final String FROM_REGISTER_PHONE = "from_register_phone";
    public static final String FROM_FORGOT_PASSWORD = "from_forgot_password";

    private static final String API_CREATE_ADMIN_URL = ApiConfig.BASE_URL + "/api/users/create_admin";
    private static final String TAG = "EnterOTPActivity";

    private PinView pinView;
    private Button btnVerify;
    private TextView txtOtpMessage;
    private ProgressBar progressBar;

    // Firebase Auth
    private FirebaseAuth mAuth;
    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private String mPhoneNumber;

    // Executor cho các tác vụ nền (Tránh ANR)
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_otp);

        mAuth = FirebaseAuth.getInstance();

        // Ánh xạ View
        txtOtpMessage = findViewById(R.id.textViewOTPSentToPhoneNumber);
        TextView txtPhoneDisplay = findViewById(R.id.textViewPhoneNumber);
        btnVerify = findViewById(R.id.buttonConfirm);
        pinView = findViewById(R.id.pinviewEnterOTP);

        // Tìm ProgressBar (nếu có trong layout)
        progressBar = findViewById(R.id.progressBar);

        // Lấy dữ liệu từ Intent
        mPhoneNumber = getIntent().getStringExtra("phone");
        if (mPhoneNumber != null) {
            txtPhoneDisplay.setText(mPhoneNumber);

            // 🔥 Chạy gửi OTP trong background để tránh treo UI lúc khởi động
            executorService.execute(() -> {
                mainHandler.post(() -> sendVerificationCode(mPhoneNumber));
            });

        } else {
            Toast.makeText(this, "Lỗi: Không tìm thấy số điện thoại.", Toast.LENGTH_SHORT).show();
            finish();
        }

        btnVerify.setOnClickListener(v -> {
            String otp = pinView.getText() != null ? pinView.getText().toString().trim() : "";
            if (otp.length() == 6) {
                verifyCode(otp);
            } else {
                Toast.makeText(this, "Vui lòng nhập đủ 6 số OTP.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        btnVerify.setEnabled(!isLoading);
        pinView.setEnabled(!isLoading);
    }

    // 🔥 Gửi mã OTP qua Firebase
    private void sendVerificationCode(String phoneNumber) {
        setLoading(true);

        // Chuẩn hóa sđt
        String formattedPhone = phoneNumber;
        if (phoneNumber.startsWith("0")) {
            formattedPhone = "+84" + phoneNumber.substring(1);
        }

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(formattedPhone)    // Phone number to verify
                        .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
                        .setActivity(this)                 // Activity (for callback binding)
                        .setCallbacks(mCallbacks)          // OnVerificationStateChangedCallbacks
                        .build();
        PhoneAuthProvider.verifyPhoneNumber(options);

        // Toast.makeText(this, "Đang gửi OTP...", Toast.LENGTH_SHORT).show();
    }

    // 🔥 Callback lắng nghe trạng thái gửi OTP
    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        @Override
        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
            Log.d(TAG, "onVerificationCompleted:" + credential);
            setLoading(false);

            String code = credential.getSmsCode();
            if (code != null) {
                pinView.setText(code);
                verifyCode(code);
            }
        }

        @Override
        public void onVerificationFailed(@NonNull FirebaseException e) {
            Log.w(TAG, "onVerificationFailed", e);
            setLoading(false);

            String errorMsg = "Gửi OTP thất bại.";
            if (e.getMessage().contains("quota")) errorMsg = "Vượt quá giới hạn gửi SMS.";
            else if (e.getMessage().contains("format")) errorMsg = "Định dạng số điện thoại sai.";

            Toast.makeText(EnterOTPActivity.this, errorMsg + "\n" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onCodeSent(@NonNull String verificationId,
                               @NonNull PhoneAuthProvider.ForceResendingToken token) {
            Log.d(TAG, "onCodeSent:" + verificationId);
            setLoading(false);

            mVerificationId = verificationId;
            mResendToken = token;
            Toast.makeText(EnterOTPActivity.this, "Đã gửi mã OTP.", Toast.LENGTH_SHORT).show();
        }
    };

    // 🔥 Xác thực mã OTP người dùng nhập
    private void verifyCode(String code) {
        if (mVerificationId == null) {
            Toast.makeText(this, "Vui lòng chờ mã OTP được gửi.", Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
        signInWithPhoneAuthCredential(credential);
    }

    // 🔥 Đăng nhập vào Firebase bằng Credential
    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Xác thực thành công -> Lấy ID Token
                        if (mAuth.getCurrentUser() != null) {
                            mAuth.getCurrentUser().getIdToken(true).addOnCompleteListener(tokenTask -> {
                                if (tokenTask.isSuccessful()) {
                                    String idToken = tokenTask.getResult().getToken();
                                    Log.d(TAG, "Firebase Auth Success. Token: " + idToken);
                                    handleNextStep(idToken);
                                } else {
                                    setLoading(false);
                                    Toast.makeText(this, "Lỗi lấy Token xác thực.", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } else {
                        setLoading(false);
                        if (task.getException() != null) {
                            Toast.makeText(this, "Mã OTP không đúng.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // 🔥 Xử lý sau khi xác thực thành công
    private void handleNextStep(String idToken) {
        Intent intent = getIntent();
        String previousActivity = intent.getStringExtra(EXTRA_PREVIOUS_ACTIVITY);

        if (FROM_FORGOT_PASSWORD.equals(previousActivity)) {
            setLoading(false);
            // 👉 Chuyển sang màn hình đặt lại mật khẩu
            Intent createPasswordIntent = new Intent(this, CreateNewPasswordActivity.class);
            createPasswordIntent.putExtra("idToken", idToken);
            startActivity(createPasswordIntent);
            finish();

        } else if (FROM_REGISTER_PHONE.equals(previousActivity)) {
            // 👉 Gọi API tạo tài khoản Admin
            // Chạy trên background thread để tránh lag UI khi chuẩn bị request
            executorService.execute(() -> {
                mainHandler.post(() -> createAdminAccount(idToken));
            });
        }
    }

    private void createAdminAccount(String idToken) {
        Intent intent = getIntent();
        JSONObject requestBody = new JSONObject();

        try {
            requestBody.put("phone", intent.getStringExtra("phone"));
            requestBody.put("password", intent.getStringExtra("password"));
            requestBody.put("full_name", intent.getStringExtra("fullName"));
            requestBody.put("dob", intent.getStringExtra("dob"));
            requestBody.put("email", intent.getStringExtra("email"));

            requestBody.put("idToken", idToken);

            String gender = intent.getStringExtra("gender");
            if (gender != null) requestBody.put("gender", gender);

        } catch (JSONException e) {
            setLoading(false);
            return;
        }

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, API_CREATE_ADMIN_URL, requestBody,
                response -> {
                    setLoading(false);
                    Toast.makeText(this, "Đăng ký thành công! Vui lòng đăng nhập.", Toast.LENGTH_LONG).show();

                    Intent loginIntent = new Intent(this, LogInActivity.class);
                    loginIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(loginIntent);
                    finish();
                },
                error -> {
                    setLoading(false);
                    String errorMsg = "Đăng ký thất bại.";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            JSONObject obj = new JSONObject(responseBody);
                            errorMsg = obj.optString("error", errorMsg);
                        } catch (Exception ignored) {}
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json; charset=UTF-8");
                return headers;
            }
        };

        // Tăng thời gian chờ (Timeout) để tránh lỗi mạng chập chờn
        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(jsonObjectRequest);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}