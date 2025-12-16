package com.se_04.enoti.maintenance.user;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import com.se_04.enoti.maintenance.AssetImageAdapter;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssetDetailActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private TextView txtName, txtId, txtLocation, txtStatus, lblHistory;
    private ImageView imgStatusIcon;

    private RecyclerView recyclerHistory;
    private AssetHistoryAdapter historyAdapter;
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

    // 🔥 Biến lưu trạng thái hiện tại để dùng cho Dialog Edit
    private String currentStatus = "Good";

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

        // 🔥 1. SETUP RECYCLERVIEW CHO ẢNH
        recyclerImages = findViewById(R.id.recyclerAssetImages);
        if (recyclerImages != null) {
            recyclerImages.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            imageAdapter = new AssetImageAdapter(imageUrls, url -> {
                // Click vào ảnh -> Mở xem full
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

        // 2. SETUP RECYCLERVIEW CHO LỊCH SỬ
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
            // Admin chỉ xem, ẩn nút báo cáo sự cố (vì admin sẽ dùng menu edit/delete)
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

    // =========================================================================
    // 🔥 PHẦN MỚI THÊM: MENU ADMIN & LOGIC CHỈNH SỬA
    // =========================================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Chỉ hiện menu nếu là Admin
        if (isAdmin) {
            getMenuInflater().inflate(R.menu.menu_asset_admin, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_edit) {
            showEditDialog();
            return true;
        } else if (item.getItemId() == R.id.action_delete) {
            confirmDeleteAsset();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chỉnh sửa thiết bị");

        // Tạo layout dialog bằng code Java thuần (đỡ phải tạo file XML mới)
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText edtName = new EditText(this);
        edtName.setHint("Tên thiết bị");
        edtName.setText(txtName.getText().toString());
        layout.addView(edtName);

        final EditText edtLocation = new EditText(this);
        edtLocation.setHint("Vị trí");
        String locText = txtLocation.getText().toString().replace("Vị trí: ", "");
        edtLocation.setText(locText);
        layout.addView(edtLocation);

        final TextView lblStatus = new TextView(this);
        lblStatus.setText("Trạng thái:");
        lblStatus.setPadding(0, 20, 0, 10);
        layout.addView(lblStatus);

        final Spinner spinnerStatus = new Spinner(this);
        String[] statuses = {"Good", "Maintenance", "Broken"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, statuses);
        spinnerStatus.setAdapter(adapter);

        // Set selection
        for (int i = 0; i < statuses.length; i++) {
            if (statuses[i].equalsIgnoreCase(currentStatus)) {
                spinnerStatus.setSelection(i);
                break;
            }
        }
        layout.addView(spinnerStatus);

        builder.setView(layout);

        builder.setPositiveButton("Lưu", (dialog, which) -> {
            String newName = edtName.getText().toString().trim();
            String newLocation = edtLocation.getText().toString().trim();
            String newStatus = spinnerStatus.getSelectedItem().toString();

            if (newName.isEmpty()) {
                Toast.makeText(this, "Tên không được để trống", Toast.LENGTH_SHORT).show();
                return;
            }

            updateAsset(newName, newLocation, newStatus);
        });

        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void updateAsset(String name, String location, String status) {
        String url = ApiConfig.BASE_URL + "/api/maintenance/asset/" + assetId;
        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("location", location);
            body.put("status", status);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, url, body,
                response -> {
                    Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();
                    fetchAssetDetails(); // Tải lại dữ liệu để cập nhật UI
                },
                error -> Toast.makeText(this, "Lỗi cập nhật thiết bị", Toast.LENGTH_SHORT).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }

    private void confirmDeleteAsset() {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa thiết bị này không? Hành động này không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) -> deleteAsset())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteAsset() {
        String url = ApiConfig.BASE_URL + "/api/maintenance/asset/" + assetId;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.DELETE, url, null,
                response -> {
                    Toast.makeText(this, "Đã xóa thiết bị!", Toast.LENGTH_SHORT).show();
                    finish(); // Đóng Activity và quay về danh sách
                },
                error -> Toast.makeText(this, "Lỗi khi xóa thiết bị", Toast.LENGTH_SHORT).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }

    // =========================================================================

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

                            // 🔥 LƯU STATUS CHO DIALOG EDIT
                            String status = assetObj.optString("status", "Good");
                            currentStatus = status;
                            updateStatusUI(status);
                        }

                        // 🔥 2. HIỂN THỊ DANH SÁCH ẢNH (GIỮ NGUYÊN)
                        if (response.has("images")) {
                            JSONArray imgArr = response.getJSONArray("images");
                            imageUrls.clear();
                            for (int i = 0; i < imgArr.length(); i++) {
                                imageUrls.add(imgArr.getString(i));
                            }
                            if (imageAdapter != null) imageAdapter.notifyDataSetChanged();

                            if (recyclerImages != null) {
                                recyclerImages.setVisibility(imageUrls.isEmpty() ? View.GONE : View.VISIBLE);
                            }
                        }

                        // 3. Hiển thị lịch sử (GIỮ NGUYÊN)
                        historyList.clear();
                        if (response.has("history")) {
                            JSONArray historyArr = response.getJSONArray("history");
                            for (int i = 0; i < historyArr.length(); i++) {
                                historyList.add(new AssetHistoryItem(historyArr.getJSONObject(i)));
                            }
                        }
                        historyAdapter.notifyDataSetChanged();

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