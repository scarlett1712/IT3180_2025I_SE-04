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

import com.android.volley.AuthFailureError;
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
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BulkUtilityBillActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private MaterialButtonToggleGroup toggleGroupService;
    private RecyclerView recyclerBulkInput;
    private MaterialButton btnCheckRates, btnSaveAll;

    private BulkUtilityAdapter adapter;
    private List<UtilityInputItem> inputList = new ArrayList<>();

    private static final String API_GET_RESIDENTS = ApiConfig.BASE_URL + "/api/residents";
    private static final String API_SAVE_UTILITY = ApiConfig.BASE_URL + "/api/finance/create-utility-bulk";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bulk_utility_bill);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupListeners();

        loadRoomsFromAPI();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toggleGroupService = findViewById(R.id.toggleGroupService);
        recyclerBulkInput = findViewById(R.id.recyclerBulkInput);
        btnCheckRates = findViewById(R.id.btnCheckRates);
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
        adapter = new BulkUtilityAdapter(inputList);
        recyclerBulkInput.setAdapter(adapter);
    }

    private void setupListeners() {
        btnCheckRates.setOnClickListener(v -> {
            Intent intent = new Intent(this, ConfigRatesActivity.class);
            startActivity(intent);
        });

        btnSaveAll.setOnClickListener(v -> submitAll());

        toggleGroupService.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                Toast.makeText(this, "Đã chuyển loại dịch vụ", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRoomsFromAPI() {
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, API_GET_RESIDENTS, null,
                response -> {
                    inputList.clear();
                    Set<String> uniqueRooms = new HashSet<>();
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            String room = obj.optString("apartment_number", "").trim();
                            if (!TextUtils.isEmpty(room)) uniqueRooms.add(room);
                        }
                        List<String> sortedRooms = new ArrayList<>(uniqueRooms);
                        Collections.sort(sortedRooms);
                        for (String room : sortedRooms) {
                            inputList.add(new UtilityInputItem(room));
                        }
                        adapter.notifyDataSetChanged();

                    } catch (Exception e) { e.printStackTrace(); }
                },
                error -> Toast.makeText(this, "Lỗi tải danh sách phòng", Toast.LENGTH_SHORT).show()
        ) {
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

    private void submitAll() {
        JSONArray jsonArray = new JSONArray();
        int count = 0;

        for (UtilityInputItem item : inputList) {
            String newStr = item.getNewIndex().trim();

            // Chỉ xử lý nếu người dùng đã nhập chỉ số mới
            if (!newStr.isEmpty()) {
                try {
                    // 🔥 FIX: Xử lý an toàn cho chỉ số cũ
                    // Nếu oldIndex rỗng hoặc null -> Mặc định là 0
                    int oldVal = 0;
                    String oldStr = item.getOldIndex();
                    if (!TextUtils.isEmpty(oldStr) && TextUtils.isDigitsOnly(oldStr)) {
                        oldVal = Integer.parseInt(oldStr);
                    }

                    int newVal = Integer.parseInt(newStr);

                    // Kiểm tra logic: Chỉ số mới không được nhỏ hơn chỉ số cũ
                    if (newVal < oldVal) {
                        Toast.makeText(this, "Lỗi: Phòng " + item.getRoomNumber() + " chỉ số mới nhỏ hơn cũ (" + oldVal + ")!", Toast.LENGTH_LONG).show();
                        return;
                    }

                    JSONObject obj = new JSONObject();
                    obj.put("room", item.getRoomNumber());
                    obj.put("old_index", oldVal);
                    obj.put("new_index", newVal);
                    jsonArray.put(obj);
                    count++;

                } catch (NumberFormatException e) {
                    // Nếu người dùng nhập ký tự không phải số vào ô "Mới"
                    Toast.makeText(this, "Lỗi định dạng số tại phòng " + item.getRoomNumber(), Toast.LENGTH_SHORT).show();
                    return;
                } catch (JSONException e) { e.printStackTrace(); }
            }
        }

        if (count == 0) {
            Toast.makeText(this, "Bạn chưa nhập chỉ số mới nào!", Toast.LENGTH_SHORT).show();
            return;
        }

        String type = (toggleGroupService.getCheckedButtonId() == R.id.btnElectricity) ? "electricity" : "water";
        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        int year = Calendar.getInstance().get(Calendar.YEAR);

        sendBulkData(jsonArray, type, month, year);
    }

    private void sendBulkData(JSONArray data, String type, int month, int year) {
        String url = API_SAVE_UTILITY;
        JSONObject body = new JSONObject();
        try {
            body.put("type", type);
            body.put("month", month);
            body.put("year", year);
            body.put("data", data);
        } catch (JSONException e) { e.printStackTrace(); }

        btnSaveAll.setEnabled(false);
        btnSaveAll.setText("Đang lưu...");

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(this, "Đã lưu thành công!", Toast.LENGTH_LONG).show();
                    setResult(Activity.RESULT_OK);
                    finish();
                },
                error -> {
                    btnSaveAll.setEnabled(true);
                    btnSaveAll.setText("Lưu & Tính tiền");
                    Toast.makeText(this, "Lỗi khi lưu dữ liệu: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }
}