package com.se_04.enoti.maintenance.user;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
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

    private TextView txtName, txtLocation, txtStatus, txtEmpty, txtHistoryTitle;
    private RecyclerView recyclerHistory;
    private AssetHistoryAdapter adapter;
    private List<AssetHistoryItem> historyList = new ArrayList<>();
    private ExtendedFloatingActionButton btnReport;

    private int assetId;
    private String assetNameString;
    private boolean isAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔥 1. Làm trong suốt Status Bar để Gradient hiển thị đẹp
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            android.view.Window window = getWindow();
            window.getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        }

        setContentView(R.layout.activity_asset_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        assetId = getIntent().getIntExtra("ASSET_ID", -1);
        assetNameString = getIntent().getStringExtra("ASSET_NAME");
        isAdmin = getIntent().getBooleanExtra("IS_ADMIN", false);

        if (assetId == -1) {
            Toast.makeText(this, "Lỗi: Không có ID thiết bị", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupUIForRole();
        fetchAssetDetails();
    }

    private void initViews() {
        txtName = findViewById(R.id.txtDetailName);
        txtLocation = findViewById(R.id.txtDetailLocation);
        txtStatus = findViewById(R.id.txtDetailStatus);
        txtEmpty = findViewById(R.id.txtEmptyHistory);
        txtHistoryTitle = findViewById(R.id.txtHistoryTitle); // Đảm bảo ID này tồn tại trong XML

        recyclerHistory = findViewById(R.id.recyclerHistory);
        btnReport = findViewById(R.id.btnGoToReport);

        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AssetHistoryAdapter(historyList);
        recyclerHistory.setAdapter(adapter);
    }

    private void setupUIForRole() {
        if (isAdmin) {
            btnReport.setVisibility(View.GONE);
            if (txtHistoryTitle != null) txtHistoryTitle.setText("Lịch sử bảo trì");
        } else {
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
            if (user == null) {
                Toast.makeText(this, "Chưa đăng nhập", Toast.LENGTH_SHORT).show();
                return;
            }
            url = ApiConfig.BASE_URL + "/api/maintenance/asset/" + assetId + "/details?user_id=" + user.getId();
        }

        Log.d("AssetDetail", "Fetching: " + url);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        // 1. Hiển thị thông tin chung
                        if (response.has("asset")) {
                            JSONObject assetObj = response.getJSONObject("asset");
                            txtName.setText(assetObj.optString("asset_name", "N/A"));
                            txtLocation.setText("Vị trí: " + assetObj.optString("location", "N/A"));

                            // 🔥 2. Cập nhật logic hiển thị trạng thái
                            String status = assetObj.optString("status", "Good");

                            if ("Good".equalsIgnoreCase(status)) {
                                txtStatus.setText("Trạng thái: Hoạt động tốt");
                                txtStatus.setTextColor(Color.parseColor("#388E3C")); // Xanh lá
                            }
                            else if ("Maintenance".equalsIgnoreCase(status)) {
                                txtStatus.setText("Trạng thái: Đang bảo trì");
                                txtStatus.setTextColor(Color.parseColor("#1976D2")); // Xanh dương
                            }
                            else if ("Broken".equalsIgnoreCase(status)) {
                                txtStatus.setText("Trạng thái: Đang gặp sự cố");
                                txtStatus.setTextColor(Color.parseColor("#D32F2F")); // Đỏ
                            }
                            else {
                                txtStatus.setText("Trạng thái: " + status);
                                txtStatus.setTextColor(Color.DKGRAY);
                            }
                        }

                        // 3. Hiển thị lịch sử
                        historyList.clear();
                        if (response.has("history")) {
                            JSONArray historyArr = response.getJSONArray("history");
                            for (int i = 0; i < historyArr.length(); i++) {
                                historyList.add(new AssetHistoryItem(historyArr.getJSONObject(i)));
                            }
                        }
                        adapter.notifyDataSetChanged();

                        if (historyList.isEmpty()) txtEmpty.setVisibility(View.VISIBLE);
                        else txtEmpty.setVisibility(View.GONE);

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Lỗi xử lý dữ liệu", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    // 🔥 3. Bỏ qua lỗi 404 (Không có dữ liệu chi tiết)
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        Log.w("AssetDetail", "Chưa có dữ liệu lịch sử (404).");
                        txtEmpty.setVisibility(View.VISIBLE);
                        txtEmpty.setText("Chưa có lịch sử bảo trì.");
                    } else {
                        Log.e("AssetDetail", "Error: " + error.toString());
                        Toast.makeText(this, "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        Volley.newRequestQueue(this).add(request);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}