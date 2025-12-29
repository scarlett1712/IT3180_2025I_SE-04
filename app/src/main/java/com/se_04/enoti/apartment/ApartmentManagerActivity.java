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
    private LinearLayout layoutEmpty;       // View hiển thị khi không có dữ liệu
    private SearchView searchView;          // Thanh tìm kiếm
    private Spinner spinnerStatus;          // Bộ lọc trạng thái
    private MaterialToolbar toolbar;

    private ApartmentAdapter adapter;

    // fullList: Lưu toàn bộ dữ liệu tải từ Server
    private List<Apartment> fullList = new ArrayList<>();
    // displayList: Lưu dữ liệu sau khi lọc để hiển thị lên màn hình
    private List<Apartment> displayList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apartment_manager);

        // 1. Ánh xạ View theo Layout mới
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        fabAdd = findViewById(R.id.fabAdd);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        searchView = findViewById(R.id.searchView);
        spinnerStatus = findViewById(R.id.spinnerStatusFilter);

        // 2. Setup Toolbar
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 3. Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // Lưu ý: Adapter sử dụng displayList (danh sách đã lọc)
        adapter = new ApartmentAdapter(displayList, new ApartmentAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Apartment apartment) {
                Intent intent = new Intent(ApartmentManagerActivity.this, ApartmentEditorActivity.class);
                intent.putExtra("apartment", apartment);
                startActivity(intent);
            }

            @Override
            public void onItemLongClick(Apartment apartment) {
                new AlertDialog.Builder(ApartmentManagerActivity.this)
                        .setTitle("Xóa phòng " + apartment.getApartmentNumber() + "?")
                        .setMessage("Hành động này không thể hoàn tác.")
                        .setPositiveButton("Xóa", (dialog, which) -> deleteApartment(apartment.getId()))
                        .setNegativeButton("Hủy", null)
                        .show();
            }
        });
        recyclerView.setAdapter(adapter);

        // 4. Setup Spinner (Bộ lọc trạng thái)
        String[] filters = {"Tất cả", "Trống", "Đã có người"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, filters);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(spinnerAdapter);

        // Sự kiện chọn Spinner
        spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterData(searchView.getQuery().toString());
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 5. Setup SearchView (Tìm kiếm)
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

        // 6. Sự kiện nút Thêm
        fabAdd.setOnClickListener(v -> {
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
                    fullList.clear(); // Xóa danh sách gốc cũ
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            fullList.add(new Apartment(
                                    obj.getInt("apartment_id"),
                                    obj.getString("apartment_number"),
                                    obj.getInt("floor"),
                                    obj.getDouble("area"),
                                    obj.optString("status", "trong")
                            ));
                        }
                        // Sau khi tải xong, gọi hàm lọc để cập nhật displayList và UI
                        filterData(searchView.getQuery().toString());

                    } catch (Exception e) { e.printStackTrace(); }
                },
                error -> Toast.makeText(this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show()
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

    // 🔥 Hàm lọc dữ liệu kết hợp Tìm kiếm & Spinner
    private void filterData(String keyword) {
        displayList.clear();
        String selectedStatus = spinnerStatus.getSelectedItem().toString();
        String searchLower = keyword.toLowerCase().trim();

        for (Apartment item : fullList) {
            // 1. Kiểm tra từ khóa tìm kiếm (Số phòng)
            boolean matchesKeyword = item.getApartmentNumber().toLowerCase().contains(searchLower);

            // 2. Kiểm tra trạng thái Spinner
            boolean matchesStatus = true;
            if (selectedStatus.equals("Trống")) {
                // Giả sử DB lưu 'trong' hoặc null là trống, 'occupied' là có người
                matchesStatus = !"occupied".equalsIgnoreCase(item.getStatus());
            } else if (selectedStatus.equals("Đã có người")) {
                matchesStatus = "occupied".equalsIgnoreCase(item.getStatus());
            }

            // Nếu thỏa mãn cả 2 điều kiện
            if (matchesKeyword && matchesStatus) {
                displayList.add(item);
            }
        }

        // Cập nhật giao diện
        adapter.notifyDataSetChanged();

        // Hiển thị layout Empty nếu không có kết quả
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
                    Toast.makeText(this, "Đã xóa!", Toast.LENGTH_SHORT).show();
                    loadData(); // Tải lại dữ liệu sau khi xóa
                },
                error -> Toast.makeText(this, "Không thể xóa (Có thể đang có người ở)", Toast.LENGTH_LONG).show()
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