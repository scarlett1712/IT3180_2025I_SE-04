package com.se_04.enoti.maintenance.admin;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreateMaintenanceActivity extends BaseActivity {

    // 🔥 Thay đổi: Dùng AutoCompleteTextView thay cho Spinner
    private AutoCompleteTextView autoCompleteAssets, autoCompleteStaff;
    private TextInputEditText edtDate, edtDesc;
    private MaterialButton btnCreate; // Dùng MaterialButton

    // Lưu danh sách ID song song với danh sách Tên
    private final List<String> assetNames = new ArrayList<>();
    private final List<Integer> assetIds = new ArrayList<>();

    private final List<String> staffNames = new ArrayList<>();
    private final List<Integer> staffIds = new ArrayList<>();

    // Biến lưu ID đã chọn
    private int selectedAssetId = -1;
    private int selectedStaffId = -1;

    private final SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_maintenance);

        initViews();
        setupToolbar();

        // Sự kiện chọn ngày - Thêm cho cả TextInputLayout và EditText
        edtDate.setOnClickListener(v -> showDatePicker());
        edtDate.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                edtDate.clearFocus();
                showDatePicker();
            }
        });
        
        // Thêm click listener cho TextInputLayout
        com.google.android.material.textfield.TextInputLayout inputLayoutDate = findViewById(R.id.inputLayoutDate);
        if (inputLayoutDate != null) {
            inputLayoutDate.setEndIconOnClickListener(v -> showDatePicker());
            inputLayoutDate.setOnClickListener(v -> showDatePicker());
        }

        // Sự kiện nút tạo
        btnCreate.setOnClickListener(v -> createSchedule());

        // Tải dữ liệu
        loadAssets();
        loadStaff();
    }

    private void initViews() {
        // Ánh xạ đúng ID trong layout mới
        autoCompleteAssets = findViewById(R.id.autoCompleteAssets);
        autoCompleteStaff = findViewById(R.id.autoCompleteStaff);
        edtDate = findViewById(R.id.edtScheduledDate);
        edtDesc = findViewById(R.id.edtDescription);
        btnCreate = findViewById(R.id.btnCreate);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Tạo lịch bảo trì");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            c.set(year, month, dayOfMonth);
            // Hiển thị định dạng dễ đọc (dd/MM/yyyy)
            edtDate.setText(displayDateFormat.format(c.getTime()));
            // Lưu định dạng API (yyyy-MM-dd) vào tag để dùng sau này
            edtDate.setTag(apiDateFormat.format(c.getTime()));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // 1. Tải danh sách Tài sản (Assets)
    private void loadAssets() {
        String url = ApiConfig.BASE_URL + "/api/maintenance/assets";
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    assetNames.clear();
                    assetIds.clear();
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            assetIds.add(obj.getInt("asset_id"));
                            // Hiển thị tên + vị trí
                            assetNames.add(obj.getString("asset_name") + " (" + obj.optString("location") + ")");
                        }

                        // 🔥 Setup Adapter cho AutoCompleteTextView
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, assetNames);
                        autoCompleteAssets.setAdapter(adapter);

                        // 🔥 Xử lý sự kiện chọn item
                        autoCompleteAssets.setOnItemClickListener((parent, view, position, id) -> {
                            selectedAssetId = assetIds.get(position);
                        });

                    } catch (JSONException e) { e.printStackTrace(); }
                },
                error -> Toast.makeText(this, "Lỗi tải thiết bị", Toast.LENGTH_SHORT).show()
        );
        Volley.newRequestQueue(this).add(request);
    }

    // 2. Tải danh sách Nhân viên (Staff)
    private void loadStaff() {
        String url = ApiConfig.BASE_URL + "/api/maintenance/staff-list";
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    staffNames.clear();
                    staffIds.clear();
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            staffIds.add(obj.getInt("user_id"));
                            // Hiển thị tên + SĐT
                            staffNames.add(obj.getString("full_name") + " - " + obj.getString("phone"));
                        }

                        // 🔥 Setup Adapter cho AutoCompleteTextView
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, staffNames);
                        autoCompleteStaff.setAdapter(adapter);

                        // 🔥 Xử lý sự kiện chọn item
                        autoCompleteStaff.setOnItemClickListener((parent, view, position, id) -> {
                            selectedStaffId = staffIds.get(position);
                        });

                    } catch (JSONException e) { e.printStackTrace(); }
                },
                error -> Toast.makeText(this, "Lỗi tải nhân viên", Toast.LENGTH_SHORT).show()
        );
        Volley.newRequestQueue(this).add(request);
    }

    // 3. Gửi yêu cầu tạo lịch
    private void createSchedule() {
        String displayDate = edtDate.getText().toString();
        // Lấy ngày chuẩn API từ Tag (đã set ở showDatePicker) hoặc parse lại
        String apiDate = (edtDate.getTag() != null) ? edtDate.getTag().toString() : "";
        String desc = edtDesc.getText().toString();

        // Validate
        if (selectedAssetId == -1) {
            Toast.makeText(this, "Vui lòng chọn thiết bị", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedStaffId == -1) {
            Toast.makeText(this, "Vui lòng chọn nhân viên", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(displayDate)) {
            Toast.makeText(this, "Vui lòng chọn ngày bảo trì", Toast.LENGTH_SHORT).show();
            return;
        }

        // Nếu người dùng nhập tay ngày mà không qua Picker -> apiDate sẽ rỗng -> Parse thủ công
        if (apiDate.isEmpty()) {
            try {
                // Giả sử user nhập đúng dd/MM/yyyy
                apiDate = apiDateFormat.format(displayDateFormat.parse(displayDate));
            } catch (Exception e) {
                apiDate = displayDate; // Fallback
            }
        }

        btnCreate.setEnabled(false);
        btnCreate.setText("Đang xử lý...");

        JSONObject body = new JSONObject();
        try {
            body.put("asset_id", selectedAssetId);
            body.put("user_id", selectedStaffId);
            body.put("scheduled_date", apiDate);
            body.put("description", desc);
        } catch (JSONException e) { e.printStackTrace(); }

        String url = ApiConfig.BASE_URL + "/api/maintenance/schedule/create";

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(this, "✅ Tạo lịch bảo trì thành công!", Toast.LENGTH_LONG).show();
                    finish(); // Đóng màn hình này, quay về danh sách
                },
                error -> {
                    btnCreate.setEnabled(true);
                    btnCreate.setText("Tạo lịch & Giao việc");
                    Toast.makeText(this, "❌ Lỗi khi tạo lịch", Toast.LENGTH_SHORT).show();
                }
        );

        Volley.newRequestQueue(this).add(request);
    }
}