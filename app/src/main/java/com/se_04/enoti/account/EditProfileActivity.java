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
    // 🔥 Thêm 2 trường mới
    private TextInputEditText edtIdentityCard, edtHomeTown;
    private TextInputEditText edtRoom, edtFloor, edtRelation;
    private CheckBox checkboxIsHouseholder;
    private AutoCompleteTextView edtGender;
    private Button btnSubmit;
    private UserItem currentUser;
    private Toolbar toolbar;

    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private final SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

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

        checkboxIsHouseholder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            edtRelation.setEnabled(!isChecked);
            if (isChecked) {
                edtRelation.setText("Bản thân");
            } else {
                edtRelation.setText("");
            }
        });

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

        // 🔥 Ánh xạ view mới (Bạn cần đảm bảo ID này có trong XML)
        edtIdentityCard = findViewById(R.id.edtIdentityCard);
        edtHomeTown = findViewById(R.id.edtHomeTown);

        edtRoom = findViewById(R.id.edtRoom);
        edtFloor = findViewById(R.id.edtFloor);
        edtRelation = findViewById(R.id.edtRelation);
        checkboxIsHouseholder = findViewById(R.id.checkboxIsHouseholder);
        btnSubmit = findViewById(R.id.btnSubmit);

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
        edtFullName.setText(currentUser.getName());
        edtPhone.setText(currentUser.getPhone());
        edtEmail.setText(currentUser.getEmail());

        // 🔥 Load CCCD & Quê quán
        edtIdentityCard.setText(currentUser.getIdentityCard());
        edtHomeTown.setText(currentUser.getHomeTown());

        if (currentUser.getGender() != null) {
            String genderString = getGenderString(currentUser.getGender());
            edtGender.setText(genderString, false);
        }

        if (currentUser.getDob() != null && !currentUser.getDob().isEmpty()) {
            try {
                Date date = apiDateFormat.parse(currentUser.getDob());
                if (date != null) {
                    edtDob.setText(displayDateFormat.format(date));
                } else {
                    edtDob.setText(currentUser.getDob());
                }
            } catch (ParseException e) {
                edtDob.setText(currentUser.getDob());
            }
        }

        String currentRoom = String.valueOf(currentUser.getRoom());
        if (currentRoom != null && !currentRoom.isEmpty() && !"null".equals(currentRoom)) {
            edtRoom.setText(currentRoom);
            edtFloor.setText(calculateFloorFromRoom(currentRoom));
        }

        if (currentUser.getRelationship() != null) {
            edtRelation.setText(currentUser.getRelationship());
            boolean isHead = "Bản thân".equalsIgnoreCase(currentUser.getRelationship()) ||
                    "Chủ hộ".equalsIgnoreCase(currentUser.getRelationship());
            checkboxIsHouseholder.setChecked(isHead);
            edtRelation.setEnabled(!isHead);
        }
    }

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
        // 🔥 Lấy giá trị mới
        String identityCard = edtIdentityCard.getText().toString().trim();
        String homeTown = edtHomeTown.getText().toString().trim();

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
            // 🔥 Gửi thêm 2 trường mới
            body.put("identity_card", identityCard);
            body.put("home_town", homeTown);
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

    private String getGenderString(Gender gender) {
        if (gender == null) {
            return "";
        }
        switch (gender) {
            case MALE: return "Nam";
            case FEMALE: return "Nữ";
            case OTHER:
            default: return "Khác";
        }
    }
}