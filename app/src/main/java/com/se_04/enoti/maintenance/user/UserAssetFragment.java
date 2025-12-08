package com.se_04.enoti.maintenance.user;

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
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
// 🔥 Đảm bảo import đúng Adapter chúng ta đã viết ở phần Admin
import com.se_04.enoti.maintenance.admin.AssetAdapter;
import com.se_04.enoti.maintenance.AssetItem;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class UserAssetFragment extends Fragment {

    private RecyclerView recyclerView;
    private AssetAdapter adapter;
    private List<AssetItem> assetList = new ArrayList<>();
    private TextView txtEmptyAssets;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user_asset, container, false);

        setupWelcomeViews(view);
        setupRecyclerView(view);
        txtEmptyAssets = view.findViewById(R.id.txtEmptyAssets);

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
        String username = (currentUser != null) ? currentUser.getName() : "Cư dân";
        txtWelcome.setText("Xin chào " + username + "!");

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

        // 🔥 Sự kiện click: Chuyển ID và Tên thiết bị sang Activity báo cáo
        adapter.setOnItemClickListener(item -> {
            Intent intent = new Intent(getActivity(), AssetDetailActivity.class);
            intent.putExtra("ASSET_ID", item.getId());
            intent.putExtra("ASSET_NAME", item.getName());
            startActivity(intent);
        });

        recyclerView.setAdapter(adapter);
    }

    private void loadAssets() {
        if (getContext() == null) return;

        // Dùng chung API lấy danh sách tài sản
        String url = ApiConfig.BASE_URL + "/api/maintenance/assets";

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    assetList.clear(); // Xóa cũ tránh trùng lặp
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            assetList.add(new AssetItem(obj));
                        }
                        adapter.notifyDataSetChanged();

                        if (assetList.isEmpty()) {
                            if (txtEmptyAssets != null) txtEmptyAssets.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            if (txtEmptyAssets != null) txtEmptyAssets.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (getContext() != null) {
                        Log.e("UserAssetFragment", "Error: " + error.getMessage());
                        if (txtEmptyAssets != null) {
                            txtEmptyAssets.setText("Lỗi kết nối server!");
                            txtEmptyAssets.setVisibility(View.VISIBLE);
                        }
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