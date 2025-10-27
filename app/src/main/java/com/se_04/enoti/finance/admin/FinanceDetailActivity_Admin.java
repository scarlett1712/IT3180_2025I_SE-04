package com.se_04.enoti.finance.admin;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FinanceDetailActivity_Admin extends AppCompatActivity {

    private TextView txtFinanceTitle, txtFinanceDeadline;
    private LinearLayout layoutRoomCheckboxes;
    private Button buttonSaveChanges;

    private RequestQueue requestQueue;
    private int financeId;
    private int adminId;

    // Map để nhóm người dùng theo phòng. Key: Tên phòng, Value: Danh sách ID người dùng
    private final Map<String, List<Integer>> roomToUsersMap = new HashMap<>();

    // Map để lưu trạng thái thanh toán ban đầu của mỗi phòng
    private final Map<String, Boolean> roomInitialStatusMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_detail_admin);

        txtFinanceTitle = findViewById(R.id.txtFinanceTitle);
        txtFinanceDeadline = findViewById(R.id.txtFinanceDeadline);
        layoutRoomCheckboxes = findViewById(R.id.layoutRoomCheckboxes);
        buttonSaveChanges = findViewById(R.id.buttonSaveChanges);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // --- Toolbar Setup ---
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.finance_detail_title);
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        requestQueue = Volley.newRequestQueue(this);

        financeId = getIntent().getIntExtra("finance_id", -1);
        String title = getIntent().getStringExtra("title");
        String dueDate = getIntent().getStringExtra("due_date");
        adminId = Integer.parseInt(UserManager.getInstance(this).getID());

        txtFinanceTitle.setText(title != null ? title : "Khoản thu");
        txtFinanceDeadline.setText("Hạn nộp: " + (dueDate != null ? dueDate : "Không rõ"));

        if (financeId == -1) {
            Toast.makeText(this, "Thiếu ID khoản thu!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadRoomStatuses();
        buttonSaveChanges.setOnClickListener(v -> updateRoomStatuses());
    }

    private void loadRoomStatuses() {
        String url = ApiConfig.BASE_URL + "/api/finance/" + financeId + "/users";
        Log.d("FinanceDetailAdmin", "GET " + url);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        roomToUsersMap.clear();
                        roomInitialStatusMap.clear();

                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            int userId = obj.optInt("user_id");
                            String room = obj.optString("room", "N/A");
                            String status = obj.optString("status", "chua_thanh_toan");
                            boolean isPaid = status.equalsIgnoreCase("da_thanh_toan");

                            if (!roomToUsersMap.containsKey(room)) {
                                roomToUsersMap.put(room, new ArrayList<>());
                            }
                            roomToUsersMap.get(room).add(userId);

                            if (!roomInitialStatusMap.containsKey(room)) {
                                roomInitialStatusMap.put(room, isPaid);
                            }
                        }
                        createCheckboxesForRooms();
                    } catch (JSONException e) {
                        Log.e("FinanceDetailAdmin", "JSON parse error", e);
                        Toast.makeText(this, "Lỗi dữ liệu từ server", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    Log.e("FinanceDetailAdmin", "Network error: " + error.toString());
                    Toast.makeText(this, "Không thể tải danh sách phòng", Toast.LENGTH_SHORT).show();
                });

        requestQueue.add(request);
    }

    private void createCheckboxesForRooms() {
        layoutRoomCheckboxes.removeAllViews();
        // Sắp xếp tên phòng để hiển thị có thứ tự
        List<String> sortedRooms = new ArrayList<>(roomToUsersMap.keySet());
        java.util.Collections.sort(sortedRooms);

        for (String room : sortedRooms) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText("Phòng " + room);
            boolean isPaid = roomInitialStatusMap.getOrDefault(room, false);
            checkBox.setChecked(isPaid);
            checkBox.setTag(room); // Tag của CheckBox là tên phòng
            layoutRoomCheckboxes.addView(checkBox);
        }
    }

    private void updateRoomStatuses() {
        int updatedCount = 0;
        for (int i = 0; i < layoutRoomCheckboxes.getChildCount(); i++) {
            View view = layoutRoomCheckboxes.getChildAt(i);
            if (view instanceof CheckBox) {
                CheckBox cb = (CheckBox) view;
                String roomName = (String) cb.getTag(); // Lấy tên phòng từ tag
                boolean isChecked = cb.isChecked();

                boolean initialStatus = roomInitialStatusMap.getOrDefault(roomName, false);
                if (isChecked != initialStatus) {
                    // 🔥 THAY ĐỔI QUAN TRỌNG: Gọi hàm cập nhật theo tên phòng
                    updateStatusForRoom(roomName, isChecked);
                    updatedCount++;
                }
            }
        }

        if (updatedCount > 0) {
            Toast.makeText(this, "Đã gửi yêu cầu cập nhật cho " + updatedCount + " phòng.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Không có thay đổi nào để lưu.", Toast.LENGTH_SHORT).show();
        }
    }

    // 🔥 THAY ĐỔI QUAN TRỌNG: Hàm này giờ gửi yêu cầu tới API /update-status hiện có
    private void updateStatusForRoom(String roomName, boolean isPaid) {
        // Sử dụng API /update-status đã có trong file finance.js
        String url = ApiConfig.BASE_URL + "/api/finance/update-status";
        JSONObject body = new JSONObject();

        try {
            // API của bạn cần "room", "finance_id", "admin_id", và "status"
            body.put("room", roomName);
            body.put("finance_id", financeId);
            body.put("admin_id", adminId);
            body.put("status", isPaid ? "da_thanh_toan" : "chua_thanh_toan");
        } catch (JSONException e) {
            Log.e("FinanceDetailAdmin", "JSON build error", e);
            return;
        }

        Log.d("FinanceDetailAdmin", "Updating status for room: " + body.toString());

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, url, body,
                response -> Log.i("FinanceDetailAdmin", "Successfully updated status for room: " + roomName),
                error -> {
                    Log.e("FinanceDetailAdmin", "Error updating status for room " + roomName + ": " + error.toString());
                    Toast.makeText(this, "Lỗi khi cập nhật phòng " + roomName, Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        requestQueue.add(request);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Kiểm tra xem item được nhấn có phải là nút "home" (mũi tên quay lại) không
        if (item.getItemId() == android.R.id.home) {
            // Thực hiện hành động quay lại màn hình trước đó
            onBackPressed(); // Hoặc finish();
            return true; // Báo hiệu rằng sự kiện đã được xử lý
        }
        return super.onOptionsItemSelected(item);
    }
}