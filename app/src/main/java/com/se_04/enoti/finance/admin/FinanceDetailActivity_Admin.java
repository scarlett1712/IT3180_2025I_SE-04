package com.se_04.enoti.finance.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
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
import java.util.Collections;
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
    private String currentTitle, currentDueDate;
    private double currentAmount = 0;

    private boolean canEdit = false;

    // Map: Phòng -> Trạng thái thanh toán (true/false)
    private final Map<String, Boolean> roomStatusMap = new HashMap<>();

    // Map: Phòng -> ID người đại diện (để xem hóa đơn)
    private final Map<String, Integer> roomInvoiceRefMap = new HashMap<>();

    // Map: Lưu trữ CheckBox View
    private final Map<String, CheckBox> roomCheckBoxViews = new HashMap<>();

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
        toolbar.setNavigationOnClickListener(v -> finish());

        requestQueue = Volley.newRequestQueue(this);

        financeId = getIntent().getIntExtra("finance_id", -1);
        currentTitle = getIntent().getStringExtra("title");
        currentDueDate = getIntent().getStringExtra("due_date");
        currentAmount = getIntent().getDoubleExtra("amount", 0);

        UserItem currentUser = UserManager.getInstance(this).getCurrentUser();
        if (currentUser != null) {
            currentUserId = Integer.parseInt(currentUser.getId());
            boolean isAccountant = (currentUser.getRole() == Role.ACCOUNTANT);

            // Chỉ Kế toán được sửa, Admin chỉ xem
            if (isAccountant) canEdit = true;
            else canEdit = false;
        }

        if (canEdit) {
            buttonSaveChanges.setVisibility(View.VISIBLE);
            buttonSaveChanges.setOnClickListener(v -> updateRoomStatuses());
        } else {
            buttonSaveChanges.setVisibility(View.GONE);
        }

        updateUIHeader();

        if (financeId == -1) {
            finish();
            return;
        }

        loadRoomData();
    }

    private void updateUIHeader() {
        txtFinanceTitle.setText(currentTitle != null ? currentTitle : "Khoản thu");
        txtFinanceDeadline.setText("Hạn nộp: " + (currentDueDate != null ? currentDueDate : "Không rõ"));
    }

    private void loadRoomData() {
        String url = ApiConfig.BASE_URL + "/api/finance/" + financeId + "/users";
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        roomStatusMap.clear();
                        roomInvoiceRefMap.clear();

                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            String room = obj.optString("room", "N/A");
                            String status = obj.optString("status", "chua_thanh_toan");
                            int userId = obj.optInt("user_id");

                            boolean isPaid = "da_thanh_toan".equalsIgnoreCase(status);

                            // Nếu phòng này chưa có trong map, hoặc nếu phòng này đã có nhưng trạng thái mới là 'paid' thì cập nhật
                            // (Mục đích: Chỉ cần 1 người trong phòng đóng tiền = phòng đã đóng tiền)
                            if (!roomStatusMap.containsKey(room)) {
                                roomStatusMap.put(room, isPaid);
                            } else {
                                if (isPaid) roomStatusMap.put(room, true);
                            }

                            // Lưu ID người thanh toán để xem hóa đơn
                            if (isPaid && !roomInvoiceRefMap.containsKey(room)) {
                                roomInvoiceRefMap.put(room, userId);
                            }
                        }
                        renderRoomList();
                    } catch (JSONException e) { e.printStackTrace(); }
                },
                error -> Toast.makeText(this, "Lỗi tải danh sách phòng", Toast.LENGTH_SHORT).show());
        requestQueue.add(request);
    }

    private void renderRoomList() {
        layoutRoomCheckboxes.removeAllViews();
        roomCheckBoxViews.clear();

        List<String> sortedRooms = new ArrayList<>(roomStatusMap.keySet());

        Collections.sort(sortedRooms, (room1, room2) -> {
            try {
                if (room1.length() < 3 || room2.length() < 3) return room1.compareTo(room2);
                int floor1 = Integer.parseInt(room1.substring(0, room1.length() - 2));
                int number1 = Integer.parseInt(room1.substring(room1.length() - 2));
                int floor2 = Integer.parseInt(room2.substring(0, room2.length() - 2));
                int number2 = Integer.parseInt(room2.substring(room2.length() - 2));

                int floorCompare = Integer.compare(floor1, floor2);
                return (floorCompare != 0) ? floorCompare : Integer.compare(number1, number2);
            } catch (Exception e) {
                return room1.compareTo(room2);
            }
        });

        for (String room : sortedRooms) {
            // 🔥 QUAN TRỌNG: Tạo biến final cục bộ để fix lỗi bấm nhầm phòng
            final String finalRoom = room;

            CheckBox checkBox = new CheckBox(this);
            checkBox.setText("Phòng " + finalRoom);

            boolean isPaid = roomStatusMap.getOrDefault(finalRoom, false);
            checkBox.setChecked(isPaid);
            checkBox.setTag(finalRoom);

            if (isPaid) {
                checkBox.setTextColor(ContextCompat.getColor(this, R.color.holo_green_dark));
                checkBox.setTypeface(null, android.graphics.Typeface.BOLD);
            }

            // --- SỰ KIỆN XEM HÓA ĐƠN ---
            checkBox.setOnLongClickListener(v -> {
                if (checkBox.isChecked()) {
                    showInvoice(finalRoom); // Dùng biến finalRoom thay vì room
                    return true;
                } else {
                    Toast.makeText(this, "Phòng này chưa thanh toán", Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

            // --- QUYỀN SỬA ---
            if (canEdit) {
                checkBox.setEnabled(true);
            } else {
                checkBox.setOnClickListener(v -> {
                    checkBox.setChecked(isPaid);
                    Toast.makeText(this, "Bạn không có quyền chỉnh sửa", Toast.LENGTH_SHORT).show();
                });
            }

            layoutRoomCheckboxes.addView(checkBox);
            roomCheckBoxViews.put(finalRoom, checkBox);
        }
    }

    private void showInvoice(String room) {
        Integer userId = roomInvoiceRefMap.get(room);
        if (userId == null) {
            Toast.makeText(this, "Không tìm thấy dữ liệu hóa đơn", Toast.LENGTH_SHORT).show();
            return;
        }
        InvoiceBottomSheet bottomSheet = InvoiceBottomSheet.newInstance(financeId, userId);
        bottomSheet.show(getSupportFragmentManager(), "InvoiceBottomSheet");
    }

    private void updateRoomStatuses() {
        if (!canEdit) return;

        int updatedCount = 0;

        for (Map.Entry<String, CheckBox> entry : roomCheckBoxViews.entrySet()) {
            String roomName = entry.getKey();
            CheckBox cb = entry.getValue();

            boolean currentChecked = cb.isChecked();
            boolean initialStatus = roomStatusMap.getOrDefault(roomName, false);

            // Chỉ update nếu có sự thay đổi
            if (currentChecked != initialStatus) {
                callApiUpdateStatus(roomName, currentChecked);
                updatedCount++;
            }
        }

        if (updatedCount > 0) {
            Toast.makeText(this, "Đang cập nhật " + updatedCount + " phòng...", Toast.LENGTH_SHORT).show();
            // Đợi server xử lý xong rồi load lại
            new android.os.Handler().postDelayed(this::loadRoomData, 1500);
        } else {
            Toast.makeText(this, "Không có thay đổi nào.", Toast.LENGTH_SHORT).show();
        }
    }

    private void callApiUpdateStatus(String roomName, boolean isPaid) {
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
                error -> Log.e("FinanceDetail", "Error updating room " + roomName)
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (canEdit) {
            getMenuInflater().inflate(R.menu.menu_finance_detail, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_edit) {
            if (canEdit) showEditDialog();
            return true;
        } else if (id == R.id.action_delete) {
            if (canEdit) showDeleteConfirmation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa khoản thu này không?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteFinance())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteFinance() {
        String url = ApiConfig.BASE_URL + "/api/finance/" + financeId;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.DELETE, url, null,
                response -> {
                    Toast.makeText(this, "Đã xóa khoản thu!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
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

    private void showEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chỉnh sửa khoản thu");

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
        if (currentAmount > 0) inputAmount.setText(String.format("%.0f", currentAmount));
        layout.addView(inputAmount);

        builder.setView(layout);

        builder.setPositiveButton("Lưu", (dialog, which) -> {
            String newTitle = inputTitle.getText().toString().trim();
            String newDate = inputDate.getText().toString().trim();
            String amountStr = inputAmount.getText().toString().trim();

            if (newTitle.isEmpty()) {
                Toast.makeText(this, "Tiêu đề không được để trống", Toast.LENGTH_SHORT).show();
                return;
            }
            Double finalAmount = amountStr.isEmpty() ? null : Double.parseDouble(amountStr);
            updateFinanceInfo(newTitle, newDate, finalAmount);
        });
        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void updateFinanceInfo(String title, String date, Double amount) {
        String url = ApiConfig.BASE_URL + "/api/finance/" + financeId;
        JSONObject body = new JSONObject();
        try {
            body.put("title", title);
            body.put("due_date", date);
            body.put("content", "Đã chỉnh sửa bởi Kế toán");
            if (amount != null) body.put("amount", amount);
            else body.put("amount", JSONObject.NULL);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, url, body,
                response -> {
                    Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();
                    currentTitle = title;
                    currentDueDate = date;
                    currentAmount = (amount != null) ? amount : 0;
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
}