package com.se_04.enoti.maintenance.user;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
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
import com.se_04.enoti.maintenance.AssetImageAdapter; // 🔥 Import Adapter ảnh mới
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
    private TextView txtName, txtId, txtLocation, txtStatus, lblHistory;
    private ImageView imgStatusIcon;

    private RecyclerView recyclerHistory;
    private AssetHistoryAdapter historyAdapter; // Đổi tên cho rõ ràng
    private List<AssetHistoryItem> historyList = new ArrayList<>();

    // 🔥 CÁC BIẾN MỚI CHO ẢNH
    private RecyclerView recyclerImages;
    private AssetImageAdapter imageAdapter;
    private final List<String> imageUrls = new ArrayList<>();

    private ExtendedFloatingActionButton btnReport;
    private View layoutEmptyHistory;

    private int assetId;
    private String assetNameString;
    private boolean isAdmin = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asset_detail);

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
        txtId = findViewById(R.id.txtDetailId);
        txtLocation = findViewById(R.id.txtDetailLocation);
        txtStatus = findViewById(R.id.txtDetailStatus);
        imgStatusIcon = findViewById(R.id.imgStatusIcon);

        lblHistory = findViewById(R.id.lblHistory);
        layoutEmptyHistory = findViewById(R.id.layoutEmptyHistory);

        btnReport = findViewById(R.id.btnGoToReport);

        // 🔥 1. SETUP RECYCLERVIEW CHO ẢNH (MỚI)
        recyclerImages = findViewById(R.id.recyclerAssetImages); // Đảm bảo bạn đã thêm ID này vào XML
        if (recyclerImages != null) {
            recyclerImages.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            imageAdapter = new AssetImageAdapter(imageUrls, url -> {
                // Click vào ảnh -> Mở xem full (Intent mặc định của Android)
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(url), "image/*");
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Không thể mở ảnh", Toast.LENGTH_SHORT).show();
                }
            });
            recyclerImages.setAdapter(imageAdapter);
        }

        // 2. SETUP RECYCLERVIEW CHO LỊCH SỬ (GIỮ NGUYÊN)
        recyclerHistory = findViewById(R.id.recyclerHistory);
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new AssetHistoryAdapter(historyList);
        recyclerHistory.setAdapter(historyAdapter);
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
            // Admin chỉ xem
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
                            txtId.setText("Mã: " + assetObj.optString("asset_id", String.valueOf(assetId)));
                            txtLocation.setText("Vị trí: " + assetObj.optString("location", "N/A"));

                            String status = assetObj.optString("status", "Good");
                            updateStatusUI(status);
                        }

                        // 🔥 2. HIỂN THỊ DANH SÁCH ẢNH (MỚI)
                        if (response.has("images")) {
                            JSONArray imgArr = response.getJSONArray("images");
                            imageUrls.clear();
                            for (int i = 0; i < imgArr.length(); i++) {
                                imageUrls.add(imgArr.getString(i));
                            }
                            if (imageAdapter != null) imageAdapter.notifyDataSetChanged();

                            // Ẩn hiện Recycler nếu không có ảnh
                            if (recyclerImages != null) {
                                recyclerImages.setVisibility(imageUrls.isEmpty() ? View.GONE : View.VISIBLE);
                            }
                        }

                        // 3. Hiển thị lịch sử (Giữ nguyên)
                        historyList.clear();
                        if (response.has("history")) {
                            JSONArray historyArr = response.getJSONArray("history");
                            for (int i = 0; i < historyArr.length(); i++) {
                                historyList.add(new AssetHistoryItem(historyArr.getJSONObject(i)));
                            }
                        }
                        historyAdapter.notifyDataSetChanged();

                        // Xử lý Empty State cho lịch sử
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

    private void updateStatusUI(String status) {
        int iconRes;
        String statusText;

        if ("Good".equalsIgnoreCase(status)) {
            statusText = "Hoạt động tốt";
            iconRes = R.drawable.ic_check_circle;
        } else if ("Maintenance".equalsIgnoreCase(status)) {
            statusText = "Đang bảo trì";
            iconRes = R.drawable.ic_history;
        } else if ("Broken".equalsIgnoreCase(status)) {
            statusText = "Đang gặp sự cố";
            iconRes = R.drawable.ic_report_problem;
        } else {
            statusText = status;
            iconRes = R.drawable.ic_devices;
        }

        txtStatus.setText(statusText);
        imgStatusIcon.setImageResource(iconRes);
    }
}