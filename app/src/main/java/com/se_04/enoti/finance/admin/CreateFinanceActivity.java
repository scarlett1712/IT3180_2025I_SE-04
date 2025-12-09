package com.se_04.enoti.finance.admin;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class CreateFinanceActivity extends BaseActivity {

    private TextInputEditText edtFinanceTitle, edtFinanceContent, edtAmount, edtDueDate;
    private Spinner spinnerFloor, spinnerType;
    private Button btnCreateFee;
    private RecyclerView recyclerRooms;
    private RoomAdapter roomAdapter;
    private TextView txtSelectedRooms;

    private final List<String> allRooms = new ArrayList<>();
    private final Map<String, List<String>> roomsByFloor = new HashMap<>();
    private Set<String> selectedRooms = new HashSet<>();

    private static final String API_GET_RESIDENTS_URL = ApiConfig.BASE_URL + "/api/residents";
    private static final String API_CREATE_FINANCE_URL = ApiConfig.BASE_URL + "/api/finance/create";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_finance);

        // --- Toolbar setup ---
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Tạo khoản thu mới");
        }

        // --- View Binding ---
        edtFinanceTitle = findViewById(R.id.edtFinanceTitle);
        edtFinanceContent = findViewById(R.id.edtFinanceContent);
        edtAmount = findViewById(R.id.edtAmount);
        edtDueDate = findViewById(R.id.edtDueDate);
        spinnerFloor = findViewById(R.id.spinnerFloor);
        spinnerType = findViewById(R.id.spinnerType);
        btnCreateFee = findViewById(R.id.btnCreateFee);
        recyclerRooms = findViewById(R.id.recyclerRooms);
        txtSelectedRooms = findViewById(R.id.txtSelectedRooms);

        recyclerRooms.setLayoutManager(new LinearLayoutManager(this));
        roomAdapter = new RoomAdapter(new ArrayList<>(), selected -> {
            selectedRooms = selected;
            updateSelectedRoomsDisplay();
        });
        recyclerRooms.setAdapter(roomAdapter);

        setupDueDate();
        setupTypeSpinner();
        btnCreateFee.setOnClickListener(v -> createFee());
        fetchRoomsFromAPI();
    }

    // -----------------------------
    // Date picker setup
    // -----------------------------
    private void setupDueDate() {
        edtDueDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%d",
                        dayOfMonth, month + 1, year);
                edtDueDate.setText(selectedDate);
            }, calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH))
                    .show();
        });
    }

    // -----------------------------
    // Spinner loại khoản thu
    // -----------------------------
    private void setupTypeSpinner() {
        List<String> types = Arrays.asList("Bắt buộc", "Tự nguyện");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, types);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(adapter);
    }

    // -----------------------------
    // Fetch rooms from API
    // -----------------------------
    private void fetchRoomsFromAPI() {
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, API_GET_RESIDENTS_URL, null,
                response -> {
                    try {
                        allRooms.clear();
                        roomsByFloor.clear();

                        // Sử dụng Set để loại trùng phòng
                        Set<String> uniqueRooms = new HashSet<>();

                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            String room = obj.optString("apartment_number", "").trim();
                            if (!TextUtils.isEmpty(room)) {
                                uniqueRooms.add(room);
                            }
                        }

                        allRooms.addAll(uniqueRooms);

                        // Gom phòng theo tầng
                        for (String room : allRooms) {
                            String floor = extractFloorFromRoom(room);
                            roomsByFloor.putIfAbsent(floor, new ArrayList<>());
                            if (!roomsByFloor.get(floor).contains(room)) {
                                roomsByFloor.get(floor).add(room);
                            }
                        }

                        setupFloorSpinner();

                    } catch (Exception e) {
                        Log.e("CreateFinanceActivity", "Error parsing rooms", e);
                        Toast.makeText(this, "Lỗi xử lý danh sách phòng", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    Log.e("CreateFinanceActivity", "Error fetching rooms", error);
                    Toast.makeText(this, "Lỗi tải danh sách phòng", Toast.LENGTH_SHORT).show();
                });
        queue.add(request);
    }

    // -----------------------------
    // Spinner tầng
    // -----------------------------
    private void setupFloorSpinner() {
        List<String> floors = new ArrayList<>(roomsByFloor.keySet());
        Collections.sort(floors);
        floors.add(0, "Tất cả tầng");

        ArrayAdapter<String> floorAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, floors);
        floorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFloor.setAdapter(floorAdapter);

        spinnerFloor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                String selectedFloor = floors.get(position);
                if (selectedFloor.equals("Tất cả tầng")) {
                    roomAdapter.updateRooms(allRooms);
                } else {
                    roomAdapter.updateRooms(roomsByFloor.getOrDefault(selectedFloor, new ArrayList<>()));
                }
                selectedRooms.clear();
                updateSelectedRoomsDisplay();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    // -----------------------------
    // Helper - tách tầng từ số phòng
    // -----------------------------
    private String extractFloorFromRoom(String room) {
        if (room.length() <= 2) return "Tầng 0";
        try {
            String floorPart = room.substring(0, room.length() - 2);
            return "Tầng " + Integer.parseInt(floorPart);
        } catch (Exception e) {
            return "Tầng 0";
        }
    }

    // -----------------------------
    // Hiển thị số phòng đã chọn
    // -----------------------------
    private void updateSelectedRoomsDisplay() {
        if (selectedRooms.isEmpty()) {
            txtSelectedRooms.setText("Chưa chọn phòng nào (mặc định: tất cả)");
        } else {
            txtSelectedRooms.setText("Đã chọn " + selectedRooms.size() + " phòng");
        }
    }

    // -----------------------------
    // Gửi yêu cầu tạo khoản thu
    // -----------------------------
    private void createFee() {
        String title = edtFinanceTitle.getText().toString().trim();
        String content = edtFinanceContent.getText().toString().trim();
        String amountStr = edtAmount.getText().toString().trim();
        String dueDateRaw = edtDueDate.getText().toString().trim();
        String type = spinnerType.getSelectedItem().toString().trim();

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "Vui lòng nhập Tiêu đề", Toast.LENGTH_SHORT).show();
            return;
        }

        // --- Bắt đầu thay đổi logic xử lý Amount ---

        Double amount = null; // ✅ Sử dụng kiểu đối tượng Double để cho phép null

        if (type.equals("Tự nguyện")) {
            // Nếu là "Tự nguyện", amount có thể rỗng
            if (!TextUtils.isEmpty(amountStr)) {
                try {
                    amount = Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            // Nếu amountStr rỗng, amount sẽ giữ nguyên giá trị null.
        } else {
            // Nếu là "Bắt buộc" hoặc loại khác, amount không được rỗng
            if (TextUtils.isEmpty(amountStr)) {
                Toast.makeText(this, "Vui lòng nhập số tiền cho khoản thu bắt buộc", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        // --- Kết thúc thay đổi ---

        try {
            JSONArray targetRooms = new JSONArray();
            if (selectedRooms.isEmpty()) {
                for (String room : allRooms) targetRooms.put(room);
            } else {
                for (String room : selectedRooms) targetRooms.put(room);
            }

            String adminId = UserManager.getInstance(this).getID();

            JSONObject body = new JSONObject();
            body.put("title", title);
            body.put("content", content.isEmpty() ? JSONObject.NULL : content);
            // ✅ Gửi null đúng cách
            body.put("amount", amount == null ? JSONObject.NULL : amount);
            body.put("due_date", TextUtils.isEmpty(dueDateRaw) ? JSONObject.NULL : dueDateRaw);
            body.put("type", type);
            body.put("target_rooms", targetRooms);
            body.put("created_by", adminId);

            Log.d("CreateFinanceActivity", "📤 Request body: " + body.toString());

            RequestQueue queue = Volley.newRequestQueue(this);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API_CREATE_FINANCE_URL, body,
                    response -> {
                        Toast.makeText(this, "Tạo khoản thu thành công!", Toast.LENGTH_SHORT).show();
                        finish();
                    },
                    error -> {
                        Log.e("CreateFinanceActivity", "Error creating fee", error);
                        Toast.makeText(this, "Lỗi khi tạo khoản thu", Toast.LENGTH_SHORT).show();
                    });

            queue.add(request);

        } catch (Exception e) {
            Log.e("CreateFinanceActivity", "Error building JSON", e);
            Toast.makeText(this, "Có lỗi xảy ra khi chuẩn bị dữ liệu", Toast.LENGTH_SHORT).show();
        }
    }


    // -----------------------------
    // Nút quay lại trên toolbar
    // -----------------------------
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}