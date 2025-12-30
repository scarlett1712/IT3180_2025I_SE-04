package com.se_04.enoti.residents;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateResidentActivity extends BaseActivity {

    // Khai báo View
    private TextInputEditText edtFullName, edtBirthDate, edtJob, edtRelation, edtPhone, edtEmail, edtIdentityCard, edtHomeTown;
    private Spinner spinnerGender;

    // 🔥 Thay thế EditText bằng Spinner cho Tầng và Phòng
    private Spinner spnFloor, spnRoom;

    private CheckBox checkboxIsHouseholder;
    private MaterialButton btnSaveResident, btnCancel, btnScanQR;
    private RequestQueue requestQueue;

    // URL API
    private static final String CREATE_URL = ApiConfig.BASE_URL + "/api/create_user/create";
    // 🔥 URL lấy danh sách phòng để đổ vào Spinner
    private static final String APARTMENT_LIST_URL = ApiConfig.BASE_URL + "/api/residents/list-for-selection";

    // Định dạng ngày
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private final SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // 🔥 Dữ liệu lưu trữ cho Spinner Phòng
    private Map<String, List<String>> floorRoomsMap = new HashMap<>();
    private List<String> floorList = new ArrayList<>();
    private List<String> currentRoomList = new ArrayList<>();

    private ArrayAdapter<String> floorAdapter;
    private ArrayAdapter<String> roomAdapter;
    private String selectedRoomNumber = "";

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

        // Setup các thành phần
        edtBirthDate.setOnClickListener(v -> showDatePickerDialog());
        setupGenderSpinner();

        // 🔥 Setup Spinner Phòng & Tầng + Gọi API tải dữ liệu
        setupApartmentSpinners();
        fetchApartmentData();

        // Logic Checkbox Chủ hộ
        checkboxIsHouseholder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                edtRelation.setText("Bản thân");
                edtRelation.setEnabled(false);
                edtRelation.setAlpha(0.7f);
                edtRelation.setError(null);
            } else {
                edtRelation.setText("");
                edtRelation.setEnabled(true);
                edtRelation.setAlpha(1.0f);
                edtRelation.requestFocus();
            }
        });

        // Sự kiện nút
        btnSaveResident.setOnClickListener(v -> createResident());
        btnCancel.setOnClickListener(v -> finish());
        btnScanQR.setOnClickListener(v -> startQRScanner());
    }

    private void initViews() {
        edtFullName = findViewById(R.id.edtFullName);
        edtBirthDate = findViewById(R.id.edtBirthDate);
        edtJob = findViewById(R.id.edtJob);
        edtRelation = findViewById(R.id.edtRelation);
        edtPhone = findViewById(R.id.edtPhone);
        edtEmail = findViewById(R.id.edtEmail);
        edtIdentityCard = findViewById(R.id.edtIdentityCard);
        edtHomeTown = findViewById(R.id.edtHomeTown);
        spinnerGender = findViewById(R.id.spinnerGender);

        // 🔥 Ánh xạ Spinner mới
        spnFloor = findViewById(R.id.spnFloor);
        spnRoom = findViewById(R.id.spnRoom);

        checkboxIsHouseholder = findViewById(R.id.checkboxIsHouseholder);
        btnSaveResident = findViewById(R.id.btnSaveResident);
        btnCancel = findViewById(R.id.btnCancel);
        btnScanQR = findViewById(R.id.btnScanQR);

        // Config Input Type
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

    // 🔥 1. CẤU HÌNH SPINNER PHÒNG & TẦNG
    private void setupApartmentSpinners() {
        floorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, floorList);
        floorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnFloor.setAdapter(floorAdapter);

        roomAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currentRoomList);
        roomAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnRoom.setAdapter(roomAdapter);

        // Khi chọn Tầng -> Lọc lại danh sách Phòng
        spnFloor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < floorList.size()) {
                    String selectedFloor = floorList.get(position);
                    updateRoomSpinner(selectedFloor);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Khi chọn Phòng -> Lưu lại
        spnRoom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < currentRoomList.size()) {
                    selectedRoomNumber = currentRoomList.get(position);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedRoomNumber = "";
            }
        });
    }

    // 🔥 2. CẬP NHẬT SPINNER PHÒNG DỰA TRÊN TẦNG
    private void updateRoomSpinner(String floor) {
        currentRoomList.clear();
        if (floorRoomsMap.containsKey(floor)) {
            List<String> rooms = floorRoomsMap.get(floor);
            if (rooms != null) {
                currentRoomList.addAll(rooms);
            }
        }

        if (currentRoomList.isEmpty()) {
            currentRoomList.add("Trống");
            selectedRoomNumber = "";
        } else {
            selectedRoomNumber = currentRoomList.get(0);
        }

        roomAdapter.notifyDataSetChanged();
        spnRoom.setSelection(0);
    }

    // 🔥 3. GỌI API LẤY DANH SÁCH TÒA NHÀ
    private void fetchApartmentData() {
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, APARTMENT_LIST_URL, null,
                response -> {
                    try {
                        floorRoomsMap.clear();
                        floorList.clear();

                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            String floor = obj.getString("floor");
                            String room = obj.getString("apartment_number");

                            if (!floorRoomsMap.containsKey(floor)) {
                                floorRoomsMap.put(floor, new ArrayList<>());
                                floorList.add(floor);
                            }
                            floorRoomsMap.get(floor).add(room);
                        }

                        if (floorList.isEmpty()) floorList.add("Không có dữ liệu");
                        floorAdapter.notifyDataSetChanged();

                        // Chọn mặc định
                        if (!floorList.isEmpty()) {
                            spnFloor.setSelection(0);
                            updateRoomSpinner(floorList.get(0));
                        }

                    } catch (Exception e) { e.printStackTrace(); }
                },
                error -> Toast.makeText(this, "Lỗi tải danh sách phòng", Toast.LENGTH_SHORT).show()
        );
        requestQueue.add(request);
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

        // 🔥 Lấy dữ liệu từ Spinner thay vì EditText
        String room = selectedRoomNumber;
        String floor = (spnFloor.getSelectedItem() != null) ? spnFloor.getSelectedItem().toString() : "0";

        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(birthDateDisplay) || TextUtils.isEmpty(phoneRaw)) {
            Toast.makeText(this, "Vui lòng nhập Tên, Ngày sinh và SĐT!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate Phòng
        if (TextUtils.isEmpty(room) || room.equals("Trống")) {
            Toast.makeText(this, "Vui lòng chọn phòng!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isValidPhone(phoneRaw)) {
            edtPhone.setError("SĐT không hợp lệ");
            return;
        }

        if (!identityCard.isEmpty() && identityCard.length() != 9 && identityCard.length() != 12) {
            edtIdentityCard.setError("CCCD phải là 9 hoặc 12 số");
            return;
        }

        String phoneForDb = phoneRaw.startsWith("0") ? "+84" + phoneRaw.substring(1) : phoneRaw;

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

            // 🔥 Gửi số phòng đã chọn từ Spinner
            body.put("room", room);
            // Gửi số tầng (dù backend tự check nhưng cứ gửi cho đủ bộ)
            body.put("floor", floor);

            body.put("is_head", isHead);
            body.put("relationship_name", relation);
            body.put("identity_card", identityCard);
            body.put("home_town", edtHomeTown.getText().toString().trim());

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, CREATE_URL, body,
                    response -> {
                        Toast.makeText(this, "✅ Tạo cư dân thành công!", Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    },
                    error -> {
                        String message = "Lỗi kết nối máy chủ";
                        if (error.networkResponse != null && error.networkResponse.data != null) {
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                JSONObject data = new JSONObject(responseBody);
                                message = data.optString("error", data.optString("message", ""));
                                if (message.isEmpty()) {
                                    if (error.networkResponse.statusCode == 409) message = "Dữ liệu bị trùng!";
                                    else if (error.networkResponse.statusCode == 404) message = "Phòng chưa tồn tại trong hệ thống.";
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
                    if (token != null) headers.put("Authorization", "Bearer " + token);
                    return headers;
                }
            };
            requestQueue.add(request);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi xử lý dữ liệu nội bộ", Toast.LENGTH_SHORT).show();
        }
    }

    private void startQRScanner() {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);
        scanner.startScan()
                .addOnSuccessListener(barcode -> {
                    String rawValue = barcode.getRawValue();
                    if (rawValue != null) parseCCCDData(rawValue);
                    else Toast.makeText(this, "Không đọc được dữ liệu!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Lỗi Camera: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void parseCCCDData(String rawData) {
        try {
            String[] parts = rawData.split("\\|");
            if (parts.length >= 6) {
                String cccd = parts[0];
                String name = parts[2];
                String dobRaw = parts[3];
                String gender = parts[4];
                String address = parts[5];

                if (edtIdentityCard != null) edtIdentityCard.setText(cccd);
                edtFullName.setText(name.toUpperCase());

                if (dobRaw.length() == 8) {
                    String day = dobRaw.substring(0, 2);
                    String month = dobRaw.substring(2, 4);
                    String year = dobRaw.substring(4, 8);
                    edtBirthDate.setText(day + "-" + month + "-" + year);
                }

                if (gender.equalsIgnoreCase("Nam")) spinnerGender.setSelection(0);
                else if (gender.equalsIgnoreCase("Nữ") || gender.equalsIgnoreCase("Nu")) spinnerGender.setSelection(1);
                else spinnerGender.setSelection(2);

                Toast.makeText(this, "✅ Đã điền thông tin từ CCCD!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Mã QR không đúng định dạng!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi xử lý dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}