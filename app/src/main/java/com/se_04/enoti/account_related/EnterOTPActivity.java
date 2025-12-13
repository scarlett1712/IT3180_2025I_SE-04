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
import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.home.accountant.MainActivity_Accountant;
import com.se_04.enoti.home.admin.MainActivity_Admin;
import com.se_04.enoti.home.agency.MainActivity_Agency;
import com.se_04.enoti.home.user.MainActivity_User;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EnterOTPActivity extends BaseActivity {

    public static final String EXTRA_PREVIOUS_ACTIVITY = "previous_activity";
    public static final String FROM_REGISTER_PHONE = "from_register_phone";
    public static final String FROM_FORGOT_PASSWORD = "from_forgot_password";
    public static final String FROM_FORCE_LOGIN = "from_force_login";

    // 🔥 API URLs RIÊNG BIỆT CHO 3 ROLE
    private static final String API_CREATE_ADMIN_URL = ApiConfig.BASE_URL + "/api/users/create_admin";
    private static final String API_CREATE_ACCOUNTANT_URL = ApiConfig.BASE_URL + "/api/users/create_accountant";
    private static final String API_CREATE_AGENCY_URL = ApiConfig.BASE_URL + "/api/users/create_agency";

    private static final String API_FIREBASE_AUTH_URL = ApiConfig.BASE_URL + "/api/users/auth/firebase";

    private PinView pinView;
    private Button btnVerify;
    private TextView txtOtpMessage, txtResendOtp, txtErrorOtp;
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
        txtResendOtp = findViewById(R.id.textViewResendOTP);
        txtErrorOtp = findViewById(R.id.textViewOTPNotMatch);

        mPhoneNumber = getIntent().getStringExtra("phone");
        if (mPhoneNumber != null) {
            txtPhoneDisplay.setText(mPhoneNumber);
            startPhoneNumberVerification(mPhoneNumber);
        } else {
            finish();
        }

        btnVerify.setOnClickListener(v -> {
            String otp = pinView.getText() != null ? pinView.getText().toString().trim() : "";
            if (otp.length() == 6) {
                txtErrorOtp.setVisibility(View.INVISIBLE);
                verifyCode(otp);
            } else {
                Toast.makeText(this, "Vui lòng nhập đủ 6 số OTP", Toast.LENGTH_SHORT).show();
            }
        });

        txtResendOtp.setOnClickListener(v -> {
            if (mPhoneNumber != null) {
                Toast.makeText(this, "Đang gửi lại mã...", Toast.LENGTH_SHORT).show();
                startPhoneNumberVerification(mPhoneNumber);
            }
        });

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
        txtResendOtp.setEnabled(!isLoading);
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
            mResendToken = token;
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
                        txtErrorOtp.setVisibility(View.VISIBLE);
                    }
                });
    }

    // 🔥 HÀM XỬ LÝ ĐIỀU HƯỚNG
    private void handleNextStep(String idToken) {
        Intent intent = getIntent();
        String previousActivity = intent.getStringExtra(EXTRA_PREVIOUS_ACTIVITY);

        if (FROM_FORGOT_PASSWORD.equals(previousActivity)) {
            setLoading(false);
            Intent createPasswordIntent = new Intent(this, CreateNewPasswordActivity.class);
            createPasswordIntent.putExtra("idToken", idToken);
            createPasswordIntent.putExtra("phone", mPhoneNumber);
            startActivity(createPasswordIntent);
            finish();

        } else if (FROM_REGISTER_PHONE.equals(previousActivity)) {
            // 🔥 LẤY TARGET ROLE ĐỂ CHỌN URL
            String targetRole = intent.getStringExtra("target_role");
            String apiUrl = API_CREATE_ADMIN_URL; // Mặc định là Admin nếu null

            if ("ACCOUNTANT".equals(targetRole)) {
                apiUrl = API_CREATE_ACCOUNTANT_URL;
            } else if ("AGENCY".equals(targetRole)) {
                apiUrl = API_CREATE_AGENCY_URL;
            }

            final String finalUrl = apiUrl;
            executorService.execute(() -> mainHandler.post(() -> createStaffAccount(idToken, finalUrl)));

        } else if (FROM_FORCE_LOGIN.equals(previousActivity)) {
            performForceLogin(idToken, false);
        }
    }

    // 🔥 GỌI API TẠO TÀI KHOẢN (Chung cho cả 3 Role)
    private void createStaffAccount(String idToken, String apiUrl) {
        Intent intent = getIntent();
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("phone", intent.getStringExtra("phone"));
            requestBody.put("password", intent.getStringExtra("password"));
            requestBody.put("full_name", intent.getStringExtra("fullName"));
            requestBody.put("dob", intent.getStringExtra("dob"));
            requestBody.put("email", intent.getStringExtra("email"));

            // Dữ liệu bổ sung
            requestBody.put("identity_card", intent.getStringExtra("identity_card"));
            requestBody.put("home_town", intent.getStringExtra("home_town"));

            requestBody.put("idToken", idToken);
            String gender = intent.getStringExtra("gender");
            requestBody.put("gender", gender != null ? gender : "Khác");
        } catch (JSONException e) { setLoading(false); return; }

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, apiUrl, requestBody,
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
                    // Xử lý lỗi trùng SĐT (Code 409 từ Backend)
                    if (error.networkResponse != null && error.networkResponse.statusCode == 409) {
                        errorMsg = "Số điện thoại này đã được đăng ký!";
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                });
        queue.add(request);
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
                            String sessionToken = response.optString("session_token", null);
                            if (sessionToken == null || sessionToken.isEmpty() || sessionToken.equals("null")) {
                                Toast.makeText(this, "Lỗi nghiêm trọng: Server không trả về token.", Toast.LENGTH_LONG).show();
                                return;
                            }
                            UserManager.getInstance(getApplicationContext()).saveAuthToken(sessionToken);

                            JSONObject userJson = response.getJSONObject("user");
                            UserItem user = UserItem.fromJson(userJson);
                            UserManager.getInstance(getApplicationContext()).saveCurrentUser(user);
                            UserManager.getInstance(getApplicationContext()).setLoggedIn(true);

                            Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();

                            Intent intent;
                            Role userRole = user.getRole();
                            if (userRole == Role.ADMIN) intent = new Intent(this, MainActivity_Admin.class);
                            else if (userRole == Role.ACCOUNTANT) intent = new Intent(this, MainActivity_Accountant.class);
                            else if (userRole == Role.AGENCY) intent = new Intent(this, MainActivity_Agency.class);
                            else intent = new Intent(this, MainActivity_User.class);

                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            setLoading(false);
                            Toast.makeText(this, "Lỗi dữ liệu từ server.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) { setLoading(false); e.printStackTrace(); }
                },
                error -> {
                    setLoading(false);
                    Toast.makeText(this, "Đăng nhập thất bại.", Toast.LENGTH_LONG).show();
                }
        );
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
        dialog.setContentView(R.layout.dialog_warning_login);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvMessage = dialog.findViewById(R.id.tvMessage);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnForce = dialog.findViewById(R.id.btnForceLogin);

        if (message != null && !message.isEmpty()) tvMessage.setText(message);

        btnCancel.setOnClickListener(v -> { dialog.dismiss(); setLoading(false); });
        btnForce.setOnClickListener(v -> { dialog.dismiss(); performForceLogin(idToken, true); });
        dialog.show();
    }
}