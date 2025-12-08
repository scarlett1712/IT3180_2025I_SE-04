package com.se_04.enoti.account_related;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EnterOTPActivity extends AppCompatActivity {

    public static final String EXTRA_PREVIOUS_ACTIVITY = "previous_activity";
    public static final String FROM_REGISTER_PHONE = "from_register_phone";
    public static final String FROM_FORGOT_PASSWORD = "from_forgot_password";
    public static final String FROM_FORCE_LOGIN = "from_force_login";

    private static final String API_CREATE_ADMIN_URL = ApiConfig.BASE_URL + "/api/users/create_admin";
    private static final String API_FIREBASE_AUTH_URL = ApiConfig.BASE_URL + "/api/users/auth/firebase";

    private PinView pinView;
    private Button btnVerify;
    private TextView txtOtpMessage, txtResendOtp, txtErrorOtp; // 🔥 Thêm biến mới
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

        // Ánh xạ View (Khớp với ID trong XML mới)
        txtOtpMessage = findViewById(R.id.textViewOTPSentToPhoneNumber);
        TextView txtPhoneDisplay = findViewById(R.id.textViewPhoneNumber);
        btnVerify = findViewById(R.id.buttonConfirm);
        pinView = findViewById(R.id.pinviewEnterOTP);
        progressBar = findViewById(R.id.progressBar);

        // 🔥 Ánh xạ các view mới thêm
        txtResendOtp = findViewById(R.id.textViewResendOTP);
        txtErrorOtp = findViewById(R.id.textViewOTPNotMatch);

        // Lấy SĐT từ Intent
        mPhoneNumber = getIntent().getStringExtra("phone");
        if (mPhoneNumber != null) {
            txtPhoneDisplay.setText(mPhoneNumber); // Hiển thị số đẹp trên giao diện
            startPhoneNumberVerification(mPhoneNumber); // Gửi mã lần đầu
        } else {
            finish();
        }

        // Sự kiện bấm nút Xác nhận
        btnVerify.setOnClickListener(v -> {
            String otp = pinView.getText() != null ? pinView.getText().toString().trim() : "";
            if (otp.length() == 6) {
                txtErrorOtp.setVisibility(View.INVISIBLE); // Ẩn lỗi trước khi check
                verifyCode(otp);
            } else {
                Toast.makeText(this, "Vui lòng nhập đủ 6 số OTP", Toast.LENGTH_SHORT).show();
            }
        });

        // 🔥 Sự kiện bấm nút Gửi lại OTP
        txtResendOtp.setOnClickListener(v -> {
            if (mPhoneNumber != null) {
                Toast.makeText(this, "Đang gửi lại mã...", Toast.LENGTH_SHORT).show();
                startPhoneNumberVerification(mPhoneNumber);
            }
        });

        // 🔥 Tự động ẩn lỗi khi người dùng nhập lại
        pinView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                txtErrorOtp.setVisibility(View.INVISIBLE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setLoading(boolean isLoading) {
        if (progressBar != null) progressBar.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);
        btnVerify.setEnabled(!isLoading);
        pinView.setEnabled(!isLoading);
        txtResendOtp.setEnabled(!isLoading); // Khóa nút gửi lại khi đang loading
    }

    private void startPhoneNumberVerification(String phoneNumber) {
        setLoading(true);
        String formattedPhone = phoneNumber;
        if (phoneNumber.startsWith("0")) formattedPhone = "+84" + phoneNumber.substring(1);

        PhoneAuthOptions.Builder optionsBuilder = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(formattedPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(mCallbacks);

        // 🔥 Nếu đã có token gửi lại (Resend), hãy dùng nó để không bị bắt check Robot
        if (mResendToken != null) {
            optionsBuilder.setForceResendingToken(mResendToken);
        }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build());
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
            mResendToken = token; // Lưu token để dùng cho chức năng "Gửi lại"
            Toast.makeText(EnterOTPActivity.this, "Đã gửi mã OTP.", Toast.LENGTH_SHORT).show();
        }
    };

    private void verifyCode(String code) {
        if (mVerificationId == null) {
            Toast.makeText(this, "Lỗi xác thực. Vui lòng gửi lại mã.", Toast.LENGTH_SHORT).show();
            return;
        }
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
                        // 🔥 Hiển thị lỗi lên giao diện thay vì chỉ Toast
                        txtErrorOtp.setVisibility(View.VISIBLE);
                        // Toast.makeText(this, "Mã OTP không đúng.", Toast.LENGTH_SHORT).show();
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
            performForceLogin(idToken, false);
        }
    }

    private void performForceLogin(String idToken, boolean forceLogin) {
        setLoading(true);
        JSONObject body = new JSONObject();
        try {
            body.put("idToken", idToken);
            if (forceLogin) body.put("force_login", true);
        } catch (JSONException e) { return; }

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API_FIREBASE_AUTH_URL, body,
                response -> {
                    try {
                        if (response.has("require_approval") && response.getBoolean("require_approval")) {
                            setLoading(false);
                            String message = response.optString("message", "Tài khoản đang đăng nhập nơi khác.");
                            showForceLoginDialog(idToken, message);
                            return;
                        }

                        if (response.has("user")) {
                            setLoading(false);
                            String sessionToken = response.optString("session_token", "");
                            if (!sessionToken.isEmpty()) UserManager.getInstance(this).saveAuthToken(sessionToken);

                            JSONObject userJson = response.getJSONObject("user");
                            UserItem user = UserItem.fromJson(userJson);
                            UserManager.getInstance(this).saveCurrentUser(user);
                            UserManager.getInstance(this).setLoggedIn(true);

                            Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();

                            Intent intent = user.getRole() == com.se_04.enoti.account.Role.ADMIN
                                    ? new Intent(this, MainActivity_Admin.class)
                                    : new Intent(this, MainActivity_User.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            setLoading(false);
                            Toast.makeText(this, "Lỗi dữ liệu từ server.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        setLoading(false);
                        e.printStackTrace();
                    }
                },
                error -> {
                    setLoading(false);
                    Toast.makeText(this, "Đăng nhập thất bại.", Toast.LENGTH_LONG).show();
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

    private void showForceLoginDialog(String idToken, String message) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_warning_login); // Layout vừa tạo
        dialog.setCancelable(false); // Không cho bấm ra ngoài để tắt

        // Làm nền dialog trong suốt để thấy bo góc
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Ánh xạ View trong Dialog
        TextView tvMessage = dialog.findViewById(R.id.tvMessage);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnForce = dialog.findViewById(R.id.btnForceLogin);

        // Set nội dung tin nhắn từ server (nếu có)
        if (message != null && !message.isEmpty()) {
            tvMessage.setText(message);
        }

        // Sự kiện nút Hủy
        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            setLoading(false); // Tắt loading ở màn hình chính
        });

        // Sự kiện nút Tiếp tục (Force Login)
        btnForce.setOnClickListener(v -> {
            dialog.dismiss();
            performForceLogin(idToken, true); // Gọi lại API với force = true
        });

        dialog.show();
    }
}