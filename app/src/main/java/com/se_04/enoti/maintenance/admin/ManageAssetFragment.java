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
import com.se_04.enoti.account.Role; // Import Role
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
    private FloatingActionButton btnAdd; // Khai báo biến toàn cục để ẩn hiện

    // 🔥 Biến kiểm tra quyền Agency
    private boolean isAgency = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_asset, container, false);

        // 1. Kiểm tra Role ngay khi khởi tạo
        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        if (currentUser != null && currentUser.getRole() == Role.AGENCY) {
            isAgency = true;
        }

        setupWelcomeViews(view, currentUser);
        setupRecyclerView(view);

        txtEmptyAssets = view.findViewById(R.id.txtEmptyAssets);
        btnAdd = view.findViewById(R.id.btnAddAsset);

        // 2. Setup các sự kiện click (Có kiểm tra quyền)
        setupClickListeners(view);

        // 3. Ẩn nút thêm nếu là Agency
        if (isAgency && btnAdd != null) {
            btnAdd.setVisibility(View.GONE);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAssets();
    }

    private void setupWelcomeViews(View view, UserItem currentUser) {
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

        String username = (currentUser != null) ? currentUser.getName() : "Quản trị viên";

        if (txtWelcome != null) txtWelcome.setText("Xin chào " + username + "!");

        if (txtGreeting != null) {
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            String timeOfDay = (hour >= 5 && hour < 11) ? "sáng" : (hour >= 11 && hour < 14) ? "trưa" : (hour >= 14 && hour < 18) ? "chiều" : "tối";
            txtGreeting.setText("Chúc bạn buổi " + timeOfDay + " tốt lành!");
        }
    }

    private void setupClickListeners(View view) {
        // --- Card Lịch bảo trì ---
        View cardSchedule = view.findViewById(R.id.cardMaintenanceSchedule);
        if (cardSchedule != null) {
            cardSchedule.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), MaintenanceActivity.class);
                // Truyền cờ isAgency sang Activity tiếp theo nếu cần ẩn nút sửa lịch
                intent.putExtra("IS_AGENCY", isAgency);
                startActivity(intent);
            });
        }

        // --- Card Báo cáo cư dân ---
        View cardReports = view.findViewById(R.id.cardResidentReports);
        if (cardReports != null) {
            cardReports.setOnClickListener(v -> {
                AdminReportBottomSheet bottomSheet = new AdminReportBottomSheet();

                // 🔥 TRUYỀN CỜ AGENCY VÀO BOTTOM SHEET
                // Để bên BottomSheet biết mà ẩn nút Duyệt/Từ chối
                Bundle args = new Bundle();
                args.putBoolean("IS_AGENCY", isAgency);
                bottomSheet.setArguments(args);

                bottomSheet.show(getParentFragmentManager(), "AdminReportBottomSheet");
            });
        }

        // --- Nút Thêm Tài sản ---
        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> {
                if (!isAgency) { // Chặn thêm lần nữa cho chắc
                    Intent intent = new Intent(getActivity(), AddAssetActivity.class);
                    startActivity(intent);
                }
            });
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

            // 🔥 Nếu là Agency, ta vẫn gửi IS_ADMIN = true để xem giao diện Admin
            // NHƯNG cần gửi thêm IS_AGENCY để bên Activity đó ẩn nút Sửa/Xóa
            intent.putExtra("IS_ADMIN", true);
            intent.putExtra("IS_AGENCY", isAgency);

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

                    // Lưu Cache
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
            JSONArray response = new JSONArray(jsonString);
            assetList.clear();
            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.getJSONObject(i);
                assetList.add(new AssetItem(obj));
            }
            adapter.notifyDataSetChanged();

            if (assetList.isEmpty() && txtEmptyAssets != null) {
                txtEmptyAssets.setVisibility(View.VISIBLE);
            } else if (txtEmptyAssets != null) {
                txtEmptyAssets.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }

        } catch (JSONException e) { e.printStackTrace(); }
    }
}