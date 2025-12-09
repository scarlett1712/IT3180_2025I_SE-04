package com.se_04.enoti.maintenance.admin;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
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

    private Spinner spinnerAssets, spinnerStaff;
    private TextInputEditText edtDate, edtDesc;
    private Button btnCreate;

    // Lưu danh sách ID song song với danh sách Tên hiển thị trên Spinner
    private List<String> assetNames = new ArrayList<>();
    private List<Integer> assetIds = new ArrayList<>();

    private List<String> staffNames = new ArrayList<>();
    private List<Integer> staffIds = new ArrayList<>();

    private final SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_maintenance);

        // Ánh xạ View
        spinnerAssets = findViewById(R.id.spinnerAssets);
        spinnerStaff = findViewById(R.id.spinnerStaff);
        edtDate = findViewById(R.id.edtScheduledDate);
        edtDesc = findViewById(R.id.edtDescription);
        btnCreate = findViewById(R.id.btnCreate);

        // Sự kiện chọn ngày
        edtDate.setOnClickListener(v -> showDatePicker());

        // Sự kiện nút tạo
        btnCreate.setOnClickListener(v -> createSchedule());

        // Tải dữ liệu cho Spinner
        loadAssets();
        loadStaff();
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            c.set(year, month, dayOfMonth);
            edtDate.setText(apiDateFormat.format(c.getTime()));
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
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, assetNames);
                        spinnerAssets.setAdapter(adapter);
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
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, staffNames);
                        spinnerStaff.setAdapter(adapter);
                    } catch (JSONException e) { e.printStackTrace(); }
                },
                error -> Toast.makeText(this, "Lỗi tải nhân viên", Toast.LENGTH_SHORT).show()
        );
        Volley.newRequestQueue(this).add(request);
    }

    // 3. Gửi yêu cầu tạo lịch
    private void createSchedule() {
        String date = edtDate.getText().toString();
        String desc = edtDesc.getText().toString();

        if (TextUtils.isEmpty(date)) {
            Toast.makeText(this, "Vui lòng chọn ngày", Toast.LENGTH_SHORT).show();
            return;
        }
        if (assetIds.isEmpty() || staffIds.isEmpty()) {
            Toast.makeText(this, "Dữ liệu chưa tải xong, vui lòng đợi", Toast.LENGTH_SHORT).show();
            return;
        }

        // Lấy ID dựa trên vị trí đang chọn trong Spinner
        int selectedAssetId = assetIds.get(spinnerAssets.getSelectedItemPosition());
        int selectedStaffId = staffIds.get(spinnerStaff.getSelectedItemPosition());

        btnCreate.setEnabled(false);
        btnCreate.setText("Đang xử lý...");

        JSONObject body = new JSONObject();
        try {
            body.put("asset_id", selectedAssetId);
            body.put("user_id", selectedStaffId);
            body.put("scheduled_date", date);
            body.put("description", desc);
        } catch (JSONException e) { e.printStackTrace(); }

        String url = ApiConfig.BASE_URL + "/api/maintenance/schedule/create";

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(this, "✅ Tạo lịch bảo trì thành công!", Toast.LENGTH_LONG).show();
                    finish(); // Đóng màn hình này, quay về danh sách
                },
                error -> Toast.makeText(this, "❌ Lỗi khi tạo lịch", Toast.LENGTH_SHORT).show()
        );

        Volley.newRequestQueue(this).add(request);
    }
}