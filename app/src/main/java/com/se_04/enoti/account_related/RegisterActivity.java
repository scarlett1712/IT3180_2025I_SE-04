package com.se_04.enoti.account_related;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView; // 🔥 Import thêm TextView
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;

import java.util.Calendar;
import java.util.Locale;

// Import các hàm validate có sẵn của bạn
import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.isValidVietnamesePhoneNumber;
import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.normalizePhoneNumber;

public class RegisterActivity extends AppCompatActivity {

    // Khai báo view
    private TextInputEditText edtFullName, edtDob, edtPhoneNumber, edtPassword, edtConfirmPassword, edtAdminKey;
    private Spinner spnGender;
    private Button btnRegister;
    private TextView textBackToLogin; // 🔥 Biến mới cho nút "Đã có tài khoản?"

    // Key xác thực admin (Hardcode hoặc lấy từ config)
    private static final String ADMIN_SECRET_KEY = "ENOTI_ADMIN_2024";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Lưu ý: Đảm bảo tên layout trùng với file XML bạn vừa tạo (ví dụ: activity_register_admin)
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
        spnGender = findViewById(R.id.spnGender);
        btnRegister = findViewById(R.id.btnRegister);

        // 🔥 Ánh xạ text view mới
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
        // Sự kiện nút Đăng ký
        btnRegister.setOnClickListener(v -> handleRegistration());

        // 🔥 Sự kiện nút "Đã có tài khoản? Đăng nhập"
        textBackToLogin.setOnClickListener(v -> {
            // Chuyển về màn hình LoginActivity
            Intent intent = new Intent(RegisterActivity.this, LogInActivity.class);
            // Xóa stack cũ để tránh người dùng ấn Back lại quay về màn đăng ký
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void handleRegistration() {
        // 1. Lấy dữ liệu
        String fullName = edtFullName.getText() != null ? edtFullName.getText().toString().trim() : "";
        String dob = edtDob.getText() != null ? edtDob.getText().toString().trim() : "";
        String phone = edtPhoneNumber.getText() != null ? edtPhoneNumber.getText().toString().trim() : "";
        String password = edtPassword.getText() != null ? edtPassword.getText().toString().trim() : "";
        String confirmPassword = edtConfirmPassword.getText() != null ? edtConfirmPassword.getText().toString().trim() : "";
        String adminKey = edtAdminKey.getText() != null ? edtAdminKey.getText().toString().trim() : "";

        String genderDisplay = spnGender.getSelectedItem() != null
                ? spnGender.getSelectedItem().toString()
                : "";

        // Map gender sang Enum backend
        String gender = "OTHER";
        if ("Nam".equals(genderDisplay)) gender = "MALE";
        else if ("Nữ".equals(genderDisplay)) gender = "FEMALE";

        // 2. Kiểm tra dữ liệu (Validate)
        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(dob)
                || TextUtils.isEmpty(genderDisplay) || TextUtils.isEmpty(phone)
                || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)
                || TextUtils.isEmpty(adminKey)) {
            Toast.makeText(this, "Vui lòng điền đầy đủ tất cả các trường.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isValidVietnamesePhoneNumber(phone)) {
            Toast.makeText(this, "Số điện thoại không hợp lệ. Vui lòng nhập số Việt Nam.", Toast.LENGTH_LONG).show();
            return;
        }
        String normalizedPhone = normalizePhoneNumber(phone);

        if (password.length() < 6) {
            Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Mật khẩu xác nhận không khớp.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Kiểm tra mã BQT
        if (!ADMIN_SECRET_KEY.equals(adminKey)) {
            Toast.makeText(this, "Mã xác thực Ban Quản Trị không chính xác.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. Chuyển sang màn hình nhập OTP
        Intent intent = new Intent(this, EnterOTPActivity.class);
        intent.putExtra("phone", normalizedPhone);
        intent.putExtra("password", password);
        intent.putExtra("fullName", fullName);
        intent.putExtra("dob", dob);
        intent.putExtra("gender", gender);
        intent.putExtra("is_admin_registration", true);

        // Gửi cờ báo hiệu đây là luồng đăng ký
        intent.putExtra(EnterOTPActivity.EXTRA_PREVIOUS_ACTIVITY, EnterOTPActivity.FROM_REGISTER_PHONE);

        startActivity(intent);
    }
}