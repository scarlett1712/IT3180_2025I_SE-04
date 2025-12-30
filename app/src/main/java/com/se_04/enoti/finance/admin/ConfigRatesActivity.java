package com.se_04.enoti.finance.admin;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import java.util.List;

public class ConfigRatesActivity extends BaseActivity {

    private MaterialButtonToggleGroup toggleGroupType;
    private RecyclerView recyclerRates;
    private MaterialButton btnSaveRates, btnAddTier;

    private ConfigRateAdapter adapter;
    private List<RateItem> rateList = new ArrayList<>();

    // Mặc định chọn Điện
    private String currentType = "electricity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_rates); // Sử dụng layout mới

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupListeners();

        // Load dữ liệu lần đầu
        loadRates();
    }

    private void initViews() {
        toggleGroupType = findViewById(R.id.toggleGroupType);
        recyclerRates = findViewById(R.id.recyclerRates);
        btnSaveRates = findViewById(R.id.btnSaveRates);
        btnAddTier = findViewById(R.id.btnAddTier);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Cấu hình Đơn giá");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new ConfigRateAdapter(rateList, currentType);
        recyclerRates.setLayoutManager(new LinearLayoutManager(this));
        recyclerRates.setAdapter(adapter);
    }

    private void setupListeners() {
        // 1. Xử lý chuyển đổi loại dịch vụ (Điện <-> Nước)
        toggleGroupType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnElectricity) {
                    currentType = "electricity";
                } else if (checkedId == R.id.btnWater) {
                    currentType = "water";
                } else if (checkedId == R.id.btnManagement) {
                    currentType = "management_fee";
                } else if (checkedId == R.id.btnService) {
                    currentType = "service_fee";
                }
                // Cập nhật adapter để hiển thị đúng định dạng
                adapter.updateType(currentType);
                // Ẩn/hiện nút thêm bậc
                updateAddTierVisibility();

                loadRates(); // Tải lại dữ liệu khi đổi tab
            }
        });

        // 2. Nút Lưu
        btnSaveRates.setOnClickListener(v -> saveRates());

        // 3. Nút Thêm bậc mới (UI Logic)
        btnAddTier.setOnClickListener(v -> {
            boolean isUtility = currentType.equals("electricity") || currentType.equals("water");
            if (!isUtility) {
                Toast.makeText(this, "Loại phí này chỉ có một bậc duy nhất", Toast.LENGTH_SHORT).show();
                return;
            }
            // Thêm một dòng trắng vào list
            rateList.add(new RateItem("Bậc " + (rateList.size() + 1), 0, 0, 0.0));
            adapter.notifyItemInserted(rateList.size() - 1);
            recyclerRates.scrollToPosition(rateList.size() - 1);
        });
    }// Ẩn nút "Thêm bậc" khi là phí quản lý hoặc dịch vụ
    private void updateAddTierVisibility() {
        boolean isUtility = currentType.equals("electricity") || currentType.equals("water");
        btnAddTier.setVisibility(isUtility ? View.VISIBLE : View.GONE);
    }

    // --- API CALLS ---

    private void loadRates() {
        String url = ApiConfig.BASE_URL + "/api/finance/utility-rates?type=" + currentType;

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    rateList.clear();

                    boolean isFixedFee = currentType.equals("management_fee") || currentType.equals("service_fee");

                    try {
                        if (response.length() == 0 && isFixedFee) {
                            // Nếu là phí cố định và server chưa có dữ liệu → tự tạo 1 dòng mặc định
                            String defaultName = currentType.equals("management_fee") ? "Phí quản lý" : "Phí dịch vụ";
                            rateList.add(new RateItem(defaultName, 0, 0, 0.0));
                        } else {
                            // Có dữ liệu từ server → load bình thường
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject obj = response.getJSONObject(i);
                                Integer max = obj.isNull("max_usage") ? 0 : obj.getInt("max_usage");

                                rateList.add(new RateItem(
                                        obj.getString("tier_name"),
                                        obj.getInt("min_usage"),
                                        max,
                                        obj.getDouble("price")
                                ));
                            }
                        }

                        adapter.notifyDataSetChanged();
                        updateAddTierVisibility();

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Lỗi xử lý dữ liệu", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    // Nếu lỗi mạng hoặc server → với phí cố định thì vẫn tạo dòng mặc định
                    rateList.clear();
                    if (currentType.equals("management_fee") || currentType.equals("service_fee")) {
                        String defaultName = currentType.equals("management_fee") ? "Phí quản lý" : "Phí dịch vụ";
                        rateList.add(new RateItem(defaultName, 0, 0, 0.0));
                        adapter.notifyDataSetChanged();
                        updateAddTierVisibility();
                    }
                    Toast.makeText(this, "Không thể tải bảng giá", Toast.LENGTH_SHORT).show();
                }
        );

        Volley.newRequestQueue(this).add(request);
    }

    private void saveRates() {
        // Lấy danh sách mới nhất từ Adapter (sau khi user chỉnh sửa)
        // Lưu ý: ConfigRateAdapter cần có method getList() trả về list hiện tại
        List<RateItem> itemsToSave = adapter.getList();

        if (itemsToSave.isEmpty()) {
            Toast.makeText(this, "Danh sách trống, không thể lưu", Toast.LENGTH_SHORT).show();
            return;
        }
        // Kiểm tra: Phí quản lý và dịch vụ chỉ được có đúng 1 bậc
        if (currentType.equals("management_fee") || currentType.equals("service_fee")) {
            if (itemsToSave.size() != 1) {
                Toast.makeText(this, "Loại phí này phải có đúng một bậc", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        btnSaveRates.setEnabled(false);
        btnSaveRates.setText("Đang lưu...");

        String url = ApiConfig.BASE_URL + "/api/finance/update-rates";
        JSONObject body = new JSONObject();

        try {
            body.put("type", currentType);
            JSONArray tiers = new JSONArray();

            for (RateItem item : itemsToSave) {
                JSONObject tier = new JSONObject();
                tier.put("tier_name", item.getName());
                tier.put("min", item.getMin());

                // Nếu max = 0 hoặc user để trống -> gửi null (cho mức vô cực)
                if (item.getMax() == 0) {
                    tier.put("max", JSONObject.NULL);
                } else {
                    tier.put("max", item.getMax());
                }

                tier.put("price", item.getPrice());
                tiers.put(tier);
            }
            body.put("tiers", tiers);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                    response -> {
                        btnSaveRates.setEnabled(true);
                        btnSaveRates.setText("Lưu cấu hình");
                        Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();
                    },
                    error -> {
                        btnSaveRates.setEnabled(true);
                        btnSaveRates.setText("Lưu cấu hình");
                        Log.e("ConfigRate", "Save Error: " + error.toString());
                        Toast.makeText(this, "Lỗi khi lưu cấu hình", Toast.LENGTH_SHORT).show();
                    }
            );
            Volley.newRequestQueue(this).add(request);

        } catch (JSONException e) {
            e.printStackTrace();
            btnSaveRates.setEnabled(true);
        }
    }
}