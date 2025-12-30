package com.se_04.enoti.apartment;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApartmentManagerActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private FloatingActionButton fabAdd;
    private LinearLayout layoutEmpty;
    private SearchView searchView;
    private Spinner spinnerStatus;
    private MaterialToolbar toolbar;

    private ApartmentAdapter adapter;
    private List<Apartment> fullList = new ArrayList<>();
    private List<Apartment> displayList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apartment_manager);

        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        fabAdd = findViewById(R.id.fabAdd);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        searchView = findViewById(R.id.searchView);
        spinnerStatus = findViewById(R.id.spinnerStatusFilter);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Quản lý Căn hộ");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Cấu hình Adapter
        adapter = new ApartmentAdapter(displayList, new ApartmentAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Apartment apartment) {
                // 🔥 SỬA ĐỔI QUAN TRỌNG:
                // Bấm vào item -> Mở màn hình quản lý CƯ DÂN trong phòng đó
                Intent intent = new Intent(ApartmentManagerActivity.this, ApartmentResidentsActivity.class);
                intent.putExtra("apartment", apartment);
                startActivity(intent);
            }

            @Override
            public void onItemLongClick(Apartment apartment) {
                // Giữ lì -> Xóa phòng (Backend đã xử lý việc đẩy dân ra đường)
                new AlertDialog.Builder(ApartmentManagerActivity.this)
                        .setTitle("Xóa phòng " + apartment.getApartmentNumber() + "?")
                        .setMessage("Hành động này sẽ chuyển tất cả cư dân trong phòng sang danh sách 'Vô gia cư'. Bạn có chắc không?")
                        .setPositiveButton("Xóa", (dialog, which) -> deleteApartment(apartment.getId()))
                        .setNegativeButton("Hủy", null)
                        .show();
            }
        });
        recyclerView.setAdapter(adapter);

        // Setup Spinner Lọc trạng thái
        String[] filters = {"Tất cả", "Trống", "Đã có người"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, filters);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(spinnerAdapter);

        spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String query = (searchView.getQuery() != null) ? searchView.getQuery().toString() : "";
                filterData(query);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Setup Tìm kiếm
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterData(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterData(newText);
                return false;
            }
        });

        // Nút thêm phòng mới
        fabAdd.setOnClickListener(v -> {
            // Vẫn giữ tính năng thêm phòng mới (Editor)
            startActivity(new Intent(this, ApartmentEditorActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        String GET_APARTMENTS = ApiConfig.BASE_URL + "/api/apartments";
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, GET_APARTMENTS, null,
                response -> {
                    fullList.clear();
                    for (int i = 0; i < response.length(); i++) {
                        try {
                            JSONObject obj = response.getJSONObject(i);

                            // Lọc bỏ dữ liệu lỗi (số phòng null)
                            if (obj.isNull("apartment_number")) continue;
                            String aptNum = obj.optString("apartment_number", "").trim();
                            if (aptNum.isEmpty() || aptNum.equalsIgnoreCase("null")) continue;

                            fullList.add(new Apartment(
                                    obj.optInt("apartment_id", -1),
                                    aptNum,
                                    obj.optInt("floor", 0),
                                    obj.optDouble("area", 0.0),
                                    obj.optString("status", "trong")
                            ));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    // Cập nhật giao diện
                    String currentQuery = (searchView.getQuery() != null) ? searchView.getQuery().toString() : "";
                    filterData(currentQuery);
                },
                error -> {
                    Toast.makeText(this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show();
                    error.printStackTrace();
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

    private void filterData(String keyword) {
        displayList.clear();
        String searchLower = (keyword == null) ? "" : keyword.toLowerCase().trim();

        String selectedStatus = "Tất cả";
        if (spinnerStatus.getSelectedItem() != null) {
            selectedStatus = spinnerStatus.getSelectedItem().toString();
        }

        for (Apartment item : fullList) {
            // 1. Tìm kiếm theo số phòng
            String aptNum = item.getApartmentNumber();
            boolean matchesKeyword = (aptNum != null) && aptNum.toLowerCase().contains(searchLower);

            // 2. Lọc theo trạng thái
            boolean matchesStatus = true;
            String status = item.getStatus();

            if (selectedStatus.equals("Trống")) {
                matchesStatus = !"Occupied".equalsIgnoreCase(status);
            } else if (selectedStatus.equals("Đã có người")) {
                matchesStatus = "Occupied".equalsIgnoreCase(status);
            }

            if (matchesKeyword && matchesStatus) {
                displayList.add(item);
            }
        }

        adapter.notifyDataSetChanged();

        if (displayList.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void deleteApartment(int id) {
        String DELETE_APARTMENT = ApiConfig.BASE_URL + "/api/apartments/delete/";
        String url = DELETE_APARTMENT + id;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.DELETE, url, null,
                response -> {
                    Toast.makeText(this, "Đã xóa phòng!", Toast.LENGTH_SHORT).show();
                    loadData();
                },
                error -> Toast.makeText(this, "Lỗi xóa phòng: " + error.getMessage(), Toast.LENGTH_LONG).show()
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