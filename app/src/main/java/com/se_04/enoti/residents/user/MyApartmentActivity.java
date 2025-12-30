package com.se_04.enoti.residents.user;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyApartmentActivity extends BaseActivity {

    private TextView tvRoomNumber, tvFloor, tvArea, tvMyRole, tvEmpty;
    private RecyclerView rvMembers;
    private SwipeRefreshLayout swipeRefresh;

    private MyRoomMemberAdapter adapter;
    private List<JSONObject> memberList = new ArrayList<>();

    private static final String API_URL = ApiConfig.BASE_URL + "/api/users/my-apartment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_apartment);

        initViews();
        setupRecyclerView();
        fetchData();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvRoomNumber = findViewById(R.id.tvRoomNumber);
        tvFloor = findViewById(R.id.tvFloor);
        tvArea = findViewById(R.id.tvArea);
        tvMyRole = findViewById(R.id.tvMyRole);
        tvEmpty = findViewById(R.id.tvEmpty);
        rvMembers = findViewById(R.id.rvMembers);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        swipeRefresh.setOnRefreshListener(this::fetchData);
    }

    private void setupRecyclerView() {
        rvMembers.setLayoutManager(new LinearLayoutManager(this));

        // Lấy ID user hiện tại để Adapter biết mà highlight chữ "Tôi"
        int currentUserId = 0;
        if (UserManager.getInstance(this).getCurrentUser() != null) {
            currentUserId = Integer.parseInt(UserManager.getInstance(this).getCurrentUser().getId());
        }

        adapter = new MyRoomMemberAdapter(this, memberList, currentUserId);
        rvMembers.setAdapter(adapter);
    }

    private void fetchData() {
        swipeRefresh.setRefreshing(true);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, API_URL, null,
                response -> {
                    swipeRefresh.setRefreshing(false);
                    try {
                        if (response.getBoolean("success")) {
                            // 1. Parse thông tin phòng
                            JSONObject apt = response.getJSONObject("apartment");

                            tvRoomNumber.setText("P." + apt.getString("number"));
                            tvFloor.setText(apt.getString("floor"));
                            tvArea.setText(apt.getString("area") + " m²");
                            tvMyRole.setText(apt.getString("my_role"));

                            // 2. Parse danh sách thành viên
                            memberList.clear();
                            JSONArray membersArr = response.getJSONArray("members");

                            if (membersArr.length() > 0) {
                                for (int i = 0; i < membersArr.length(); i++) {
                                    memberList.add(membersArr.getJSONObject(i));
                                }
                                tvEmpty.setVisibility(View.GONE);
                                rvMembers.setVisibility(View.VISIBLE);
                            } else {
                                tvEmpty.setVisibility(View.VISIBLE);
                                rvMembers.setVisibility(View.GONE);
                            }

                            adapter.notifyDataSetChanged();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Lỗi xử lý dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    swipeRefresh.setRefreshing(false);
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        // Trường hợp user chưa được xếp vào phòng
                        tvRoomNumber.setText("Chưa có");
                        tvFloor.setText("--");
                        tvArea.setText("--");
                        tvMyRole.setText("Vô gia cư");
                        memberList.clear();
                        adapter.notifyDataSetChanged();
                        tvEmpty.setText("Bạn chưa được xếp vào căn hộ nào.");
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(this, "Lỗi kết nối: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
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
}