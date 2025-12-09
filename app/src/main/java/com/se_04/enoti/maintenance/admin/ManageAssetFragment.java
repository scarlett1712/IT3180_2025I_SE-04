package com.se_04.enoti.maintenance.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.maintenance.AssetItem;
import com.se_04.enoti.report.AdminReportBottomSheet;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.DataCacheManager;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ManageAssetFragment extends Fragment {

    private RecyclerView recyclerView;
    private AssetAdapter adapter;
    private List<AssetItem> assetList = new ArrayList<>();
    private TextView txtEmptyAssets;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_asset, container, false);

        setupWelcomeViews(view);
        setupRecyclerView(view);

        txtEmptyAssets = view.findViewById(R.id.txtEmptyAssets);

        View cardSchedule = view.findViewById(R.id.cardMaintenanceSchedule);
        if (cardSchedule != null) {
            cardSchedule.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), MaintenanceActivity.class);
                startActivity(intent);
            });
        }

        View cardReports = view.findViewById(R.id.cardResidentReports);
        if (cardReports != null) {
            cardReports.setOnClickListener(v -> {
                AdminReportBottomSheet bottomSheet = new AdminReportBottomSheet();
                bottomSheet.show(getParentFragmentManager(), "AdminReportBottomSheet");
            });
        }

        FloatingActionButton btnAdd = view.findViewById(R.id.btnAddAsset);
        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), AddAssetActivity.class);
                startActivity(intent);
            });
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAssets();
    }

    private void setupWelcomeViews(View view) {
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Quản trị viên";

        if (txtWelcome != null) txtWelcome.setText("Xin chào " + username + "!");

        if (txtGreeting != null) {
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            String timeOfDay = (hour >= 5 && hour < 11) ? "sáng" : (hour >= 11 && hour < 14) ? "trưa" : (hour >= 14 && hour < 18) ? "chiều" : "tối";
            txtGreeting.setText("Chúc bạn buổi " + timeOfDay + " tốt lành!");
        }
    }

    private void setupRecyclerView(View view) {
        recyclerView = view.findViewById(R.id.recyclerViewAssets);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new AssetAdapter(assetList);

        adapter.setOnItemClickListener(item -> {
            Intent intent = new Intent(getActivity(), com.se_04.enoti.maintenance.user.AssetDetailActivity.class);
            intent.putExtra("ASSET_ID", item.getId());
            intent.putExtra("ASSET_NAME", item.getName());
            intent.putExtra("IS_ADMIN", true);
            startActivity(intent);
        });

        recyclerView.setAdapter(adapter);
    }

    private void loadAssets() {
        if (getContext() == null) return;

        // 1. Load Cache
        String cachedData = DataCacheManager.getInstance(requireContext()).readCache(DataCacheManager.CACHE_ASSETS);
        if (cachedData != null && !cachedData.isEmpty()) {
            parseAndDisplayData(cachedData);
        }

        // 2. Load API
        String url = ApiConfig.BASE_URL + "/api/maintenance/assets";
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    if (getContext() == null) return;

                    // 🔥 Lưu Cache
                    DataCacheManager.getInstance(requireContext())
                            .saveCache(DataCacheManager.CACHE_ASSETS, response.toString());

                    parseAndDisplayData(response.toString());
                },
                error -> {
                    if (getContext() != null) {
                        Log.e("ManageAssetFragment", "Error: " + error.getMessage());
                        if (txtEmptyAssets != null) {
                            txtEmptyAssets.setVisibility(View.VISIBLE);
                            txtEmptyAssets.setText("Không thể tải dữ liệu.\nKiểm tra kết nối mạng!");
                        }
                        recyclerView.setVisibility(View.GONE);
                    }
                }
        );

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000, 0, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        Volley.newRequestQueue(requireContext()).add(request);
    }
    private void parseAndDisplayData(String jsonString) {
        try {
            JSONArray response = new JSONArray(jsonString); // Hoặc parse từ String nếu load từ cache
            assetList.clear();
            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.getJSONObject(i);
                assetList.add(new AssetItem(obj));
            }
            adapter.notifyDataSetChanged();

            // Xử lý empty view ...
            if (assetList.isEmpty() && txtEmptyAssets != null) {
                txtEmptyAssets.setVisibility(View.VISIBLE);
            } else if (txtEmptyAssets != null) {
                txtEmptyAssets.setVisibility(View.GONE);
            }

        } catch (JSONException e) { e.printStackTrace(); }
    }

}