package com.se_04.enoti.finance.admin;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
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
    private final List<RoomStatus> roomStatusList = new ArrayList<>();

    // Dữ liệu model đơn giản
    private static class RoomStatus {
        int userId;
        String room;
        boolean isPaid;

        RoomStatus(int userId, String room, boolean isPaid) {
            this.userId = userId;
            this.room = room;
            this.isPaid = isPaid;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_detail_admin);

        txtFinanceTitle = findViewById(R.id.txtFinanceTitle);
        txtFinanceDeadline = findViewById(R.id.txtFinanceDeadline);
        layoutRoomCheckboxes = findViewById(R.id.layoutRoomCheckboxes);
        buttonSaveChanges = findViewById(R.id.buttonSaveChanges);

        requestQueue = Volley.newRequestQueue(this);

        // 🧾 Nhận dữ liệu từ Intent
        financeId = getIntent().getIntExtra("finance_id", -1);
        String title = getIntent().getStringExtra("title");
        String dueDate = getIntent().getStringExtra("due_date");

        adminId = Integer.parseInt(UserManager.getInstance(this).getID()); // Lấy id admin đang đăng nhập

        txtFinanceTitle.setText(title != null ? title : "Khoản thu");
        txtFinanceDeadline.setText("Hạn nộp: " + (dueDate != null ? dueDate : "Không rõ"));

        if (financeId == -1) {
            Toast.makeText(this, "Thiếu ID khoản thu!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadRoomStatuses();

        buttonSaveChanges.setOnClickListener(v -> updateStatuses());
    }

    // 🧩 Lấy danh sách phòng và trạng thái thanh toán
    private void loadRoomStatuses() {
        String url = ApiConfig.BASE_URL + "/api/finance/" + financeId + "/users";
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        layoutRoomCheckboxes.removeAllViews();
                        roomStatusList.clear();

                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            int userId = obj.optInt("user_id");
                            String room = obj.optString("room", "N/A");
                            String status = obj.optString("status", "chua_thanh_toan");

                            boolean isPaid = status.equalsIgnoreCase("da_thanh_toan");
                            roomStatusList.add(new RoomStatus(userId, room, isPaid));

                            CheckBox checkBox = new CheckBox(this);
                            checkBox.setText("Phòng " + room);
                            checkBox.setChecked(isPaid);
                            checkBox.setTag(userId);
                            layoutRoomCheckboxes.addView(checkBox);
                        }
                    } catch (JSONException e) {
                        Log.e("FinanceDetailAdmin", "JSON parse error", e);
                        Toast.makeText(this, "Lỗi dữ liệu từ server", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    Log.e("FinanceDetailAdmin", "Network error", error);
                    Toast.makeText(this, "Không thể tải danh sách phòng", Toast.LENGTH_SHORT).show();
                });

        requestQueue.add(request);
    }

    // 🟢 Cập nhật trạng thái đã thanh toán
    private void updateStatuses() {
        int totalChecked = 0;
        for (int i = 0; i < layoutRoomCheckboxes.getChildCount(); i++) {
            View view = layoutRoomCheckboxes.getChildAt(i);
            if (view instanceof CheckBox) {
                CheckBox cb = (CheckBox) view;
                boolean isChecked = cb.isChecked();
                int userId = (int) cb.getTag();
                totalChecked += (isChecked ? 1 : 0);

                updateSingleStatus(userId, isChecked);
            }
        }

        Toast.makeText(this, "Đã lưu thay đổi cho " + totalChecked + " phòng.", Toast.LENGTH_SHORT).show();
    }

    private void updateSingleStatus(int userId, boolean isPaid) {
        String url = ApiConfig.BASE_URL + "/api/finance/update-status";
        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);
            body.put("finance_id", financeId);
            body.put("admin_id", adminId);
            body.put("status", isPaid ? "da_thanh_toan" : "chua_thanh_toan");
        } catch (JSONException e) {
            Log.e("FinanceDetailAdmin", "JSON build error", e);
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, url, body,
                response -> Log.i("FinanceDetailAdmin", "Status updated for user " + userId),
                error -> Log.e("FinanceDetailAdmin", "Error updating status", error)
        );

        requestQueue.add(request);
    }
}
