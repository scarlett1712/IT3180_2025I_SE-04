package com.se_04.enoti.maintenance.admin; // Đổi package cho phù hợp

import android.content.Intent;
import android.os.Bundle;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_asset, container, false);

        setupWelcomeViews(view);
        setupRecyclerView(view);

        // 🔥 Nút mở Lịch bảo trì (MaintenanceActivity)
        View cardSchedule = view.findViewById(R.id.cardMaintenanceSchedule);
        cardSchedule.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MaintenanceActivity.class);
            startActivity(intent);
        });

        // 🔥 Nút thêm thiết bị (AddAssetActivity)
        FloatingActionButton btnAdd = view.findViewById(R.id.btnAddAsset);
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AddAssetActivity.class);
            startActivity(intent);
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAssets(); // Load lại dữ liệu khi quay lại màn hình này
    }

    private void setupWelcomeViews(View view) {
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Admin";
        txtWelcome.setText("Xin chào " + username + "!");

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay = (hour >= 5 && hour < 11) ? "sáng" : (hour >= 11 && hour < 14) ? "trưa" : (hour >= 14 && hour < 18) ? "chiều" : "tối";
        txtGreeting.setText("Chúc bạn một buổi " + timeOfDay + " vui vẻ!");
    }

    private void setupRecyclerView(View view) {
        recyclerView = view.findViewById(R.id.recyclerViewAssets);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AssetAdapter(assetList);
        recyclerView.setAdapter(adapter);
    }

    private void loadAssets() {
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
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Không thể tải danh sách thiết bị", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        Volley.newRequestQueue(requireContext()).add(request);
    }
}