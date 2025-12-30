package com.se_04.enoti.account_related;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.button.MaterialButton; // 🔥 Import nút Material
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner; // 🔥 Import Scanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.BaseActivity;

import java.util.Calendar;
import java.util.Locale;

import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.isValidVietnamesePhoneNumber;
import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.normalizePhoneNumber;

public class RegisterActivity extends BaseActivity {

    private TextInputEditText edtFullName, edtDob, edtJob, edtPhoneNumber, edtPassword, edtConfirmPassword, edtAdminKey;
    private TextInputEditText edtEmail, edtIdentityCard, edtHomeTown;
    private Spinner spnGender;
    private Button btnRegister;
    private TextView textBackToLogin;
    private MaterialButton btnScanQR;
    private static final String KEY_ADMIN = "ENOTI_ADMIN_2024";
    private static final String KEY_ACCOUNTANT = "ENOTI_KT_2024";
    private static final String KEY_AGENCY = "ENOTI_CQCN_2024";

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
        edtJob = findViewById(R.id.edtJob);
        edtPhoneNumber = findViewById(R.id.edtPhoneNumber);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        edtAdminKey = findViewById(R.id.edtAdminKey);
        edtEmail = findViewById(R.id.edtEmail);
        edtIdentityCard = findViewById(R.id.edtIdentityCard);
        edtHomeTown = findViewById(R.id.edtHomeTown);
        spnGender = findViewById(R.id.spnGender);
        btnRegister = findViewById(R.id.btnRegister);
        textBackToLogin = findViewById(R.id.textBackToLogin);
        btnScanQR = findViewById(R.id.btnScanQR);
    }

    private void setupDatePicker() {
        edtDob.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, month + 1, year);
                edtDob.setText(selectedDate);
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void setupGenderSpinner() {
        String[] genders = {"Nam", "Nữ", "Khác"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, genders);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnGender.setAdapter(adapter);
    }

    private void setupListeners() {
        btnRegister.setOnClickListener(v -> handleRegistration());

        // 🔥 Sự kiện bấm nút quét QR
        if (btnScanQR != null) {
            btnScanQR.setOnClickListener(v -> startQRScanner());
        }

        textBackToLogin.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LogInActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    // ==================================================================
    // 📷 LOGIC QUÉT VÀ XỬ LÝ QR CCCD
    // ==================================================================
    private void startQRScanner() {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();

        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);

        scanner.startScan()
                .addOnSuccessListener(barcode -> {
                    String rawValue = barcode.getRawValue();
                    if (rawValue != null) {
                        parseCCCDData(rawValue);
                    } else {
                        Toast.makeText(this, "Không đọc được dữ liệu!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnCanceledListener(() -> {
                    // Người dùng bấm hủy, không làm gì
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi Camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void parseCCCDData(String rawData) {
        try {
            // Cấu trúc CCCD: CCCD|CMND(cũ)|Tên|NgàySinh(ddMMyyyy)|GiớiTinh|ĐịaChỉ|NgàyCấp
            String[] parts = rawData.split("\\|");

            if (parts.length >= 6) {
                String cccd = parts[0];
                String name = parts[2];
                String dobRaw = parts[3]; // format: ddMMyyyy
                String gender = parts[4];
                String address = parts[5];

                // 1. Điền Số CCCD
                if (edtIdentityCard != null) edtIdentityCard.setText(cccd);

                // 2. Điền Họ Tên (Viết hoa)
                edtFullName.setText(name.toUpperCase());

                // 3. Xử lý Ngày sinh (từ ddMMyyyy -> dd/MM/yyyy cho đúng định dạng App đang dùng)
                if (dobRaw.length() == 8) {
                    String day = dobRaw.substring(0, 2);
                    String month = dobRaw.substring(2, 4);
                    String year = dobRaw.substring(4, 8);
                    // Lưu ý: Ở RegisterActivity bạn dùng định dạng dd/MM/yyyy (dấu /)
                    edtDob.setText(day + "/" + month + "/" + year);
                }

                // 5. Chọn Giới tính
                if (gender.equalsIgnoreCase("Nam")) {
                    spnGender.setSelection(0);
                } else if (gender.equalsIgnoreCase("Nữ") || gender.equalsIgnoreCase("Nu")) {
                    spnGender.setSelection(1);
                } else {
                    spnGender.setSelection(2);
                }

                Toast.makeText(this, "✅ Đã điền thông tin từ CCCD!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Mã QR không đúng định dạng CCCD gắn chip!", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi xử lý dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================================================================

    private void handleRegistration() {
        btnRegister.setEnabled(false);

        // 1. Lấy dữ liệu
        String fullName = getTextSafe(edtFullName);
        String dob = getTextSafe(edtDob);
        String job = getTextSafe(edtJob);
        String phone = getTextSafe(edtPhoneNumber);
        String password = getTextSafe(edtPassword);
        String confirmPassword = getTextSafe(edtConfirmPassword);
        String secretKey = getTextSafe(edtAdminKey);
        String email = getTextSafe(edtEmail);
        String identityCard = getTextSafe(edtIdentityCard);
        String homeTown = getTextSafe(edtHomeTown);

        String genderDisplay = spnGender.getSelectedItem() != null ? spnGender.getSelectedItem().toString() : "";
        String gender = "OTHER";
        if ("Nam".equals(genderDisplay)) gender = "MALE";
        else if ("Nữ".equals(genderDisplay)) gender = "FEMALE";

        // 2. Validate
        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(dob) || TextUtils.isEmpty(phone)
                || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)
                || TextUtils.isEmpty(secretKey)) {
            showError("Vui lòng điền đầy đủ tất cả các trường có dấu *");
            return;
        }

        if (!TextUtils.isEmpty(email) && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Email không hợp lệ.");
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

        // 3. Logic Role
        String targetRole = "";
        if (KEY_ADMIN.equals(secretKey)) {
            targetRole = "ADMIN";
        } else if (KEY_ACCOUNTANT.equals(secretKey)) {
            targetRole = "ACCOUNTANT";
        } else if (KEY_AGENCY.equals(secretKey)) {
            targetRole = "AGENCY";
        } else {
            showError("Mã xác thực không đúng. Vui lòng kiểm tra lại.");
            return;
        }

        // 4. Chuyển sang OTP Activity
        Intent intent = new Intent(this, EnterOTPActivity.class);
        intent.putExtra("phone", normalizedPhone);
        intent.putExtra("password", password);
        intent.putExtra("fullName", fullName);
        intent.putExtra("dob", dob);
        intent.putExtra("job", job);
        intent.putExtra("gender", gender);
        intent.putExtra("email", email);
        intent.putExtra("identity_card", identityCard);
        intent.putExtra("home_town", homeTown);
        intent.putExtra("target_role", targetRole);
        intent.putExtra(EnterOTPActivity.EXTRA_PREVIOUS_ACTIVITY, EnterOTPActivity.FROM_REGISTER_PHONE);

        startActivity(intent);
        btnRegister.postDelayed(() -> btnRegister.setEnabled(true), 2000);
    }

    private String getTextSafe(TextInputEditText edt) {
        return edt.getText() != null ? edt.getText().toString().trim() : "";
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        btnRegister.setEnabled(true);
    }
}