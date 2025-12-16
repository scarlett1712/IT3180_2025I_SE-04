package com.se_04.enoti.residents;

import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.normalizePhoneNumber;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CreateResidentActivity extends BaseActivity {

    // 🔥 Đã thêm edtIdentityCard, edtHomeTown vào danh sách biến
    private TextInputEditText edtFullName, edtBirthDate, edtJob, edtRelation, edtPhone, edtEmail, edtRoom, edtFloor, edtIdentityCard, edtHomeTown;
    private Spinner spinnerGender;
    private CheckBox checkboxIsHouseholder;
    private MaterialButton btnSaveResident, btnCancel;
    private RequestQueue requestQueue;

    // ⚙️ API endpoint
    private static final String BASE_URL = ApiConfig.BASE_URL + "/api/create_user/create";

    // ⚙️ Date formatters
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private final SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_resident);

        initViews();
        requestQueue = Volley.newRequestQueue(this);

        // Toolbar back
        MaterialToolbar toolbar = findViewById(R.id.toolbar_add_resident);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("Thêm cư dân mới");
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Date picker
        edtBirthDate.setOnClickListener(v -> showDatePickerDialog());

        // Gender spinner
        setupGenderSpinner();

        // Checkbox chủ hộ
        checkboxIsHouseholder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            edtRelation.setEnabled(!isChecked);
            if (isChecked) edtRelation.setText("");
        });

        // Buttons
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

        // 🔥 Ánh xạ view mới (Bạn cần đảm bảo ID này tồn tại trong layout XML)
        edtIdentityCard = findViewById(R.id.edtIdentityCard);
        edtHomeTown = findViewById(R.id.edtHomeTown);

        spinnerGender = findViewById(R.id.spinnerGender);
        checkboxIsHouseholder = findViewById(R.id.checkboxIsHouseholder);
        btnSaveResident = findViewById(R.id.btnSaveResident);
        btnCancel = findViewById(R.id.btnCancel);
    }

    private void setupGenderSpinner() {
        String[] genders = {"Nam", "Nữ", "Khác"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, genders);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(adapter);
    }

    private void showDatePickerDialog() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePicker = new DatePickerDialog(this,
                (view, y, m, d) -> {
                    // Hiển thị dạng dd-MM-yyyy
                    String formatted = String.format(Locale.getDefault(), "%02d-%02d-%04d", d, m + 1, y);
                    edtBirthDate.setText(formatted);
                },
                year, month, day);
        datePicker.show();
    }

    private void createResident() {
        String fullName = edtFullName.getText().toString().trim();
        String birthDateDisplay = edtBirthDate.getText().toString().trim();
        String job = edtJob.getText().toString().trim();
        String gender = spinnerGender.getSelectedItem().toString();
        String floorInput = edtFloor.getText().toString().trim();
        String roomInput = edtRoom.getText().toString().trim();
        boolean isHead = checkboxIsHouseholder.isChecked();
        String relation;
        if (isHead) relation = "Bản thân";
        else relation =  edtRelation.getText().toString().trim();
        String phoneBeforeNormalized = edtPhone.getText().toString().trim();
        String phone = normalizePhoneNumber(phoneBeforeNormalized);
        String email = edtEmail.getText().toString().trim();

        // 🔥 Lấy dữ liệu mới (Kiểm tra null để tránh lỗi nếu view chưa được khởi tạo)
        String identityCard = edtIdentityCard != null ? edtIdentityCard.getText().toString().trim() : "";
        String homeTown = edtHomeTown != null ? edtHomeTown.getText().toString().trim() : "";

        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(birthDateDisplay) || TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin bắt buộc!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isHead && TextUtils.isEmpty(relation)) {
            Toast.makeText(this, "Vui lòng nhập quan hệ với chủ hộ!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Chuyển định dạng ngày dd-MM-yyyy -> yyyy-MM-dd
        String birthDateApi;
        try {
            birthDateApi = apiFormat.format(displayFormat.parse(birthDateDisplay));
        } catch (ParseException e) {
            Toast.makeText(this, "Định dạng ngày sinh không hợp lệ!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🧹 Lọc số từ input phòng và tầng
        String floor = floorInput.replaceAll("\\D", "");  // chỉ lấy số
        String room = roomInput.replaceAll("\\D", "");    // chỉ lấy số

        if (floor.isEmpty() || room.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tầng và phòng hợp lệ (chứa số)!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("phone", phone);
            body.put("full_name", fullName);
            body.put("gender", gender);
            body.put("dob", birthDateApi);  // yyyy-MM-dd
            body.put("job", job);
            body.put("email", email);
            body.put("room", room);
            body.put("floor", floor);
            body.put("is_head", isHead);
            body.put("relationship_name", relation);

            // 🔥 Gửi thêm 2 trường mới lên server
            body.put("identity_card", identityCard);
            body.put("home_town", homeTown);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    BASE_URL,
                    body,
                    response -> {
                        Toast.makeText(this, "Tạo cư dân thành công!", Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    },
                    error -> {
                        String message = (error.getMessage() != null) ? error.getMessage() : "Không thể kết nối máy chủ.";
                        Toast.makeText(this, "Lỗi khi tạo cư dân: " + message, Toast.LENGTH_LONG).show();
                    }
            );

            requestQueue.add(request);
        } catch (JSONException e) {
            Toast.makeText(this, "Lỗi khi xử lý dữ liệu gửi đi!", Toast.LENGTH_SHORT).show();
        }
    }
}