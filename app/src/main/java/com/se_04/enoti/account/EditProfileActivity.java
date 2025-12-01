package com.se_04.enoti.account;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class EditProfileActivity extends AppCompatActivity {

    private TextInputEditText edtFullName, edtPhone, edtEmail, edtDob;
    private TextInputEditText edtRoom, edtFloor, edtRelation;
    private CheckBox checkboxIsHouseholder;
    private AutoCompleteTextView edtGender;
    private Button btnSubmit;
    private UserItem currentUser;
    private Toolbar toolbar;

    // Định dạng ngày: Hiển thị (dd-MM-yyyy) và API (yyyy-MM-dd)
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private final SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        // 🔥 FIX: Tắt tính năng tự động sửa lỗi ngày tháng để tránh ra năm 0007
        apiDateFormat.setLenient(false);
        displayDateFormat.setLenient(false);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chỉnh sửa thông tin");
            toolbar.setTitleTextColor(Color.WHITE);
        }

        initViews();
        setupGenderDropdown();
        setupDatePicker();

        // Xử lý Checkbox chủ hộ
        checkboxIsHouseholder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            edtRelation.setEnabled(!isChecked);
            if (isChecked) {
                edtRelation.setText("Bản thân");
            } else {
                edtRelation.setText("");
            }
        });

        // Load thông tin hiện tại
        currentUser = UserManager.getInstance(this).getCurrentUser();
        if (currentUser != null) {
            loadUserData();
        }

        btnSubmit.setOnClickListener(v -> submitUpdate());
    }

    private void initViews() {
        edtFullName = findViewById(R.id.edtFullName);
        edtPhone = findViewById(R.id.edtPhone);
        edtEmail = findViewById(R.id.edtEmail);
        edtGender = findViewById(R.id.edtGender);
        edtDob = findViewById(R.id.edtDob);

        edtRoom = findViewById(R.id.edtRoom);
        edtFloor = findViewById(R.id.edtFloor);
        edtRelation = findViewById(R.id.edtRelation);
        checkboxIsHouseholder = findViewById(R.id.checkboxIsHouseholder);
        btnSubmit = findViewById(R.id.btnSubmit);

        // 🔥 FIX: Không cho phép sửa Phòng và Tầng (Read-only)
        edtRoom.setEnabled(false);
        edtFloor.setEnabled(false);
    }

    private void setupGenderDropdown() {
        String[] genders = new String[]{"Nam", "Nữ", "Khác"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, genders);
        edtGender.setAdapter(adapter);
    }

    private void setupDatePicker() {
        edtDob.setOnClickListener(v -> showDatePicker());
        edtDob.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showDatePicker();
        });
    }

    private void showDatePicker() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        String currentDob = edtDob.getText().toString();
        if (!currentDob.isEmpty()) {
            try {
                Date date = displayDateFormat.parse(currentDob);
                if (date != null) {
                    c.setTime(date);
                    year = c.get(Calendar.YEAR);
                    month = c.get(Calendar.MONTH);
                    day = c.get(Calendar.DAY_OF_MONTH);
                }
            } catch (ParseException ignored) {}
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    Calendar selectedCal = Calendar.getInstance();
                    selectedCal.set(year1, monthOfYear, dayOfMonth);
                    edtDob.setText(displayDateFormat.format(selectedCal.getTime()));
                }, year, month, day);
        datePickerDialog.show();
    }

    private void loadUserData() {
        // 1. Thông tin cơ bản
        edtFullName.setText(currentUser.getName());
        edtPhone.setText(currentUser.getPhone());
        edtEmail.setText(currentUser.getEmail());

        // 2. Giới tính (Xử lý Enum -> String)
        if (currentUser.getGender() != null) {
            String genderString = getGenderString(currentUser.getGender());
            edtGender.setText(genderString, false);
        }

        // 3. Ngày sinh (Xử lý thông minh format API vs Display)
        if (currentUser.getDob() != null && !currentUser.getDob().isEmpty()) {
            try {
                // Thử parse theo format API trước (yyyy-MM-dd)
                Date date = apiDateFormat.parse(currentUser.getDob());
                if (date != null) {
                    edtDob.setText(displayDateFormat.format(date));
                } else {
                    // Nếu không phải format API, thử hiển thị trực tiếp
                    edtDob.setText(currentUser.getDob());
                }
            } catch (ParseException e) {
                // Nếu lỗi, thử hiển thị trực tiếp (có thể nó đã là dd-MM-yyyy)
                edtDob.setText(currentUser.getDob());
            }
        }

        // 4. Phòng & Tầng (Tự động tính)
        // Lưu ý: getRoom() trả về Object/Int, cần convert sang String an toàn
        String currentRoom = String.valueOf(currentUser.getRoom());
        if (currentRoom != null && !currentRoom.isEmpty() && !"null".equals(currentRoom)) {
            edtRoom.setText(currentRoom);
            edtFloor.setText(calculateFloorFromRoom(currentRoom));
        }

        // 5. Quan hệ & Chủ hộ
        if (currentUser.getRelationship() != null) {
            edtRelation.setText(currentUser.getRelationship());
            boolean isHead = "Bản thân".equalsIgnoreCase(currentUser.getRelationship()) ||
                    "Chủ hộ".equalsIgnoreCase(currentUser.getRelationship());
            checkboxIsHouseholder.setChecked(isHead);
            edtRelation.setEnabled(!isHead);
        }
    }

    /**
     * 🔥 Logic tính tầng: Số phòng bỏ đi 2 chữ số cuối
     * Ví dụ: 1204 -> 12, 501 -> 5
     */
    private String calculateFloorFromRoom(String room) {
        if (room == null || room.length() <= 2) return "";
        return room.substring(0, room.length() - 2);
    }

    private void submitUpdate() {
        String fullName = edtFullName.getText().toString().trim();
        String phone = edtPhone.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String gender = edtGender.getText().toString().trim();
        String dobDisplay = edtDob.getText().toString().trim();

        // Lấy thông tin phòng/tầng (dù không sửa được nhưng cần thiết để kiểm tra logic)
        // Lưu ý: Không cần gửi lên server nếu server không cho phép update
        boolean isHead = checkboxIsHouseholder.isChecked();
        String relation = isHead ? "Bản thân" : edtRelation.getText().toString().trim();

        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(phone) || TextUtils.isEmpty(dobDisplay)) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ: Họ tên, SĐT, Ngày sinh", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isHead && TextUtils.isEmpty(relation)) {
            Toast.makeText(this, "Vui lòng nhập quan hệ với chủ hộ!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert ngày hiển thị (dd-MM-yyyy) -> API (yyyy-MM-dd)
        String dobApi = null;
        try {
            Date date = displayDateFormat.parse(dobDisplay);
            if (date != null) dobApi = apiDateFormat.format(date);
        } catch (ParseException e) {
            Toast.makeText(this, "Định dạng ngày sinh lỗi", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("user_id", Integer.parseInt(currentUser.getId()));
            body.put("full_name", fullName);
            body.put("phone", phone);
            body.put("email", email);
            body.put("gender", gender);
            body.put("dob", dobApi);
            body.put("relationship", relation);
            body.put("is_head", isHead);
            // Không gửi room & floor vì người dùng không được phép sửa
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String url = ApiConfig.BASE_URL + "/api/profile-requests/create";

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    try {
                        if (response.has("success") && response.getBoolean("success")) {
                            Toast.makeText(this, "Gửi yêu cầu thành công! Vui lòng chờ duyệt.", Toast.LENGTH_LONG).show();
                            finish();
                        } else {
                            String msg = response.optString("error", "Có lỗi xảy ra");
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(this, "Lỗi kết nối hoặc yêu cầu đang chờ duyệt", Toast.LENGTH_SHORT).show()
        );

        Volley.newRequestQueue(this).add(request);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Helper chuyển đổi Enum Gender sang String tiếng Việt
    private String getGenderString(Gender gender) {
        if (gender == null) {
            return "";
        }
        switch (gender) {
            case MALE:
                return "Nam";
            case FEMALE:
                return "Nữ";
            case OTHER:
            default:
                return "Khác";
        }
    }
}
