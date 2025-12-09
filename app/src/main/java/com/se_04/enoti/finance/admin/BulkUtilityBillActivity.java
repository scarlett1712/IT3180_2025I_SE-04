package com.se_04.enoti.finance.admin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BulkUtilityBillActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private MaterialButtonToggleGroup toggleGroupService;
    private RecyclerView recyclerBulkInput;
    private MaterialButton btnCheckRates, btnSaveAll;

    private BulkUtilityAdapter adapter;
    private List<UtilityInputItem> inputList = new ArrayList<>();

    // API URLs
    private static final String API_GET_RESIDENTS = ApiConfig.BASE_URL + "/api/residents";
    private static final String API_SAVE_UTILITY = ApiConfig.BASE_URL + "/api/finance/create-utility-bulk";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bulk_utility_bill); // Sử dụng layout mới

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupListeners();

        // Tải danh sách phòng từ API thay vì dữ liệu giả
        loadRoomsFromAPI();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toggleGroupService = findViewById(R.id.toggleGroupService);
        recyclerBulkInput = findViewById(R.id.recyclerBulkInput);
        btnCheckRates = findViewById(R.id.btnCheckRates); // Nút Tra cứu giá (trước là CheckAndConfig)
        btnSaveAll = findViewById(R.id.btnSaveAll);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chốt số hàng loạt");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        recyclerBulkInput.setLayoutManager(new LinearLayoutManager(this));
        // Khởi tạo Adapter
        adapter = new BulkUtilityAdapter(inputList);
        recyclerBulkInput.setAdapter(adapter);
    }

    private void setupListeners() {
        // 1. Xử lý nút Tra cứu giá (Chuyển sang màn hình ConfigRates)
        btnCheckRates.setOnClickListener(v -> {
            Intent intent = new Intent(this, ConfigRatesActivity.class);
            startActivity(intent);
        });

        // 2. Xử lý nút Lưu
        btnSaveAll.setOnClickListener(v -> submitAll());

        // 3. Xử lý Toggle Điện/Nước (Nếu cần clear dữ liệu cũ khi đổi tab)
        toggleGroupService.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                // Có thể load lại chỉ số cũ tương ứng với loại dịch vụ ở đây
                // Ví dụ: loadOldIndexes(checkedId == R.id.btnElectricity ? "electricity" : "water");
                Toast.makeText(this, "Đã chuyển loại dịch vụ", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- API CALLS ---

    // 1. Tải danh sách phòng để nhập liệu
    private void loadRoomsFromAPI() {
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, API_GET_RESIDENTS, null,
                response -> {
                    inputList.clear();
                    Set<String> uniqueRooms = new HashSet<>();

                    try {
                        // Lọc lấy danh sách phòng duy nhất
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            String room = obj.optString("apartment_number", "").trim();
                            if (!TextUtils.isEmpty(room)) {
                                uniqueRooms.add(room);
                            }
                        }

                        // Sắp xếp phòng tăng dần
                        List<String> sortedRooms = new ArrayList<>(uniqueRooms);
                        Collections.sort(sortedRooms);

                        // Tạo danh sách item nhập liệu
                        for (String room : sortedRooms) {
                            // Mặc định chỉ số cũ là 0 (hoặc lấy từ API khác nếu có)
                            // Chỉ số mới để trống
                            inputList.add(new UtilityInputItem(room));
                        }
                        adapter.notifyDataSetChanged();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(this, "Lỗi tải danh sách phòng", Toast.LENGTH_SHORT).show()
        );
        queue.add(request);
    }

    // 2. Gửi dữ liệu lên Server
    private void submitAll() {
        JSONArray jsonArray = new JSONArray();
        int count = 0;

        // Duyệt qua list trong Adapter để lấy dữ liệu User đã nhập
        for (UtilityInputItem item : inputList) {
            String newStr = item.getNewIndex().trim();
            // Nếu có nhập chỉ số mới thì mới xử lý
            if (!newStr.isEmpty()) {
                try {
                    int oldVal = Integer.parseInt(item.getOldIndex());
                    int newVal = Integer.parseInt(newStr);

                    if (newVal < oldVal) {
                        Toast.makeText(this, "Lỗi: Phòng " + item.getRoomNumber() + " chỉ số mới nhỏ hơn cũ!", Toast.LENGTH_LONG).show();
                        return;
                    }

                    JSONObject obj = new JSONObject();
                    obj.put("room", item.getRoomNumber());
                    obj.put("old_index", oldVal);
                    obj.put("new_index", newVal);
                    jsonArray.put(obj);
                    count++;
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Lỗi định dạng số ở phòng " + item.getRoomNumber(), Toast.LENGTH_SHORT).show();
                    return;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        if (count == 0) {
            Toast.makeText(this, "Bạn chưa nhập chỉ số mới nào!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Xác định loại dịch vụ đang chọn
        String type = (toggleGroupService.getCheckedButtonId() == R.id.btnElectricity) ? "electricity" : "water";
        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        int year = Calendar.getInstance().get(Calendar.YEAR);

        // Gửi API
        sendBulkData(jsonArray, type, month, year);
    }

    private void sendBulkData(JSONArray data, String type, int month, int year) {
        String url = API_SAVE_UTILITY;
        JSONObject body = new JSONObject();
        try {
            body.put("type", type);
            body.put("month", month);
            body.put("year", year);
            body.put("data", data); // Mảng dữ liệu chi tiết
        } catch (JSONException e) { e.printStackTrace(); }

        Log.d("BulkUtility", "Sending: " + body.toString());

        btnSaveAll.setEnabled(false);
        btnSaveAll.setText("Đang lưu...");

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(this, "Đã lưu và tính tiền thành công!", Toast.LENGTH_LONG).show();
                    setResult(Activity.RESULT_OK);
                    finish();
                },
                error -> {
                    btnSaveAll.setEnabled(true);
                    btnSaveAll.setText("Lưu & Tính tiền");
                    Log.e("BulkUtility", "Error: " + error.toString());
                    Toast.makeText(this, "Lỗi khi lưu dữ liệu", Toast.LENGTH_SHORT).show();
                }
        );

        Volley.newRequestQueue(this).add(request);
    }
}