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

    // Map: Phòng -> Danh sách UserID
    private final Map<String, List<Integer>> roomToUsersMap = new HashMap<>();
    // Map: Phòng -> Đã thanh toán hay chưa (ban đầu)
    private final Map<String, Boolean> roomInitialStatusMap = new HashMap<>();
    // 🔥 MAP MỚI: Lưu UserID đại diện đã thanh toán cho phòng đó (để lấy hóa đơn)
    private final Map<String, Integer> roomPaidPayerMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_detail_admin);

        // ... (Giữ nguyên phần khởi tạo view và toolbar) ...
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
        currentAmount = getIntent().getDoubleExtra("amount", 0);

        UserItem currentUser = UserManager.getInstance(this).getCurrentUser();
        if (currentUser != null) {
            currentUserId = Integer.parseInt(currentUser.getId());
            boolean isAdmin = UserManager.getInstance(this).isAdmin();
            boolean isAccountant = (currentUser.getRole() == Role.ACCOUNTANT);

            // Kế toán có quyền sửa, Admin chỉ xem
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

        loadRoomStatuses();
    }

    // ... (Giữ nguyên các hàm onCreateOptionsMenu, showEditDialog, deleteFinance...) ...

    private void updateUIHeader() {
        txtFinanceTitle.setText(currentTitle != null ? currentTitle : "Khoản thu");
        txtFinanceDeadline.setText("Hạn nộp: " + (currentDueDate != null ? currentDueDate : "Không rõ"));
    }

    // -------------------------------------------------------------
    // 🔥 SỬA LOGIC LOAD PHÒNG ĐỂ LẤY ID NGƯỜI ĐÃ THANH TOÁN
    // -------------------------------------------------------------
    private void loadRoomStatuses() {
        String url = ApiConfig.BASE_URL + "/api/finance/" + financeId + "/users";
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        roomToUsersMap.clear();
                        roomInitialStatusMap.clear();
                        roomPaidPayerMap.clear(); // Reset map

                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            int userId = obj.optInt("user_id");
                            String room = obj.optString("room", "N/A");
                            String status = obj.optString("status", "chua_thanh_toan");
                            boolean isPaid = status.equalsIgnoreCase("da_thanh_toan");

                            if (!roomToUsersMap.containsKey(room)) roomToUsersMap.put(room, new ArrayList<>());
                            roomToUsersMap.get(room).add(userId);

                            if (!roomInitialStatusMap.containsKey(room)) roomInitialStatusMap.put(room, isPaid);

                            // 🔥 Nếu đã thanh toán, lưu lại userId này để dùng gọi API hóa đơn
                            if (isPaid) {
                                roomPaidPayerMap.put(room, userId);
                            }
                        }
                        createCheckboxesForRooms();
                    } catch (JSONException e) { e.printStackTrace(); }
                },
                error -> Toast.makeText(this, "Lỗi tải danh sách phòng", Toast.LENGTH_SHORT).show());
        requestQueue.add(request);
    }

    // -------------------------------------------------------------
    // 🔥 SỬA LOGIC HIỂN THỊ CHECKBOX VÀ SỰ KIỆN CLICK
    // -------------------------------------------------------------
    private void createCheckboxesForRooms() {
        layoutRoomCheckboxes.removeAllViews();
        List<String> sortedRooms = new ArrayList<>(roomToUsersMap.keySet());
        java.util.Collections.sort(sortedRooms, new java.util.Comparator<String>() {
            @Override
            public int compare(String room1, String room2) {
                try {
                    // Tách tầng và số phòng cho room1
                    int floor1 = Integer.parseInt(room1.substring(0, room1.length() - 2));
                    int number1 = Integer.parseInt(room1.substring(room1.length() - 2));

                    // Tách tầng và số phòng cho room2
                    int floor2 = Integer.parseInt(room2.substring(0, room2.length() - 2));
                    int number2 = Integer.parseInt(room2.substring(room2.length() - 2));

                    // So sánh tầng trước
                    int floorCompare = Integer.compare(floor1, floor2);
                    if (floorCompare != 0) {
                        return floorCompare; // Nếu tầng khác nhau, trả về kết quả so sánh tầng
                    }

                    // Nếu tầng giống nhau, so sánh số phòng
                    return Integer.compare(number1, number2);

                } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                    // Nếu có lỗi (ví dụ: tên phòng không đúng định dạng), dùng cách so sánh chuỗi mặc định
                    Log.e("SortRooms", "Lỗi khi phân tích tên phòng: " + room1 + " hoặc " + room2, e);
                    return room1.compareTo(room2);
                }
            }
        });


        if (canEdit) {
            Toast.makeText(this, "Giữ lì (Long Press) vào phòng đã thanh toán để xem hóa đơn", Toast.LENGTH_LONG).show();
        }

        for (String room : sortedRooms) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText("Phòng " + room);
            boolean isPaid = roomInitialStatusMap.getOrDefault(room, false);
            checkBox.setChecked(isPaid);
            checkBox.setTag(room);

            // LOGIC QUYỀN HẠN & CLICK
            if (canEdit) {
                // KẾ TOÁN:
                // - Click thường: Đánh dấu check/uncheck để cập nhật trạng thái
                // - Giữ lì (Long Click): Xem hóa đơn (nếu đã thanh toán)
                checkBox.setEnabled(true);

                checkBox.setOnLongClickListener(v -> {
                    if (checkBox.isChecked()) {
                        showInvoiceBottomSheet(room);
                        return true; // Đã xử lý sự kiện
                    }
                    return false;
                });

            } else {
                // ADMIN (Chỉ xem):
                // - Không click được checkbox để đổi trạng thái
                // - Click vào để xem hóa đơn (nếu đã thanh toán)
                checkBox.setClickable(true); // Vẫn cho click nhưng logic khác
                checkBox.setFocusable(false); // Không focus nhập liệu

                // Override sự kiện click để không đổi trạng thái check mà mở hóa đơn
                checkBox.setOnClickListener(v -> {
                    // Reset lại trạng thái cũ (không cho đổi)
                    checkBox.setChecked(isPaid);

                    if (isPaid) {
                        showInvoiceBottomSheet(room);
                    } else {
                        Toast.makeText(this, "Phòng này chưa thanh toán", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Đổi màu nếu đã thanh toán để dễ nhìn
            if (isPaid) {
                checkBox.setTextColor(ContextCompat.getColor(this, R.color.holo_green_dark));
                checkBox.setTypeface(null, android.graphics.Typeface.BOLD);
            }

            layoutRoomCheckboxes.addView(checkBox);
        }
    }

    // 🔥 HÀM MỞ BOTTOM SHEET
    private void showInvoiceBottomSheet(String room) {
        Integer userId = roomPaidPayerMap.get(room);
        if (userId == null) {
            Toast.makeText(this, "Không tìm thấy thông tin người thanh toán", Toast.LENGTH_SHORT).show();
            return;
        }

        InvoiceBottomSheet bottomSheet = InvoiceBottomSheet.newInstance(financeId, userId);
        bottomSheet.show(getSupportFragmentManager(), "InvoiceBottomSheet");
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
        if (updatedCount > 0) {
            Toast.makeText(this, "Đang cập nhật...", Toast.LENGTH_SHORT).show();
            // Reload lại để cập nhật map ID người thanh toán mới
            new android.os.Handler().postDelayed(this::loadRoomStatuses, 1000);
        }
        else Toast.makeText(this, "Không có thay đổi.", Toast.LENGTH_SHORT).show();
    }

    // ... (Các hàm updateStatusForRoom, showEditDialog giữ nguyên) ...
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

    // ... (Các hàm menu giữ nguyên) ...
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
            onBackPressed();
            return true;
        } else if (id == R.id.action_edit) {
            showEditDialog();
            return true;
        } else if (id == R.id.action_delete) {
            showDeleteConfirmation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Giữ nguyên các hàm showDeleteConfirmation, deleteFinance, showEditDialog, updateFinanceInfo từ code gốc của bạn
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

        if (currentAmount > 0) {
            inputAmount.setText(String.format("%.0f", currentAmount));
        }
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

            Double finalAmount = null;
            if (!amountStr.isEmpty()) {
                try {
                    finalAmount = Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    finalAmount = null;
                }
            }

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

            if (amount != null) {
                body.put("amount", amount);
            } else {
                body.put("amount", JSONObject.NULL);
            }

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