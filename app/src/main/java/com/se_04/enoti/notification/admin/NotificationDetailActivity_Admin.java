package com.se_04.enoti.notification.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.feedback.admin.FeedbackAdapter_Admin;
import com.se_04.enoti.feedback.admin.FeedbackItem_Admin;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class NotificationDetailActivity_Admin extends BaseActivity {

    private TextView txtTitle, txtDate, txtSender, txtContent, txtFeedbackCount;
    private RecyclerView recyclerFeedback;
    private LinearLayout layoutEmpty;
    private FeedbackAdapter_Admin adapter;
    private final List<FeedbackItem_Admin> feedbackList = new ArrayList<>();
    private RequestQueue requestQueue;

    // Biến lưu dữ liệu hiện tại để dùng cho chức năng Edit/Delete
    private long currentNotificationId;
    private String currentTitle;
    private String currentContent;
    private String currentType;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_detail_admin);

        // --- Toolbar setup ---
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết thông báo");
        }

        // --- Khởi tạo view ---
        txtTitle = findViewById(R.id.txtDetailTitle);
        txtDate = findViewById(R.id.txtDetailDate);
        txtSender = findViewById(R.id.txtDetailSender);
        txtContent = findViewById(R.id.txtDetailContent);
        txtFeedbackCount = findViewById(R.id.txtFeedbackCount);
        recyclerFeedback = findViewById(R.id.recyclerFeedback);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        recyclerFeedback.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FeedbackAdapter_Admin(feedbackList);
        recyclerFeedback.setAdapter(adapter);

        requestQueue = Volley.newRequestQueue(this);

        // --- Nhận dữ liệu từ Intent ---
        currentNotificationId = getIntent().getLongExtra("notification_id", -1);
        currentTitle = getIntent().getStringExtra("title");
        currentContent = getIntent().getStringExtra("content");
        currentType = getIntent().getStringExtra("type");
        String sender = getIntent().getStringExtra("sender");
        String expiredDate = getIntent().getStringExtra("expired_date");

        // Set dữ liệu lên View
        txtTitle.setText(currentTitle != null ? currentTitle : "Không rõ tiêu đề");
        txtContent.setText(currentContent != null ? currentContent : "(Không có nội dung)");
        txtSender.setText(getString(R.string.notification_sender, sender != null ? sender : "Ban quản lý"));
        txtDate.setText("Hạn phản hồi: " + (expiredDate != null ? expiredDate : "Không rõ"));

        // --- Gọi API feedback ---
        if (currentNotificationId != -1) {
            fetchFeedbackList((int) currentNotificationId);
        } else {
            Toast.makeText(this, "Lỗi: Không tìm thấy ID thông báo", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * 🔹 Lấy danh sách feedback của thông báo
     */
    private void fetchFeedbackList(int notificationId) {
        String url = ApiConfig.BASE_URL + "/api/feedback/notification/" + notificationId;

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    feedbackList.clear();
                    parseFeedbackList(response);
                    adapter.notifyDataSetChanged();

                    if (feedbackList.isEmpty()) {
                        layoutEmpty.setVisibility(View.VISIBLE);
                        txtFeedbackCount.setText("Chưa có phản hồi nào từ cư dân");
                    } else {
                        layoutEmpty.setVisibility(View.GONE);
                        txtFeedbackCount.setText("Có " + feedbackList.size() + " phản hồi từ cư dân");
                    }
                },
                error -> {
                    Log.e("AdminNotifDetail", "Error fetching feedbacks", error);
                    // Không hiện toast lỗi nếu chỉ là không có feedback (404)
                    if (error.networkResponse != null && error.networkResponse.statusCode != 404) {
                        Toast.makeText(this, "Lỗi tải phản hồi", Toast.LENGTH_SHORT).show();
                    }
                });

        requestQueue.add(request);
    }

    private void parseFeedbackList(JSONArray response) {
        for (int i = 0; i < response.length(); i++) {
            try {
                JSONObject obj = response.getJSONObject(i);
                FeedbackItem_Admin item = new FeedbackItem_Admin();
                item.setId(obj.optInt("feedback_id", -1));
                item.setTitle(obj.optString("content", "(Không có nội dung)"));
                item.setDate(obj.optString("created_at", ""));
                item.setSender(obj.optString("full_name", "Cư dân"));
                feedbackList.add(item);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    // 🔥 1. TẠO MENU (Sửa / Xóa)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notification_admin, menu); // Thay tên menu của bạn nếu khác

        // --- THÊM ĐOẠN NÀY ĐỂ ÉP MÀU CHỮ THÀNH ĐEN ---
        for (int i = 0; i < menu.size(); i++) {
            android.view.MenuItem item = menu.getItem(i);

            // Lấy tiêu đề hiện tại
            CharSequence title = item.getTitle();
            if (title != null) {
                // Tạo một chuỗi Spannable để gắn màu
                android.text.SpannableString spanString = new android.text.SpannableString(title);

                // Gán màu ĐEN (Color.BLACK) cho toàn bộ chuỗi
                spanString.setSpan(new android.text.style.ForegroundColorSpan(android.graphics.Color.BLACK), 0, spanString.length(), 0);

                // Set lại tiêu đề mới đã có màu
                item.setTitle(spanString);
            }
        }
        // ----------------------------------------------

        return true;
    }

    // 🔥 2. XỬ LÝ SỰ KIỆN MENU
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_edit) {
            openEditScreen(); // Mở màn hình sửa
            return true;
        } else if (id == R.id.action_delete) {
            confirmDelete(); // Xác nhận xóa
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // --- LOGIC XÓA ---
    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa thông báo này không? Mọi dữ liệu liên quan sẽ bị mất.")
                .setPositiveButton("Xóa", (dialog, which) -> deleteNotification())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteNotification() {
        String url = ApiConfig.BASE_URL + "/api/notification/delete/" + currentNotificationId;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.DELETE, url, null,
                response -> {
                    Toast.makeText(this, "Đã xóa thông báo thành công!", Toast.LENGTH_SHORT).show();
                    // Gửi broadcast để refresh list ở màn hình trước
                    Intent intent = new Intent(CreateNotificationActivity.ACTION_NOTIFICATION_CREATED);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                    finish(); // Đóng màn hình
                },
                error -> {
                    Toast.makeText(this, "Lỗi khi xóa thông báo", Toast.LENGTH_SHORT).show();
                    Log.e("DeleteNotif", "Error: " + error.toString());
                }
        );
        requestQueue.add(request);
    }

    // --- LOGIC SỬA ---
    private void openEditScreen() {
        Intent intent = new Intent(this, CreateNotificationActivity.class);
        intent.putExtra("IS_EDIT_MODE", true); // Cờ hiệu chế độ sửa
        intent.putExtra("notification_id", currentNotificationId);
        intent.putExtra("title", currentTitle);
        intent.putExtra("content", currentContent);
        intent.putExtra("type", currentType);

        startActivity(intent);
        finish(); // Đóng màn hình này để khi Lưu xong sẽ quay về danh sách
    }
}