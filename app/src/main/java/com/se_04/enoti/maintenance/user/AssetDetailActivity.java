package com.se_04.enoti.maintenance.user;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.maintenance.AssetHistoryItem;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AssetDetailActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private TextView txtName, txtId, txtLocation, txtStatus, txtEmptyHistory, lblHistory;
    private ImageView imgStatusIcon;

    private RecyclerView recyclerHistory;
    private AssetHistoryAdapter adapter;
    private List<AssetHistoryItem> historyList = new ArrayList<>();

    private ExtendedFloatingActionButton btnReport;
    private View layoutEmptyHistory;

    private int assetId;
    private String assetNameString;
    private boolean isAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asset_detail); // Sử dụng layout mới

        // Lấy dữ liệu từ Intent
        assetId = getIntent().getIntExtra("ASSET_ID", -1);
        assetNameString = getIntent().getStringExtra("ASSET_NAME");
        isAdmin = getIntent().getBooleanExtra("IS_ADMIN", false);

        if (assetId == -1) {
            Toast.makeText(this, "Lỗi: Không tìm thấy thiết bị", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupUIForRole();

        // Tải dữ liệu
        fetchAssetDetails();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);

        txtName = findViewById(R.id.txtDetailName);
        txtId = findViewById(R.id.txtDetailId); // Thêm ID mới
        txtLocation = findViewById(R.id.txtDetailLocation);
        txtStatus = findViewById(R.id.txtDetailStatus);
        imgStatusIcon = findViewById(R.id.imgStatusIcon); // Icon trạng thái

        lblHistory = findViewById(R.id.lblHistory); // Tiêu đề danh sách
        txtEmptyHistory = findViewById(R.id.txtEmptyHistory);
        layoutEmptyHistory = findViewById(R.id.layoutEmptyHistory);

        recyclerHistory = findViewById(R.id.recyclerHistory);
        btnReport = findViewById(R.id.btnGoToReport);

        // Setup RecyclerView
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AssetHistoryAdapter(historyList);
        recyclerHistory.setAdapter(adapter);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết thiết bị");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupUIForRole() {
        if (isAdmin) {
            // Admin chỉ xem, không báo hỏng ở đây (hoặc tùy logic của bạn)
            btnReport.setVisibility(View.GONE);
            if (lblHistory != null) lblHistory.setText("LỊCH SỬ BẢO TRÌ TOÀN BỘ");
        } else {
            // User có nút báo hỏng
            btnReport.setOnClickListener(v -> {
                Intent intent = new Intent(this, ReportIssueActivity.class);
                intent.putExtra("ASSET_ID", assetId);
                intent.putExtra("ASSET_NAME", assetNameString);
                startActivity(intent);
            });
        }
    }

    private void fetchAssetDetails() {
        String url;
        if (isAdmin) {
            url = ApiConfig.BASE_URL + "/api/maintenance/asset/" + assetId + "/details?role=admin";
        } else {
            UserItem user = UserManager.getInstance(this).getCurrentUser();
            if (user == null) return;
            url = ApiConfig.BASE_URL + "/api/maintenance/asset/" + assetId + "/details?user_id=" + user.getId();
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        // 1. Hiển thị thông tin chung
                        if (response.has("asset")) {
                            JSONObject assetObj = response.getJSONObject("asset");

                            txtName.setText(assetObj.optString("asset_name", "N/A"));
                            txtId.setText("Mã: " + assetObj.optString("id", String.valueOf(assetId))); // Hiển thị ID
                            txtLocation.setText("Vị trí: " + assetObj.optString("location", "N/A"));

                            // 🔥 Cập nhật trạng thái (Text + Màu + Icon)
                            String status = assetObj.optString("status", "Good");
                            updateStatusUI(status);
                        }

                        // 2. Hiển thị lịch sử
                        historyList.clear();
                        if (response.has("history")) {
                            JSONArray historyArr = response.getJSONArray("history");
                            for (int i = 0; i < historyArr.length(); i++) {
                                historyList.add(new AssetHistoryItem(historyArr.getJSONObject(i)));
                            }
                        }
                        adapter.notifyDataSetChanged();

                        // 3. Xử lý Empty State
                        if (historyList.isEmpty()) {
                            layoutEmptyHistory.setVisibility(View.VISIBLE);
                            recyclerHistory.setVisibility(View.GONE);
                        } else {
                            layoutEmptyHistory.setVisibility(View.GONE);
                            recyclerHistory.setVisibility(View.VISIBLE);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Lỗi xử lý dữ liệu", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        layoutEmptyHistory.setVisibility(View.VISIBLE);
                        recyclerHistory.setVisibility(View.GONE);
                    } else {
                        Log.e("AssetDetail", "Error: " + error.toString());
                        Toast.makeText(this, "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        Volley.newRequestQueue(this).add(request);
    }

    // Helper cập nhật UI trạng thái
    private void updateStatusUI(String status) {
        int colorRes;
        int iconRes;
        String statusText;

        if ("Good".equalsIgnoreCase(status)) {
            statusText = "Hoạt động tốt";
            colorRes = R.color.holo_green_dark; // Hoặc mã màu #388E3C
            iconRes = R.drawable.ic_check_circle; // Icon tích xanh
        } else if ("Maintenance".equalsIgnoreCase(status)) {
            statusText = "Đang bảo trì";
            colorRes = R.color.blue_primary; // Hoặc #1976D2
            iconRes = R.drawable.ic_history; // Icon đồng hồ
        } else if ("Broken".equalsIgnoreCase(status)) {
            statusText = "Đang gặp sự cố";
            colorRes = R.color.red_primary; // Hoặc #D32F2F
            iconRes = R.drawable.ic_report_problem; // Icon cảnh báo
        } else {
            statusText = status;
            colorRes = android.R.color.darker_gray;
            iconRes = R.drawable.ic_devices;
        }

        // Set màu text
        txtStatus.setText(statusText);
        // Lưu ý: Cần context để lấy màu từ resource nếu dùng R.color
        // txtStatus.setTextColor(ContextCompat.getColor(this, colorRes));

        // Set icon tint
        imgStatusIcon.setImageResource(iconRes);
        // imgStatusIcon.setColorFilter(ContextCompat.getColor(this, colorRes));
    }
}