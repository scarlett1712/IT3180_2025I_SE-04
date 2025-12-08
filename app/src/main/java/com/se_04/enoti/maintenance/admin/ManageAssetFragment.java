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
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ManageAssetFragment extends Fragment {

    private RecyclerView recyclerView;
    private AssetAdapter adapter;
    private List<AssetItem> assetList = new ArrayList<>();

    // 🔥 Biến cho View thông báo trống
    private TextView txtEmptyAssets;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_asset, container, false);

        setupWelcomeViews(view);
        setupRecyclerView(view);

        // 🔥 Ánh xạ TextView trống
        txtEmptyAssets = view.findViewById(R.id.txtEmptyAssets);

        // Nút mở Lịch bảo trì
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

        // Nút thêm thiết bị
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
            Intent intent = new Intent(getActivity(), com.se_04.enoti.maintenance.user.AssetDetailActivity.class); // Trỏ tới Activity chi tiết
            intent.putExtra("ASSET_ID", item.getId());
            intent.putExtra("ASSET_NAME", item.getName());
            intent.putExtra("IS_ADMIN", true); // 🚩 Đánh dấu là Admin đang xem
            startActivity(intent);
        });

        recyclerView.setAdapter(adapter);
    }

    private void loadAssets() {
        if (getContext() == null) return;

        String url = ApiConfig.BASE_URL + "/api/maintenance/assets";

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    assetList.clear();
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            assetList.add(new AssetItem(obj));
                        }
                        adapter.notifyDataSetChanged();

                        // 🔥 LOGIC KIỂM TRA RỖNG Ở ĐÂY
                        if (assetList.isEmpty()) {
                            // Nếu danh sách rỗng -> Hiện chữ "Trống", Ẩn RecyclerView
                            txtEmptyAssets.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            // Nếu có dữ liệu -> Ẩn chữ "Trống", Hiện RecyclerView
                            txtEmptyAssets.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(), "Lỗi xử lý dữ liệu", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (getContext() != null) {
                        Log.e("ManageAssetFragment", "Error loading assets: " + error.getMessage());
                        // Nếu lỗi mạng, có thể coi như không có dữ liệu để hiển thị
                        // Hoặc bạn có thể set text khác như "Lỗi kết nối"
                        txtEmptyAssets.setVisibility(View.VISIBLE);
                        txtEmptyAssets.setText("Không thể tải dữ liệu. Kiểm tra kết nối!");
                        recyclerView.setVisibility(View.GONE);
                    }
                }
        );

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000, // Thời gian chờ: 30 giây
                0,     // Số lần thử lại: 0 (Để 0 để tránh gửi chồng request)
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        Volley.newRequestQueue(requireContext()).add(request);
    }
}