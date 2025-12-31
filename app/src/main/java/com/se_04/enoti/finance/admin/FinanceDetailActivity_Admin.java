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

    // Map: Phòng -> ID người đại diện (để xem hóa đơn hoặc để cập nhật)
    private final Map<String, Integer> roomRepresentativeIdMap = new HashMap<>();

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
                        roomRepresentativeIdMap.clear();

                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            String room = obj.optString("room", "N/A");
                            String status = obj.optString("status", "chua_thanh_toan");
                            int userId = obj.optInt("user_id");

                            boolean isPaid = "da_thanh_toan".equalsIgnoreCase(status);

                            // Chỉ cần 1 người trong phòng đóng tiền = phòng đã đóng tiền
                            if (!roomStatusMap.containsKey(room) || isPaid) {
                                roomStatusMap.put(room, isPaid);
                            }

                            // Luôn lưu user_id đầu tiên của mỗi phòng làm đại diện để gửi API
                            if (!roomRepresentativeIdMap.containsKey(room)) {
                                roomRepresentativeIdMap.put(room, userId);
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
            int num1 = extractRoomNumber(room1);
            int num2 = extractRoomNumber(room2);
            return Integer.compare(num1, num2);
        });

        for (String room : sortedRooms) {
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

            checkBox.setOnLongClickListener(v -> {
                if (checkBox.isChecked()) {
                    showInvoice(finalRoom);
                    return true;
                } else {
                    Toast.makeText(this, "Phòng này chưa thanh toán", Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

            if (!canEdit) {
                 checkBox.setOnClickListener(v -> {
                    checkBox.setChecked(isPaid); // Reset to original state
                    Toast.makeText(this, "Bạn không có quyền chỉnh sửa", Toast.LENGTH_SHORT).show();
                });
            }

            layoutRoomCheckboxes.addView(checkBox);
            roomCheckBoxViews.put(finalRoom, checkBox);
        }
    }

    private void showInvoice(String room) {
        Integer userId = roomRepresentativeIdMap.get(room);
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
        // Danh sách để theo dõi các phòng đang được xử lý, tránh gửi request thừa
        for (Map.Entry<String, CheckBox> entry : roomCheckBoxViews.entrySet()) {
            String roomName = entry.getKey();
            CheckBox cb = entry.getValue();

            boolean currentChecked = cb.isChecked();
            boolean initialStatus = roomStatusMap.getOrDefault(roomName, false);

            // Chỉ gửi request nếu trạng thái CheckBox khác với trạng thái ban đầu từ server
            if (currentChecked != initialStatus) {
                Integer representativeId = roomRepresentativeIdMap.get(roomName);
                if (representativeId != null) {
                    // Truyền thêm roomName vào API nếu Backend của bạn hỗ trợ query theo room
                    callApiUpdateStatus(representativeId, roomName, currentChecked);
                    updatedCount++;
                } else {
                    Log.e("FinanceDetailAdmin", "Không tìm thấy ID đại diện cho phòng: " + roomName);
                }
            }
        }

        if (updatedCount > 0) {
            Toast.makeText(this, "Đang cập nhật " + updatedCount + " phòng...", Toast.LENGTH_SHORT).show();
            // Tăng thời gian delay một chút để Server kịp xử lý DB trước khi load lại
            new android.os.Handler().postDelayed(this::loadRoomData, 2000);
        } else {
            Toast.makeText(this, "Không có thay đổi nào.", Toast.LENGTH_SHORT).show();
        }
    }

    // Cập nhật lại hàm gọi API để nhận thêm tham số Room (giúp log chính xác hơn)
    private void callApiUpdateStatus(int userId, String roomName, boolean isPaid) {
        String url = ApiConfig.BASE_URL + "/api/finance/update-status";
        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);
            body.put("room", roomName); // Gửi thêm room để Backend dễ xử lý đồng bộ
            body.put("finance_id", financeId);
            body.put("status", isPaid ? "da_thanh_toan" : "chua_thanh_toan");
        } catch (JSONException e) { return; }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, url, body,
                response -> Log.i("FinanceDetail", "Cập nhật thành công phòng: " + roomName),
                error -> {
                    Log.e("FinanceDetail", "Lỗi cập nhật phòng: " + roomName + " | Error: " + error.toString());
                    // Không nên hiện Toast ở đây nếu update hàng loạt vì sẽ gây spam Toast
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

    private int extractRoomNumber(String room) {
        try {
            String numbers = room.replaceAll("\\D+", "");
            return numbers.isEmpty() ? 0 : Integer.parseInt(numbers);
        } catch (Exception e) {
            return 0;
        }
    }
}