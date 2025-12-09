package com.se_04.enoti.account_related;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;

import java.util.Calendar;
import java.util.Locale;

import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.isValidVietnamesePhoneNumber;
import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.normalizePhoneNumber;

public class RegisterActivity extends AppCompatActivity {

    // Khai báo view
    private TextInputEditText edtFullName, edtDob, edtPhoneNumber, edtPassword, edtConfirmPassword, edtAdminKey;
    // 🔥 Thêm 2 trường mới cho CCCD và Quê quán
    private TextInputEditText edtIdentityCard, edtHomeTown;

    private Spinner spnGender;
    private Button btnRegister;
    private TextView textBackToLogin;

    // Key xác thực admin
    private static final String ADMIN_SECRET_KEY = "ENOTI_ADMIN_2024";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupDatePicker();
        setupGenderSpinner();
        setupListeners();
    }

    private void initViews() {
        edtFullName = findViewById(R.id.edtFullName);
        edtDob = findViewById(R.id.edtDob);
        edtPhoneNumber = findViewById(R.id.edtPhoneNumber);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        edtAdminKey = findViewById(R.id.edtAdminKey);

        // 🔥 Ánh xạ view mới (Đảm bảo file XML đã có ID này)
        edtIdentityCard = findViewById(R.id.edtIdentityCard);
        edtHomeTown = findViewById(R.id.edtHomeTown);

        spnGender = findViewById(R.id.spnGender);
        btnRegister = findViewById(R.id.btnRegister);
        textBackToLogin = findViewById(R.id.textBackToLogin);
    }

    private void setupDatePicker() {
        edtDob.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%d",
                        dayOfMonth, month + 1, year);
                edtDob.setText(selectedDate);
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void setupGenderSpinner() {
        String[] genders = {"Nam", "Nữ", "Khác"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, genders);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnGender.setAdapter(adapter);
    }

    private void setupListeners() {
        btnRegister.setOnClickListener(v -> handleRegistration());

        textBackToLogin.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LogInActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void handleRegistration() {
        // 🛑 Chống spam click: Khóa nút ngay lập tức
        btnRegister.setEnabled(false);

        // 1. Lấy dữ liệu
        String fullName = getTextSafe(edtFullName);
        String dob = getTextSafe(edtDob);
        String phone = getTextSafe(edtPhoneNumber);
        String password = getTextSafe(edtPassword);
        String confirmPassword = getTextSafe(edtConfirmPassword);
        String adminKey = getTextSafe(edtAdminKey);
        // 🔥 Lấy dữ liệu mới
        String identityCard = getTextSafe(edtIdentityCard);
        String homeTown = getTextSafe(edtHomeTown);

        String genderDisplay = spnGender.getSelectedItem() != null ? spnGender.getSelectedItem().toString() : "";

        // Map gender
        String gender = "OTHER";
        if ("Nam".equals(genderDisplay)) gender = "MALE";
        else if ("Nữ".equals(genderDisplay)) gender = "FEMALE";

        // 2. Validate dữ liệu
        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(dob) || TextUtils.isEmpty(phone)
                || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)
                || TextUtils.isEmpty(adminKey) || TextUtils.isEmpty(identityCard) || TextUtils.isEmpty(homeTown)) {
            showError("Vui lòng điền đầy đủ tất cả các trường.");
            return;
        }

        if (!isValidVietnamesePhoneNumber(phone)) {
            showError("Số điện thoại không hợp lệ.");
            return;
        }
        String normalizedPhone = normalizePhoneNumber(phone);

        if (password.length() < 6) {
            showError("Mật khẩu phải có ít nhất 6 ký tự.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Mật khẩu xác nhận không khớp.");
            return;
        }

        if (!ADMIN_SECRET_KEY.equals(adminKey)) {
            showError("Mã xác thực Ban Quản Trị không chính xác.");
            return;
        }

        // 3. Chuyển sang màn hình nhập OTP (Kèm theo toàn bộ dữ liệu)
        Intent intent = new Intent(this, EnterOTPActivity.class);
        intent.putExtra("phone", normalizedPhone);
        intent.putExtra("password", password);
        intent.putExtra("fullName", fullName);
        intent.putExtra("dob", dob);
        intent.putExtra("gender", gender);
        // 🔥 Truyền thêm 2 trường mới
        intent.putExtra("identity_card", identityCard);
        intent.putExtra("home_town", homeTown);

        intent.putExtra("is_admin_registration", true);
        intent.putExtra(EnterOTPActivity.EXTRA_PREVIOUS_ACTIVITY, EnterOTPActivity.FROM_REGISTER_PHONE);

        startActivity(intent);

        // Mở lại nút sau một khoảng thời gian ngắn (phòng trường hợp quay lại)
        btnRegister.postDelayed(() -> btnRegister.setEnabled(true), 2000);
    }

    // Helper lấy text an toàn
    private String getTextSafe(TextInputEditText edt) {
        return edt.getText() != null ? edt.getText().toString().trim() : "";
    }

    // Helper hiện lỗi và mở khóa nút
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        btnRegister.setEnabled(true);
    }
}