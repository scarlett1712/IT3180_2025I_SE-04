package com.se_04.enoti.residents;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateResidentActivity extends BaseActivity {

    private TextInputEditText edtFullName, edtBirthDate, edtJob, edtRelation, edtPhone, edtEmail, edtRoom, edtFloor, edtIdentityCard, edtHomeTown;
    private Spinner spinnerGender;
    private CheckBox checkboxIsHouseholder;
    private MaterialButton btnSaveResident, btnCancel;
    private RequestQueue requestQueue;

    // URL API
    private static final String CREATE_URL = ApiConfig.BASE_URL + "/api/create_user/create";

    // Định dạng ngày: Hiển thị (dd-MM-yyyy) và Gửi đi (yyyy-MM-dd)
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private final SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_resident);

        initViews();
        requestQueue = Volley.newRequestQueue(this);

        // Setup Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar_add_resident);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Thêm cư dân mới");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Setup DatePicker
        edtBirthDate.setOnClickListener(v -> showDatePickerDialog());

        // Setup Spinner
        setupGenderSpinner();

        // Logic Checkbox Chủ hộ
        checkboxIsHouseholder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // TRƯỜNG HỢP: Tích chọn là Chủ Hộ
                edtRelation.setText("Bản thân");      // 1. Tự động điền
                edtRelation.setEnabled(false);        // 2. Khóa không cho sửa
                edtRelation.setAlpha(0.7f);           // 3. Làm mờ đi cho dễ nhận biết
                edtRelation.setError(null);           // 4. Xóa báo lỗi cũ (nếu có)
            } else {
                // TRƯỜNG HỢP: Bỏ chọn (Là thành viên)
                edtRelation.setText("");              // 1. Xóa chữ "Bản thân"
                edtRelation.setEnabled(true);         // 2. Mở khóa cho nhập lại
                edtRelation.setAlpha(1.0f);           // 3. Làm sáng lại
                edtRelation.requestFocus();           // 4. Focus vào để nhập luôn
            }
        });

        // Sự kiện nút Lưu & Hủy
        btnSaveResident.setOnClickListener(v -> createResident());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void initViews() {
        edtFullName = findViewById(R.id.edtFullName);
        edtBirthDate = findViewById(R.id.edtBirthDate);
        edtJob = findViewById(R.id.edtJob);
        edtRelation = findViewById(R.id.edtRelation);
        edtPhone = findViewById(R.id.edtPhone);
        edtEmail = findViewById(R.id.edtEmail);
        edtRoom = findViewById(R.id.edtRoom);
        edtFloor = findViewById(R.id.edtFloor);
        edtIdentityCard = findViewById(R.id.edtIdentityCard);
        edtHomeTown = findViewById(R.id.edtHomeTown);
        spinnerGender = findViewById(R.id.spinnerGender);
        checkboxIsHouseholder = findViewById(R.id.checkboxIsHouseholder);
        btnSaveResident = findViewById(R.id.btnSaveResident);
        btnCancel = findViewById(R.id.btnCancel);

        // --- CẤU HÌNH INPUT TYPE (QUAN TRỌNG) ---

        // 1. Số điện thoại: Chỉ nhập số
        edtPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        edtPhone.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});

        // 2. CCCD: Chỉ nhập số
        if (edtIdentityCard != null) {
            edtIdentityCard.setInputType(InputType.TYPE_CLASS_NUMBER);
            edtIdentityCard.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        }

        // 3. Số phòng & Tầng: Chỉ nhập số dương (TYPE_CLASS_NUMBER)
        // Vì bạn yêu cầu "chỉ là dương, không có chữ cái"
        edtRoom.setInputType(InputType.TYPE_CLASS_NUMBER);
        edtFloor.setEnabled(false); // Không cho nhập
        edtFloor.setText("Tự động"); // Điền chữ để user hiểu
        edtFloor.setAlpha(0.7f); // Làm mờ đi một chút

        // Ngày sinh: Không cho gõ phím, phải chọn lịch
        edtBirthDate.setFocusable(false);
        edtBirthDate.setClickable(true);
    }

    private void setupGenderSpinner() {
        String[] genders = {"Nam", "Nữ", "Khác"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, genders);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(adapter);
    }

    private void showDatePickerDialog() {
        final Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePicker = new DatePickerDialog(this, (view, y, m, d) -> {
            String formatted = String.format(Locale.getDefault(), "%02d-%02d-%04d", d, m + 1, y);
            edtBirthDate.setText(formatted);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        datePicker.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePicker.show();
    }

    // Kiểm tra định dạng SĐT Việt Nam cơ bản
    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^(0|\\+84)[0-9]{9}$");
    }

    private void createResident() {
        // Lấy dữ liệu thô
        String fullName = edtFullName.getText().toString().trim();
        String birthDateDisplay = edtBirthDate.getText().toString().trim();
        String phoneRaw = edtPhone.getText().toString().trim();
        String identityCard = edtIdentityCard != null ? edtIdentityCard.getText().toString().trim() : "";
        String floorRaw = edtFloor.getText().toString().trim();
        String roomRaw = edtRoom.getText().toString().trim();

        // 1. Validate bắt buộc
        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(birthDateDisplay) || TextUtils.isEmpty(phoneRaw)) {
            Toast.makeText(this, "Vui lòng nhập Tên, Ngày sinh và SĐT!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Validate Phòng
        if (TextUtils.isEmpty(roomRaw)) {
            edtRoom.setError("Vui lòng nhập số phòng");
            return;
        }

        // 3. Validate SĐT
        if (!isValidPhone(phoneRaw)) {
            edtPhone.setError("SĐT không hợp lệ");
            return;
        }

        // 4. Validate CCCD (Nếu có nhập)
        if (!identityCard.isEmpty() && identityCard.length() != 9 && identityCard.length() != 12) {
            edtIdentityCard.setError("CCCD phải là 9 hoặc 12 số");
            return;
        }

        // Chuẩn hóa dữ liệu gửi đi
        String phoneForDb = phoneRaw.startsWith("0") ? "+84" + phoneRaw.substring(1) : phoneRaw;

        // Vì bạn nói phòng chỉ là số dương, ta dùng replaceAll để chắc chắn loại bỏ ký tự lạ (an toàn)
        String room = roomRaw.replaceAll("\\D", "");
        String floor = floorRaw.replaceAll("\\D", "");

        boolean isHead = checkboxIsHouseholder.isChecked();
        String relation = isHead ? "Bản thân" : edtRelation.getText().toString().trim();
        if (!isHead && relation.isEmpty()) {
            edtRelation.setError("Vui lòng nhập quan hệ");
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("phone", phoneForDb);
            body.put("full_name", fullName);
            body.put("gender", spinnerGender.getSelectedItem().toString());
            body.put("dob", apiFormat.format(displayFormat.parse(birthDateDisplay)));
            body.put("job", edtJob.getText().toString().trim());
            body.put("email", edtEmail.getText().toString().trim());
            body.put("room", room);
            body.put("floor", "0");
            body.put("is_head", isHead);
            body.put("relationship_name", relation);
            body.put("identity_card", identityCard);
            body.put("home_town", edtHomeTown.getText().toString().trim());

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, CREATE_URL, body,
                    response -> {
                        Toast.makeText(this, "✅ Tạo cư dân thành công!", Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK); // Báo cho Activity cha biết là đã thêm OK
                        finish();
                    },
                    error -> {
                        // Xử lý hiển thị lỗi chi tiết từ Server
                        String message = "Lỗi kết nối máy chủ";
                        if (error.networkResponse != null && error.networkResponse.data != null) {
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                JSONObject data = new JSONObject(responseBody);

                                // Ưu tiên lấy thông báo lỗi từ các key phổ biến
                                message = data.optString("error", data.optString("message", ""));

                                if (message.isEmpty()) {
                                    if (error.networkResponse.statusCode == 409) {
                                        message = "Dữ liệu bị trùng (SĐT hoặc Phòng đã có chủ)!";
                                    } else if (error.networkResponse.statusCode == 400) {
                                        message = "Dữ liệu đầu vào không hợp lệ.";
                                    }
                                }
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                        Toast.makeText(this, "❌ " + message, Toast.LENGTH_LONG).show();
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                    if (token != null) {
                        headers.put("Authorization", "Bearer " + token);
                    }
                    return headers;
                }
            };
            requestQueue.add(request);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi xử lý dữ liệu nội bộ", Toast.LENGTH_SHORT).show();
        }
    }
}