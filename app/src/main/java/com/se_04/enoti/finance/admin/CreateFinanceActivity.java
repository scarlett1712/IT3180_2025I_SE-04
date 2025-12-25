package com.se_04.enoti.finance.admin;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CreateFinanceActivity extends BaseActivity {

    private AutoCompleteTextView spinnerFloor, spinnerType;
    private TextInputEditText edtFinanceTitle, edtFinanceContent, edtAmount, edtDueDate;
    private MaterialButton btnCreateFee;
    private RecyclerView recyclerRooms;
    private RoomAdapter roomAdapter;
    private TextView txtSelectedRooms;
    private CheckBox chkSelectAllRooms;

    private final List<String> allRooms = new ArrayList<>();
    private final Map<String, List<String>> roomsByFloor = new HashMap<>();
    private Set<String> selectedRooms = new HashSet<>();

    private static final String API_GET_RESIDENTS_URL = ApiConfig.BASE_URL + "/api/residents";
    private static final String API_CREATE_FINANCE_URL = ApiConfig.BASE_URL + "/api/finance/create";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_finance);

        initViews();
        setupToolbar();
        setupDueDate();
        setupTypeDropdown();
        setupSelectAllCheckbox();

        recyclerRooms.setLayoutManager(new GridLayoutManager(this, 3));
        roomAdapter = new RoomAdapter(new ArrayList<>(), selected -> {
            selectedRooms = selected;
            updateSelectedRoomsDisplay();

            if (selectedRooms.size() < allRooms.size() && chkSelectAllRooms.isChecked()) {
                chkSelectAllRooms.setOnCheckedChangeListener(null);
                chkSelectAllRooms.setChecked(false);
                chkSelectAllRooms.setOnCheckedChangeListener(this::onSelectAllChanged);
            }
        });
        recyclerRooms.setAdapter(roomAdapter);

        btnCreateFee.setOnClickListener(v -> createFee());

        fetchRoomsFromAPI();
    }

    private void initViews() {
        edtFinanceTitle = findViewById(R.id.edtFinanceTitle);
        edtFinanceContent = findViewById(R.id.edtFinanceContent);
        edtAmount = findViewById(R.id.edtAmount);
        edtDueDate = findViewById(R.id.edtDueDate);
        spinnerFloor = findViewById(R.id.spinnerFloor);
        spinnerType = findViewById(R.id.spinnerType);
        btnCreateFee = findViewById(R.id.btnCreateFee);
        recyclerRooms = findViewById(R.id.recyclerRooms);
        txtSelectedRooms = findViewById(R.id.txtSelectedRooms);
        chkSelectAllRooms = findViewById(R.id.chkSelectAllRooms);

        // Khóa nhập tay ngày sinh
        edtDueDate.setFocusable(false);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Tạo khoản thu mới");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupDueDate() {
        edtDueDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            DatePickerDialog datePicker = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%d",
                        dayOfMonth, month + 1, year);
                edtDueDate.setText(selectedDate);
            }, calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));

            datePicker.getDatePicker().setMinDate(System.currentTimeMillis());
            datePicker.show();
        });
    }

    private void setupTypeDropdown() {
        String[] types = {"Phí dịch vụ", "Tiền điện", "Tiền nước", "Phí gửi xe", "Tự nguyện", "Khác"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, types);
        spinnerType.setAdapter(adapter);
        spinnerType.setText(types[0], false);

        spinnerType.setOnItemClickListener((parent, view, position, id) -> {
            String selectedType = types[position];
            if (selectedType.equals("Tiền điện") || selectedType.equals("Tiền nước") || selectedType.equals("Phí dịch vụ")) {
                Toast.makeText(this, "Hệ thống: " + selectedType + " là loại phí bắt buộc", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSelectAllCheckbox() {
        chkSelectAllRooms.setOnCheckedChangeListener(this::onSelectAllChanged);
    }

    private void onSelectAllChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            selectedRooms.clear();
            selectedRooms.addAll(allRooms);
            roomAdapter.selectAll(true);
            recyclerRooms.setAlpha(0.5f);
            recyclerRooms.setEnabled(false);
        } else {
            selectedRooms.clear();
            roomAdapter.selectAll(false);
            recyclerRooms.setAlpha(1.0f);
            recyclerRooms.setEnabled(true);
        }
        updateSelectedRoomsDisplay();
    }

    private void fetchRoomsFromAPI() {
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, API_GET_RESIDENTS_URL, null,
                response -> {
                    try {
                        allRooms.clear();
                        roomsByFloor.clear();
                        Set<String> uniqueRooms = new HashSet<>();

                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            String room = obj.optString("apartment_number", "").trim();
                            if (!TextUtils.isEmpty(room)) uniqueRooms.add(room);
                        }

                        allRooms.addAll(uniqueRooms);
                        Collections.sort(allRooms);

                        for (String room : allRooms) {
                            String floor = extractFloorFromRoom(room);
                            roomsByFloor.putIfAbsent(floor, new ArrayList<>());
                            if (!roomsByFloor.get(floor).contains(room)) {
                                roomsByFloor.get(floor).add(room);
                            }
                        }
                        setupFloorDropdown();
                        roomAdapter.updateRooms(allRooms);

                    } catch (Exception e) {
                        Log.e("CreateFinanceActivity", "Error parsing rooms", e);
                    }
                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                        Toast.makeText(this, "Phiên đăng nhập hết hạn", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Lỗi tải danh sách phòng", Toast.LENGTH_SHORT).show();
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        queue.add(request);
    }

    private void setupFloorDropdown() {
        List<String> floors = new ArrayList<>(roomsByFloor.keySet());
        Collections.sort(floors);
        floors.add(0, "Tất cả các tầng");

        ArrayAdapter<String> floorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, floors);
        spinnerFloor.setAdapter(floorAdapter);
        spinnerFloor.setText(floors.get(0), false);

        spinnerFloor.setOnItemClickListener((parent, view, position, id) -> {
            String selectedFloor = (String) parent.getItemAtPosition(position);
            if (selectedFloor.equals("Tất cả các tầng")) {
                roomAdapter.updateRooms(allRooms);
            } else {
                roomAdapter.updateRooms(roomsByFloor.getOrDefault(selectedFloor, new ArrayList<>()));
            }
            if (!chkSelectAllRooms.isChecked()) {
                selectedRooms.clear();
                updateSelectedRoomsDisplay();
            }
        });
    }

    private String extractFloorFromRoom(String room) {
        if (room.length() <= 2) return "Tầng 0";
        try {
            String floorPart = room.substring(0, room.length() - 2);
            floorPart = floorPart.replaceAll("\\D+", "");
            return "Tầng " + floorPart;
        } catch (Exception e) {
            return "Khác";
        }
    }

    private void updateSelectedRoomsDisplay() {
        if (chkSelectAllRooms.isChecked()) {
            txtSelectedRooms.setText("Đã chọn tất cả (" + allRooms.size() + " phòng)");
        } else if (selectedRooms.isEmpty()) {
            txtSelectedRooms.setText("Chưa chọn phòng nào (vui lòng chọn)");
        } else {
            txtSelectedRooms.setText("Đã chọn " + selectedRooms.size() + " phòng");
        }
    }

    private void createFee() {
        String title = edtFinanceTitle.getText().toString().trim();
        String content = edtFinanceContent.getText().toString().trim();
        String amountStr = edtAmount.getText().toString().trim();
        String dueDateRaw = edtDueDate.getText().toString().trim();
        String type = spinnerType.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "Vui lòng nhập Tiêu đề", Toast.LENGTH_SHORT).show();
            return;
        }

        Double amount = null;
        if (type.equalsIgnoreCase("Tự nguyện")) {
            if (!TextUtils.isEmpty(amountStr)) {
                try { amount = Double.parseDouble(amountStr); } catch (NumberFormatException e) { return; }
            }
        } else {
            if (TextUtils.isEmpty(amountStr)) {
                Toast.makeText(this, "Vui lòng nhập số tiền", Toast.LENGTH_SHORT).show();
                return;
            }
            try { amount = Double.parseDouble(amountStr); } catch (NumberFormatException e) { return; }
        }

        boolean isMandatory = !type.equalsIgnoreCase("Tự nguyện");

        try {
            JSONArray targetRooms = new JSONArray();
            if (chkSelectAllRooms.isChecked() || (selectedRooms.isEmpty() && spinnerFloor.getText().toString().equals("Tất cả các tầng"))) {
                for (String room : allRooms) targetRooms.put(room);
            } else {
                if (selectedRooms.isEmpty()) {
                    Toast.makeText(this, "Vui lòng chọn ít nhất 1 phòng", Toast.LENGTH_SHORT).show();
                    return;
                }
                for (String room : selectedRooms) targetRooms.put(room);
            }

            String adminId = UserManager.getInstance(this).getID();
            JSONObject body = new JSONObject();
            body.put("title", title);
            body.put("content", content.isEmpty() ? JSONObject.NULL : content);
            body.put("amount", amount == null ? JSONObject.NULL : amount);
            body.put("due_date", TextUtils.isEmpty(dueDateRaw) ? JSONObject.NULL : dueDateRaw);
            body.put("type", type);
            body.put("target_rooms", targetRooms);
            body.put("created_by", adminId);

            body.put("is_mandatory", isMandatory);

            btnCreateFee.setEnabled(false);
            btnCreateFee.setText("Đang xử lý...");

            RequestQueue queue = Volley.newRequestQueue(this);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API_CREATE_FINANCE_URL, body,
                    response -> {
                        Toast.makeText(this, "Tạo khoản thu thành công!", Toast.LENGTH_SHORT).show();
                        finish();
                    },
                    error -> {
                        btnCreateFee.setEnabled(true);
                        btnCreateFee.setText("Tạo khoản thu");
                        Toast.makeText(this, "Lỗi khi tạo khoản thu", Toast.LENGTH_SHORT).show();
                    }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                    if (token != null) headers.put("Authorization", "Bearer " + token);
                    return headers;
                }
            };

            queue.add(request);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi tạo dữ liệu", Toast.LENGTH_SHORT).show();
        }
    }
}