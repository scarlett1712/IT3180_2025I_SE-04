package com.se_04.enoti.account_related;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;

import java.util.Calendar;
import java.util.Locale;

import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.isValidVietnamesePhoneNumber;
import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.normalizePhoneNumber;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText edtFullName, edtDob, edtPhoneNumber, edtPassword, edtConfirmPassword, edtAdminKey;
    private MaterialAutoCompleteTextView spnGender;
    private Button btnRegister;

    private static final String ADMIN_SECRET_KEY = "ENOTI_ADMIN_2024";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Ánh xạ view
        edtFullName = findViewById(R.id.edtFullName);
        edtDob = findViewById(R.id.edtDob);
        edtPhoneNumber = findViewById(R.id.edtPhoneNumber);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        edtAdminKey = findViewById(R.id.edtAdminKey);
        spnGender = findViewById(R.id.spnGender);
        btnRegister = findViewById(R.id.btnRegister);

        setupDatePicker();
        setupGenderSpinner();

        btnRegister.setOnClickListener(v -> handleRegistration());
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
                this, android.R.layout.simple_dropdown_item_1line, genders);
        spnGender.setAdapter(adapter);
    }

    private void handleRegistration() {
        String fullName = edtFullName.getText().toString().trim();
        String dob = edtDob.getText().toString().trim();
        String gender = spnGender.getText().toString().trim();
        String phone = edtPhoneNumber.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();
        String confirmPassword = edtConfirmPassword.getText().toString().trim();
        String adminKey = edtAdminKey.getText().toString().trim();

        // === 1. Kiểm tra trống ===
        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(dob)
                || TextUtils.isEmpty(gender) || TextUtils.isEmpty(phone)
                || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)
                || TextUtils.isEmpty(adminKey)) {
            Toast.makeText(this, "Vui lòng điền đầy đủ tất cả các trường.", Toast.LENGTH_SHORT).show();
            return;
        }

        // === 2. Kiểm tra số điện thoại ===
        if (!isValidVietnamesePhoneNumber(phone)) {
            Toast.makeText(this,
                    "Số điện thoại không hợp lệ. Vui lòng nhập số Việt Nam (bắt đầu bằng 0 hoặc +84).",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Chuẩn hóa số điện thoại sang dạng +84...
        String normalizedPhone = normalizePhoneNumber(phone);

        // === 3. Kiểm tra mật khẩu ===
        if (password.length() < 6) {
            Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Mật khẩu xác nhận không khớp.", Toast.LENGTH_SHORT).show();
            return;
        }

        // === 4. Kiểm tra mã xác thực quản trị ===
        if (!ADMIN_SECRET_KEY.equals(adminKey)) {
            Toast.makeText(this, "Mã xác thực Ban Quản Trị không chính xác.", Toast.LENGTH_SHORT).show();
            return;
        }

        // === 5. Nếu mọi thứ hợp lệ, chuyển sang OTP ===
        Intent intent = new Intent(this, EnterOTPActivity.class);
        intent.putExtra("phone", normalizedPhone);
        intent.putExtra("password", password);
        intent.putExtra("fullName", fullName);
        intent.putExtra("dob", dob);
        intent.putExtra("gender", gender); // 👈 Truyền giới tính
        intent.putExtra("is_admin_registration", true);
        startActivity(intent);
    }
}
