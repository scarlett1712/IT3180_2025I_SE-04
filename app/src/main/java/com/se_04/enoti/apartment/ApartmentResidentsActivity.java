package com.se_04.enoti.apartment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.residents.ResidentItem;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApartmentResidentsActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private RecyclerView rvResidents;
    private Button btnAddResident;

    // Đối tượng phòng hiện tại
    private Apartment currentApartment;

    // Adapter hiển thị danh sách người trong phòng
    private ApartmentResidentAdapter adapter;

    // Danh sách người ĐANG Ở trong phòng này
    private List<ResidentItem> inRoomList = new ArrayList<>();
    // Danh sách người VÔ GIA CƯ (room == null) - Dùng để thêm vào phòng
    private List<ResidentItem> homelessList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apartment_residents);

        // 1. Nhận dữ liệu phòng từ Activity trước
        if (getIntent().hasExtra("apartment")) {
            currentApartment = (Apartment) getIntent().getSerializableExtra("apartment");
        } else {
            Toast.makeText(this, "Lỗi: Không tìm thấy thông tin phòng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 2. Ánh xạ View
        toolbar = findViewById(R.id.toolbar);
        rvResidents = findViewById(R.id.rvResidents);
        btnAddResident = findViewById(R.id.btnAddResident);

        // 3. Setup Toolbar
        setSupportActionBar(toolbar);
        updateToolbarTitle();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 4. Setup RecyclerView
        rvResidents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ApartmentResidentAdapter(inRoomList, this::confirmRemoveUser);
        rvResidents.setAdapter(adapter);

        // 5. Sự kiện nút Thêm người
        btnAddResident.setOnClickListener(v -> showAddUserDialog());

        // 6. Tải dữ liệu ban đầu
        loadAllResidents();
    }

    // ==================================================================
    // 📝 MENU CHỈNH SỬA THÔNG TIN PHÒNG
    // ==================================================================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu có nút Edit (cây bút chì)
        getMenuInflater().inflate(R.menu.menu_apartment_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_edit_apartment) {
            showEditApartmentDialog(); // Mở Dialog sửa phòng
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ==================================================================
    // 🏠 LOGIC QUẢN LÝ PHÒNG (Sửa thông tin)
    // ==================================================================
    private void showEditApartmentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Inflate layout dialog_apartment_editor.xml
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_apartment_editor, null);

        // Ánh xạ View trong Dialog
        EditText edtRoomNumber = view.findViewById(R.id.edtRoomNumber);
        EditText edtFloor = view.findViewById(R.id.edtFloor);
        EditText edtArea = view.findViewById(R.id.edtArea);
        Button btnSave = view.findViewById(R.id.btnSave);

        // Điền dữ liệu cũ vào form
        if (currentApartment != null) {
            edtRoomNumber.setText(currentApartment.getApartmentNumber());
            edtFloor.setText(String.valueOf(currentApartment.getFloor()));
            edtArea.setText(String.valueOf(currentApartment.getArea()));
        }

        AlertDialog dialog = builder.setView(view).create();

        // Sự kiện nút Lưu trong Dialog
        btnSave.setOnClickListener(v -> {
            String num = edtRoomNumber.getText().toString().trim();
            String floor = edtFloor.getText().toString().trim();
            String area = edtArea.getText().toString().trim();

            if (num.isEmpty() || floor.isEmpty() || area.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            // Gọi API cập nhật
            updateApartmentInfo(dialog, num, floor, area);
        });

        dialog.show();
    }

    private void updateApartmentInfo(AlertDialog dialog, String num, String floor, String area) {
        String url = ApiConfig.BASE_URL + "/api/apartments/update/" + currentApartment.getId();
        JSONObject body = new JSONObject();
        try {
            body.put("apartment_number", num);
            body.put("floor", Integer.parseInt(floor));
            body.put("area", Double.parseDouble(area));
            // Giữ nguyên status cũ
            body.put("status", currentApartment.getStatus());
            body.put("building_id", 1); // Mặc định building 1
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, url, body,
                response -> {
                    Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();

                    // Cập nhật lại đối tượng currentApartment để hiển thị mới ngay lập tức
                    currentApartment.setApartmentNumber(num);
                    currentApartment.setFloor(Integer.parseInt(floor));
                    currentApartment.setArea(Double.parseDouble(area));

                    // Cập nhật giao diện Toolbar
                    updateToolbarTitle();
                },
                error -> Toast.makeText(this, "Lỗi cập nhật: " + error.getMessage(), Toast.LENGTH_SHORT).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if(token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }

    private void updateToolbarTitle() {
        if (getSupportActionBar() != null && currentApartment != null) {
            getSupportActionBar().setTitle("Phòng " + currentApartment.getApartmentNumber());
            getSupportActionBar().setSubtitle("Tầng " + currentApartment.getFloor() + " - " + currentApartment.getArea() + "m²");
        }
    }

    // ==================================================================
    // 👥 LOGIC QUẢN LÝ CƯ DÂN (Load, Thêm, Xóa)
    // ==================================================================

    // 1. Tải toàn bộ cư dân và phân loại
    private void loadAllResidents() {
        String url = ApiConfig.BASE_URL + "/api/residents";

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    inRoomList.clear();
                    homelessList.clear();
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);

                            // Kiểm tra null cho trường room
                            String roomName = null;
                            if (!obj.isNull("apartment_number")) {
                                roomName = obj.getString("apartment_number");
                            }

                            // Tạo object ResidentItem từ JSON
                            ResidentItem item = new ResidentItem(
                                    obj.getInt("user_item_id"),
                                    obj.getInt("user_id"),
                                    obj.getString("full_name"),
                                    obj.optString("gender", ""),
                                    obj.optString("dob", ""),
                                    obj.optString("job", ""),
                                    obj.optString("email", ""),
                                    obj.optString("phone", ""),
                                    obj.optString("relationship_with_the_head_of_household", ""),
                                    obj.optString("family_id", ""),
                                    obj.optBoolean("is_living", true),
                                    roomName,
                                    obj.optString("identity_card", ""),
                                    obj.optString("home_town", "")
                            );

                            // 🔥 Phân loại cư dân
                            if (roomName != null && roomName.equals(currentApartment.getApartmentNumber())) {
                                // Người trong phòng này -> Thêm vào list hiển thị
                                inRoomList.add(item);
                            } else if (roomName == null || roomName.trim().isEmpty() || roomName.equals("null")) {
                                // Người chưa có phòng -> Thêm vào list vô gia cư
                                homelessList.add(item);
                            }
                        }
                        adapter.notifyDataSetChanged();

                    } catch (JSONException e) { e.printStackTrace(); }
                },
                error -> Toast.makeText(this, "Lỗi tải dữ liệu cư dân", Toast.LENGTH_SHORT).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if(token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }

    private void showAddUserDialog() {
        if (homelessList.isEmpty()) {
            Toast.makeText(this, "Không có cư dân nào đang chờ xếp phòng.", Toast.LENGTH_LONG).show();
            return;
        }

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_select_resident, null);

        EditText edtSearch = view.findViewById(R.id.edtSearch);
        RecyclerView rvSelect = view.findViewById(R.id.rvSelectResident);

        rvSelect.setLayoutManager(new LinearLayoutManager(this));

        SelectResidentAdapter selectAdapter = new SelectResidentAdapter(homelessList, selectedUser -> {
            // 🔥 THAY ĐỔI Ở ĐÂY:
            // Không gọi API ngay, mà mở Dialog nhập quan hệ trước
            bottomSheetDialog.dismiss(); // Đóng list chọn
            showInputRelationshipDialog(selectedUser); // Mở dialog nhập liệu
        });
        rvSelect.setAdapter(selectAdapter);

        // Logic tìm kiếm (giữ nguyên)
        edtSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                selectAdapter.filter(s.toString());
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.show();
    }

    // ==================================================================
    // 2. THÊM HÀM MỚI: HIỂN THỊ DIALOG NHẬP QUAN HỆ
    // ==================================================================
    private void showInputRelationshipDialog(ResidentItem user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_input_relationship, null);

        TextView tvTargetName = view.findViewById(R.id.tvTargetName);
        EditText edtRelation = view.findViewById(R.id.edtRelation);
        android.widget.CheckBox chkIsHead = view.findViewById(R.id.chkIsHead);
        Button btnConfirm = view.findViewById(R.id.btnConfirmAdd);

        tvTargetName.setText("Thêm " + user.getName() + " vào P" + currentApartment.getApartmentNumber());

        AlertDialog dialog = builder.setView(view).create();

        btnConfirm.setOnClickListener(v -> {
            String relationship = edtRelation.getText().toString().trim();
            boolean isHead = chkIsHead.isChecked();

            if (relationship.isEmpty()) {
                edtRelation.setError("Vui lòng nhập quan hệ");
                return;
            }

            // Gọi API với đầy đủ thông tin
            updateResidentApartment(user.getUserId(), currentApartment.getId(), relationship, isHead);
            dialog.dismiss();
        });

        dialog.show();
    }

    // 3. Xác nhận xóa người khỏi phòng
    private void confirmRemoveUser(ResidentItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Cảnh báo")
                .setMessage("Bạn muốn xóa " + item.getName() + " khỏi phòng này?")
                .setPositiveButton("Đồng ý", (dialog, which) -> {
                    // Gọi API xóa: apartmentId = null, relationship = null, isHead = false
                    updateResidentApartment(item.getUserId(), null, null, false);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // 4. Gọi API Backend để cập nhật phòng cho cư dân
    private void updateResidentApartment(int userId, Integer apartmentId, String relationship, boolean isHead) {
        String url = ApiConfig.BASE_URL + "/api/residents/assign-apartment";
        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);

            if (apartmentId == null) {
                // TRƯỜNG HỢP XÓA (Đuổi ra)
                body.put("apartment_id", JSONObject.NULL);
            } else {
                // TRƯỜNG HỢP THÊM (Có quan hệ)
                body.put("apartment_id", apartmentId);
                body.put("relationship", relationship);
                body.put("is_head", isHead);
            }

        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, url, body,
                response -> {
                    String msg = (apartmentId == null) ? "Đã mời ra khỏi phòng" : "Đã thêm thành công!";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    loadAllResidents(); // Tải lại để cập nhật danh sách
                },
                error -> {
                    Toast.makeText(this, "Lỗi cập nhật: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    error.printStackTrace();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if(token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }
}