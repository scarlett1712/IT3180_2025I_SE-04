package com.se_04.enoti.finance.admin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
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
                String type = getCurrentServiceType();
                boolean isFixed = type.equals("management_fee") || type.equals("service_fee");

                adapter.setInputMode(!isFixed);

                btnSaveAll.setText(isFixed ? "Chốt phí" : "Lưu & Tính tiền");

                String serviceName;
                switch (type) {
                    case "electricity":
                        serviceName = "Điện";
                        break;
                    case "water":
                        serviceName = "Nước";
                        break;
                    case "management_fee":
                        serviceName = "Phí quản lý";
                        break;
                    case "service_fee":
                        serviceName = "Phí dịch vụ";
                        break;
                    default:
                        serviceName = "Dịch vụ";
                        break;
                }
                getSupportActionBar().setTitle("Chốt số " + serviceName);
            }
        });
    }

    private void loadRoomsFromAPI() {
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, API_GET_RESIDENTS, null,
                response -> {
                    inputList.clear();
                    Set<String> uniqueRooms = new HashSet<>();
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            String room = obj.optString("apartment_number", "").trim();
                            if (!TextUtils.isEmpty(room)) {
                                uniqueRooms.add(room);
                            }
                        }

                        List<String> sortedRooms = new ArrayList<>(uniqueRooms);
                        Collections.sort(sortedRooms, (room1, room2) -> {
                            int num1 = extractRoomNumber(room1);
                            int num2 = extractRoomNumber(room2);
                            return Integer.compare(num1, num2);
                        });

                        for (String room : sortedRooms) {
                            inputList.add(new UtilityInputItem(room));
                        }
                        adapter.notifyDataSetChanged();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(this, "Lỗi tải danh sách phòng", Toast.LENGTH_SHORT).show()
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }

    private void submitAll() {
        String type = getCurrentServiceType();
        boolean isFixedFee = type.equals("management_fee") || type.equals("service_fee");

        if (isFixedFee) {
            confirmAndSaveFixedFee(type);
            return;
        }

        JSONArray jsonArray = new JSONArray();
        int count = 0;

        for (UtilityInputItem item : inputList) {
            String newStr = item.getNewIndex().trim();
            if (!newStr.isEmpty()) {
                try {
                    int oldVal = 0;
                    String oldStr = item.getOldIndex();
                    if (!TextUtils.isEmpty(oldStr) && TextUtils.isDigitsOnly(oldStr)) {
                        oldVal = Integer.parseInt(oldStr);
                    }

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
                    Toast.makeText(this, "Lỗi định dạng số tại phòng " + item.getRoomNumber(), Toast.LENGTH_SHORT).show();
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

        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        int year = Calendar.getInstance().get(Calendar.YEAR);

        sendBulkData(jsonArray, type, month, year);
    }

    private void confirmAndSaveFixedFee(String type) {
        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        int year = Calendar.getInstance().get(Calendar.YEAR);

        JSONObject body = new JSONObject();
        try {
            body.put("type", type);
            body.put("month", month);
            body.put("year", year);
            body.put("auto_calculate", true);
            } catch (JSONException e) {
            e.printStackTrace();
        }

        btnSaveAll.setEnabled(false);
        btnSaveAll.setText("Đang chốt phí...");

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API_SAVE_UTILITY, body,
                response -> {
                    Toast.makeText(this, "Chốt " + getTypeName(type) + " thành công!", Toast.LENGTH_LONG).show();
                    setResult(Activity.RESULT_OK);
                    finish();
                },
                error -> {
                    btnSaveAll.setEnabled(true);
                    btnSaveAll.setText("Chốt phí");
                    Toast.makeText(this, "Lỗi khi chốt phí: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }

    private void sendBulkData(JSONArray data, String type, int month, int year) {
        JSONObject body = new JSONObject();
        try {
            body.put("type", type);
            body.put("month", month);
            body.put("year", year);
            body.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        btnSaveAll.setEnabled(false);
        btnSaveAll.setText("Đang lưu...");

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API_SAVE_UTILITY, body,
                response -> {
                    Toast.makeText(this, "Lưu và tính tiền thành công!", Toast.LENGTH_LONG).show();
                    setResult(Activity.RESULT_OK);
                    finish();
                },
                error -> {
                    btnSaveAll.setEnabled(true);
                    btnSaveAll.setText("Lưu & Tính tiền");
                    Toast.makeText(this, "Lỗi khi lưu: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }

    // SỬA: Dùng if-else thay switch để tránh lỗi "constant expression required"
    private String getCurrentServiceType() {
        int checkedId = toggleGroupService.getCheckedButtonId();
        if (checkedId == R.id.btnElectricity) {
            return "electricity";
        } else if (checkedId == R.id.btnWater) {
            return "water";
        } else if (checkedId == R.id.btnManagement) {
            return "management_fee";
        } else if (checkedId == R.id.btnService) {
            return "service_fee";
        } else {
            return "electricity";
        }
    }

    private String getTypeName(String type) {
        switch (type) {
            case "electricity":
                return "điện";
            case "water":
                return "nước";
            case "management_fee":
                return "phí quản lý";
            case "service_fee":
                return "phí dịch vụ";
            default:
                return "dịch vụ";
        }
    }

    private int extractRoomNumber(String room) {
        try {
            String numbers = room.replaceAll("\\D+", "");
            return numbers.isEmpty() ? 0 : Integer.parseInt(numbers);
        } catch (Exception e) {
            return 0;
        }
    }
}