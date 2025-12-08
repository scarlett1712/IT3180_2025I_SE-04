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
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.home.admin.MainActivity_Admin;
import com.se_04.enoti.home.user.MainActivity_User;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

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
    // 🔥 Hằng số mới cho trường hợp ép đăng nhập
    public static final String FROM_FORCE_LOGIN = "from_force_login";

    private static final String API_CREATE_ADMIN_URL = ApiConfig.BASE_URL + "/api/users/create_admin";
    private static final String API_FIREBASE_AUTH_URL = ApiConfig.BASE_URL + "/api/users/auth/firebase"; // API xác thực token

    private static final String TAG = "EnterOTPActivity";

    private PinView pinView;
    private Button btnVerify;
    private TextView txtOtpMessage;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private String mPhoneNumber;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_otp);

        mAuth = FirebaseAuth.getInstance();

        txtOtpMessage = findViewById(R.id.textViewOTPSentToPhoneNumber);
        TextView txtPhoneDisplay = findViewById(R.id.textViewPhoneNumber);
        btnVerify = findViewById(R.id.buttonConfirm);
        pinView = findViewById(R.id.pinviewEnterOTP);
        progressBar = findViewById(R.id.progressBar);

        mPhoneNumber = getIntent().getStringExtra("phone");
        if (mPhoneNumber != null) {
            txtPhoneDisplay.setText(mPhoneNumber);
            executorService.execute(() -> {
                mainHandler.post(() -> sendVerificationCode(mPhoneNumber));
            });
        } else {
            finish();
        }

        btnVerify.setOnClickListener(v -> {
            String otp = pinView.getText() != null ? pinView.getText().toString().trim() : "";
            if (otp.length() == 6) verifyCode(otp);
        });
    }

    private void setLoading(boolean isLoading) {
        if (progressBar != null) progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnVerify.setEnabled(!isLoading);
        pinView.setEnabled(!isLoading);
    }

    private void sendVerificationCode(String phoneNumber) {
        setLoading(true);
        String formattedPhone = phoneNumber;
        if (phoneNumber.startsWith("0")) formattedPhone = "+84" + phoneNumber.substring(1);

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(formattedPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(mCallbacks)
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        @Override
        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
            setLoading(false);
            String code = credential.getSmsCode();
            if (code != null) {
                pinView.setText(code);
                verifyCode(code);
            }
        }
        @Override
        public void onVerificationFailed(@NonNull FirebaseException e) {
            setLoading(false);
            Toast.makeText(EnterOTPActivity.this, "Gửi OTP thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        @Override
        public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
            setLoading(false);
            mVerificationId = verificationId;
            mResendToken = token;
            Toast.makeText(EnterOTPActivity.this, "Đã gửi mã OTP.", Toast.LENGTH_SHORT).show();
        }
    };

    private void verifyCode(String code) {
        if (mVerificationId == null) return;
        setLoading(true);
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        if (mAuth.getCurrentUser() != null) {
                            mAuth.getCurrentUser().getIdToken(true).addOnCompleteListener(tokenTask -> {
                                if (tokenTask.isSuccessful()) {
                                    String idToken = tokenTask.getResult().getToken();
                                    handleNextStep(idToken);
                                } else {
                                    setLoading(false);
                                }
                            });
                        }
                    } else {
                        setLoading(false);
                        Toast.makeText(this, "Mã OTP không đúng.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleNextStep(String idToken) {
        Intent intent = getIntent();
        String previousActivity = intent.getStringExtra(EXTRA_PREVIOUS_ACTIVITY);

        if (FROM_FORGOT_PASSWORD.equals(previousActivity)) {
            setLoading(false);
            Intent createPasswordIntent = new Intent(this, CreateNewPasswordActivity.class);
            createPasswordIntent.putExtra("idToken", idToken);
            startActivity(createPasswordIntent);
            finish();

        } else if (FROM_REGISTER_PHONE.equals(previousActivity)) {
            executorService.execute(() -> mainHandler.post(() -> createAdminAccount(idToken)));

        } else if (FROM_FORCE_LOGIN.equals(previousActivity)) {
            // 🔥 TRƯỜNG HỢP MẤT MÁY: Gọi API Login bằng Firebase Token
            performForceLogin(idToken);
        }
    }

    // 🔥 Hàm thực hiện Force Login
    private void performForceLogin(String idToken) {
        JSONObject body = new JSONObject();
        try {
            body.put("idToken", idToken);
        } catch (JSONException e) { return; }

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API_FIREBASE_AUTH_URL, body,
                response -> {
                    try {
                        setLoading(false);
                        // Xử lý đăng nhập thành công (giống LogInActivity)
                        String sessionToken = response.optString("session_token", "");
                        if (!sessionToken.isEmpty()) UserManager.getInstance(this).saveAuthToken(sessionToken);

                        JSONObject userJson = response.getJSONObject("user");
                        UserItem user = UserItem.fromJson(userJson);
                        UserManager.getInstance(this).saveCurrentUser(user);
                        UserManager.getInstance(this).setLoggedIn(true);

                        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();

                        // Vào app
                        Intent intent = user.getRole() == com.se_04.enoti.account.Role.ADMIN
                                ? new Intent(this, MainActivity_Admin.class)
                                : new Intent(this, MainActivity_User.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Lỗi xử lý dữ liệu.", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    setLoading(false);
                    Toast.makeText(this, "Đăng nhập thất bại. Số điện thoại chưa đăng ký?", Toast.LENGTH_LONG).show();
                }
        );
        queue.add(request);
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
            requestBody.put("gender", gender != null ? gender : "Khác");
        } catch (JSONException e) { setLoading(false); return; }

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API_CREATE_ADMIN_URL, requestBody,
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
                    Toast.makeText(this, "Đăng ký thất bại.", Toast.LENGTH_LONG).show();
                });
        queue.add(request);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) executorService.shutdown();
    }
}