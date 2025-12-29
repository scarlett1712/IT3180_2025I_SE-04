package com.se_04.enoti.residents;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
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

    private TextInputEditText edtFullName, edtBirthDate, edtJob, edtRelation, edtPhone, edtEmail, edtRoom, edtFloor, edtIdentityCard, edtHomeTown;
    private Spinner spinnerGender;
    private CheckBox checkboxIsHouseholder;
    private MaterialButton btnSaveResident, btnCancel;
    private RequestQueue requestQueue;

    private static final String BASE_URL = ApiConfig.BASE_URL + "/api/create_user/create";
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private final SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_resident);

        initViews();
        requestQueue = Volley.newRequestQueue(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_add_resident);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Thêm cư dân mới");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        edtBirthDate.setOnClickListener(v -> showDatePickerDialog());
        setupGenderSpinner();

        checkboxIsHouseholder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            edtRelation.setEnabled(!isChecked);
            if (isChecked) edtRelation.setText("");
        });

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

        // Giới hạn nhập liệu 12 ký tự để chứa vừa đủ +84XXXXXXXXX
        edtPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        edtPhone.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});

        if (edtIdentityCard != null) {
            edtIdentityCard.setInputType(InputType.TYPE_CLASS_NUMBER);
            edtIdentityCard.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        }

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

    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^(0|\\+84)[0-9]{9}$");
    }

    private void createResident() {
        String fullName = edtFullName.getText().toString().trim();
        String birthDateDisplay = edtBirthDate.getText().toString().trim();
        String phoneRaw = edtPhone.getText().toString().trim();
        String identityCard = edtIdentityCard != null ? edtIdentityCard.getText().toString().trim() : "";
        String floorRaw = edtFloor.getText().toString().trim();

        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(birthDateDisplay) || TextUtils.isEmpty(phoneRaw)) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin bắt buộc!", Toast.LENGTH_SHORT).show();
            return;
        }
        int floorValue = 0;
        try {
            floorValue = Integer.parseInt(floorRaw.replaceAll("\\D", ""));
        } catch (Exception e) { floorValue = 0; }

        if (floorValue <= 0) {
            edtFloor.setError("Tầng phải bắt đầu từ tầng 1!");
            Toast.makeText(this, "Số tầng không hợp lệ!", Toast.LENGTH_SHORT).show();
            edtFloor.requestFocus();
            return;
        }

        if (!isValidPhone(phoneRaw)) {
            Toast.makeText(this, "Số điện thoại không hợp lệ!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!identityCard.isEmpty() && identityCard.length() != 9 && identityCard.length() != 12) {
            Toast.makeText(this, "Số CCCD/CMND phải là 9 hoặc 12 chữ số!", Toast.LENGTH_SHORT).show();
            return;
        }

        String phoneForDb;
        if (phoneRaw.startsWith("0")) {
            phoneForDb = "+84" + phoneRaw.substring(1);
        } else {
            phoneForDb = phoneRaw;
        }

        String floor = edtFloor.getText().toString().trim().replaceAll("\\D", "");
        String room = edtRoom.getText().toString().trim().replaceAll("\\D", "");
        boolean isHead = checkboxIsHouseholder.isChecked();
        String relation = isHead ? "Bản thân" : edtRelation.getText().toString().trim();

        try {
            JSONObject body = new JSONObject();
            body.put("phone", phoneForDb); // Sử dụng số đã định dạng +84
            body.put("full_name", fullName);
            body.put("gender", spinnerGender.getSelectedItem().toString());
            body.put("dob", apiFormat.format(displayFormat.parse(birthDateDisplay)));
            body.put("job", edtJob.getText().toString().trim());
            body.put("email", edtEmail.getText().toString().trim());
            body.put("room", room);
            body.put("floor", floor);
            body.put("is_head", isHead);
            body.put("relationship_name", relation);
            body.put("identity_card", identityCard);
            body.put("home_town", edtHomeTown.getText().toString().trim());

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, BASE_URL, body,
                    response -> {
                        Toast.makeText(this, "Tạo cư dân thành công!", Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    },
                    error -> {
                        if (error.networkResponse != null) {
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                JSONObject data = new JSONObject(responseBody);
                                String serverMsg = data.optString("message", data.optString("error", data.optString("msg", "")));

                                if (serverMsg.isEmpty()) {
                                    if (error.networkResponse.statusCode == 400 || error.networkResponse.statusCode == 409) {
                                        serverMsg = "Dữ liệu đã tồn tại trong hệ thống!";
                                    } else {
                                        serverMsg = "Lỗi không xác định từ máy chủ.";
                                    }
                                }

                                String lowerMsg = serverMsg.toLowerCase();
                                if (lowerMsg.contains("phone") || lowerMsg.contains("số điện thoại")) {
                                    Toast.makeText(this, "Số điện thoại đã được đăng ký!", Toast.LENGTH_LONG).show();
                                } else if (lowerMsg.contains("identity") || lowerMsg.contains("cccd")) {
                                    Toast.makeText(this, "Số CCCD/CMND đã tồn tại!", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(this, "Lỗi: " + serverMsg, Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                Toast.makeText(this, "Dữ liệu trùng lặp hoặc không hợp lệ!", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(this, "Lỗi kết nối máy chủ!", Toast.LENGTH_SHORT).show();
                        }
                    }
            );
            requestQueue.add(request);
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi xử lý dữ liệu!", Toast.LENGTH_SHORT).show();
        }
    }
}