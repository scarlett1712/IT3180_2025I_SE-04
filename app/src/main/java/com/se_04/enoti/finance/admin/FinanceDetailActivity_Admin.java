package com.se_04.enoti.finance.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FinanceDetailActivity_Admin extends BaseActivity {

    private TextView txtFinanceTitle, txtFinanceDeadline;
    private LinearLayout layoutRoomCheckboxes;
    private Button buttonSaveChanges;

    private RequestQueue requestQueue;
    private int financeId;
    private int currentUserId;
    private String currentTitle, currentDueDate; // Lưu lại để dùng khi Edit
    private double currentAmount = 0; // Lưu số tiền nếu cần hiển thị

    // 🔥 Biến quyết định quyền sửa (Mặc định là FALSE)
    private boolean canEdit = false;

    private final Map<String, List<Integer>> roomToUsersMap = new HashMap<>();
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
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết khoản thu");
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        requestQueue = Volley.newRequestQueue(this);

        financeId = getIntent().getIntExtra("finance_id", -1);
        currentTitle = getIntent().getStringExtra("title");
        currentDueDate = getIntent().getStringExtra("due_date");
        // Nếu có truyền amount qua Intent thì lấy, không thì mặc định
        currentAmount = getIntent().getDoubleExtra("amount", 0);

        UserItem currentUser = UserManager.getInstance(this).getCurrentUser();
        if (currentUser != null) {
            currentUserId = Integer.parseInt(currentUser.getId());

            boolean isAdmin = UserManager.getInstance(this).isAdmin();
            boolean isAccountant = (currentUser.getRole() == Role.ACCOUNTANT);

            if (isAdmin) {
                canEdit = false;
            } else if (isAccountant) {
                canEdit = true;
            } else {
                canEdit = false;
            }
        }

        // Ẩn/Hiện nút Lưu trạng thái
        if (canEdit) {
            buttonSaveChanges.setVisibility(View.VISIBLE);
            buttonSaveChanges.setOnClickListener(v -> updateRoomStatuses());
        } else {
            buttonSaveChanges.setVisibility(View.GONE);
        }

        updateUIHeader(); // Hiển thị Title và Date

        if (financeId == -1) {
            Toast.makeText(this, "Thiếu ID khoản thu!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadRoomStatuses();
    }

    private void updateUIHeader() {
        txtFinanceTitle.setText(currentTitle != null ? currentTitle : "Khoản thu");
        txtFinanceDeadline.setText("Hạn nộp: " + (currentDueDate != null ? currentDueDate : "Không rõ"));
    }

    // 🔥 TẠO MENU (3 CHẤM)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Chỉ inflate menu nếu có quyền chỉnh sửa (Kế toán)
        if (canEdit) {
            getMenuInflater().inflate(R.menu.menu_finance_detail, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    // 🔥 XỬ LÝ CLICK MENU
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        else if (id == R.id.action_edit) {
            showEditDialog(); // Hiện hộp thoại sửa
            return true;
        }
        else if (id == R.id.action_delete) {
            showDeleteConfirmation(); // Hiện hộp thoại xác nhận xóa
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // --- LOGIC XÓA ---
    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa khoản thu này không? Dữ liệu thanh toán của cư dân cũng sẽ bị xóa.")
                .setPositiveButton("Xóa", (dialog, which) -> deleteFinance())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteFinance() {
        String url = ApiConfig.BASE_URL + "/api/finance/" + financeId;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.DELETE, url, null,
                response -> {
                    Toast.makeText(this, "Đã xóa khoản thu!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK); // Báo về màn hình trước để reload
                    finish();
                },
                error -> Toast.makeText(this, "Lỗi khi xóa khoản thu", Toast.LENGTH_SHORT).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if(token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        requestQueue.add(request);
    }

    // --- LOGIC SỬA (HIỆN DIALOG) ---
    private void showEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chỉnh sửa khoản thu");

        // Tạo layout cho dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputTitle = new EditText(this);
        inputTitle.setHint("Tiêu đề");
        inputTitle.setText(currentTitle);
        layout.addView(inputTitle);

        final EditText inputDate = new EditText(this);
        inputDate.setHint("Hạn nộp (DD-MM-YYYY)");
        inputDate.setText(currentDueDate);
        layout.addView(inputDate);

        final EditText inputAmount = new EditText(this);
        inputAmount.setHint("Số tiền (VNĐ)");
        inputAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        // Nếu có số tiền thì set, không thì để trống hoặc 0
        if (currentAmount > 0) inputAmount.setText(String.format("%.0f", currentAmount));
        layout.addView(inputAmount);

        builder.setView(layout);

        builder.setPositiveButton("Lưu", (dialog, which) -> {
            String newTitle = inputTitle.getText().toString().trim();
            String newDate = inputDate.getText().toString().trim();
            String amountStr = inputAmount.getText().toString().trim();

            if (newTitle.isEmpty() || amountStr.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }
            updateFinanceInfo(newTitle, newDate, Double.parseDouble(amountStr));
        });
        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void updateFinanceInfo(String title, String date, double amount) {
        String url = ApiConfig.BASE_URL + "/api/finance/" + financeId;
        JSONObject body = new JSONObject();
        try {
            body.put("title", title);
            body.put("amount", amount);
            body.put("due_date", date);
            body.put("content", "Đã chỉnh sửa bởi Kế toán"); // Có thể cho nhập content nếu cần
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, url, body,
                response -> {
                    Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();
                    // Cập nhật lại UI ngay lập tức
                    currentTitle = title;
                    currentDueDate = date;
                    currentAmount = amount;
                    updateUIHeader();
                    setResult(RESULT_OK);
                },
                error -> Toast.makeText(this, "Lỗi cập nhật", Toast.LENGTH_SHORT).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if(token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        requestQueue.add(request);
    }

    // --- CÁC HÀM CŨ (LOAD ROOM, CHECKBOX...) GIỮ NGUYÊN ---

    private void loadRoomStatuses() {
        String url = ApiConfig.BASE_URL + "/api/finance/" + financeId + "/users";
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

                            if (!roomToUsersMap.containsKey(room)) roomToUsersMap.put(room, new ArrayList<>());
                            roomToUsersMap.get(room).add(userId);
                            if (!roomInitialStatusMap.containsKey(room)) roomInitialStatusMap.put(room, isPaid);
                        }
                        createCheckboxesForRooms();
                    } catch (JSONException e) { e.printStackTrace(); }
                },
                error -> Toast.makeText(this, "Lỗi tải danh sách phòng", Toast.LENGTH_SHORT).show());
        requestQueue.add(request);
    }

    private void createCheckboxesForRooms() {
        layoutRoomCheckboxes.removeAllViews();
        List<String> sortedRooms = new ArrayList<>(roomToUsersMap.keySet());
        java.util.Collections.sort(sortedRooms);

        for (String room : sortedRooms) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText("Phòng " + room);
            boolean isPaid = roomInitialStatusMap.getOrDefault(room, false);
            checkBox.setChecked(isPaid);
            checkBox.setTag(room);

            if (canEdit) {
                checkBox.setEnabled(true);
                checkBox.setClickable(true);
                checkBox.setAlpha(1.0f);
            } else {
                checkBox.setEnabled(false);
                checkBox.setClickable(false);
                checkBox.setFocusable(false);
                checkBox.setTextColor(ContextCompat.getColor(this, R.color.black));
                checkBox.setAlpha(0.6f);
            }
            layoutRoomCheckboxes.addView(checkBox);
        }
    }

    private void updateRoomStatuses() {
        if (!canEdit) return;
        int updatedCount = 0;
        for (int i = 0; i < layoutRoomCheckboxes.getChildCount(); i++) {
            View view = layoutRoomCheckboxes.getChildAt(i);
            if (view instanceof CheckBox) {
                CheckBox cb = (CheckBox) view;
                String roomName = (String) cb.getTag();
                boolean isChecked = cb.isChecked();
                boolean initialStatus = roomInitialStatusMap.getOrDefault(roomName, false);
                if (isChecked != initialStatus) {
                    updateStatusForRoom(roomName, isChecked);
                    updatedCount++;
                }
            }
        }
        if (updatedCount > 0) Toast.makeText(this, "Đang cập nhật...", Toast.LENGTH_SHORT).show();
        else Toast.makeText(this, "Không có thay đổi.", Toast.LENGTH_SHORT).show();
    }

    private void updateStatusForRoom(String roomName, boolean isPaid) {
        String url = ApiConfig.BASE_URL + "/api/finance/update-status";
        JSONObject body = new JSONObject();
        try {
            body.put("room", roomName);
            body.put("finance_id", financeId);
            body.put("admin_id", currentUserId);
            body.put("status", isPaid ? "da_thanh_toan" : "chua_thanh_toan");
        } catch (JSONException e) { return; }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, url, body,
                response -> Log.i("FinanceDetail", "Updated room: " + roomName),
                error -> Toast.makeText(this, "Lỗi cập nhật phòng " + roomName, Toast.LENGTH_SHORT).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if(token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        requestQueue.add(request);
    }
}