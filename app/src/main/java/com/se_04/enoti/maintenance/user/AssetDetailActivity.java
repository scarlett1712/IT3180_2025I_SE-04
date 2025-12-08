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
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AssetDetailActivity extends AppCompatActivity {

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
        // 🔥 Lưu ý: XML phải có ID này, nếu không hãy xóa dòng này và xóa biến txtHistoryTitle
        txtHistoryTitle = findViewById(R.id.txtHistoryTitle);

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
                    Log.d("AssetDetail", "Response: " + response.toString());
                    try {
                        // 1. Info
                        if (response.has("asset")) {
                            JSONObject assetObj = response.getJSONObject("asset");
                            txtName.setText(assetObj.optString("asset_name", "N/A"));
                            txtLocation.setText("Vị trí: " + assetObj.optString("location", "N/A"));

                            String status = assetObj.optString("status", "");
                            if ("Good".equals(status)) {
                                txtStatus.setText("Trạng thái: Hoạt động tốt");
                                txtStatus.setTextColor(Color.parseColor("#388E3C"));
                            } else {
                                txtStatus.setText("Trạng thái: Đang bảo trì");
                                txtStatus.setTextColor(Color.parseColor("#D32F2F"));
                            }
                        }

                        // 2. History
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
                    Log.e("AssetDetail", "Error: " + error.toString());
                    Toast.makeText(this, "Không thể tải dữ liệu", Toast.LENGTH_SHORT).show();
                }
        );

        Volley.newRequestQueue(this).add(request);
    }
}