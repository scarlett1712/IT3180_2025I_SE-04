package com.se_04.enoti.notification.admin;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide; // 🔥 Đã thêm Glide
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
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

    // 🔥 Views đính kèm
    private ImageView imgAttachment;
    private CardView cardAttachment;
    private MaterialButton btnViewFile;

    private long currentNotificationId;
    private String currentTitle, currentContent, currentType;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_detail_admin);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết thông báo");
        }

        txtTitle = findViewById(R.id.txtDetailTitle);
        txtDate = findViewById(R.id.txtDetailDate);
        txtSender = findViewById(R.id.txtDetailSender);
        txtContent = findViewById(R.id.txtDetailContent);
        txtFeedbackCount = findViewById(R.id.txtFeedbackCount);
        recyclerFeedback = findViewById(R.id.recyclerFeedback);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        // 🔥 Ánh xạ views mới
        imgAttachment = findViewById(R.id.imgAttachment);
        cardAttachment = findViewById(R.id.cardAttachment);
        btnViewFile = findViewById(R.id.btnViewFile);

        recyclerFeedback.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FeedbackAdapter_Admin(feedbackList);
        recyclerFeedback.setAdapter(adapter);

        requestQueue = Volley.newRequestQueue(this);

        currentNotificationId = getIntent().getLongExtra("notification_id", -1);
        currentTitle = getIntent().getStringExtra("title");
        currentContent = getIntent().getStringExtra("content");
        currentType = getIntent().getStringExtra("type");
        String sender = getIntent().getStringExtra("sender");
        String expiredDate = getIntent().getStringExtra("expired_date");

        // 🔥 Lấy file info
        String fileUrl = getIntent().getStringExtra("file_url");
        String fileType = getIntent().getStringExtra("file_type");

        // DEBUG LOG
        Log.e("NotifAdmin", "ID: " + currentNotificationId + ", URL: " + fileUrl + ", Type: " + fileType);

        txtTitle.setText(currentTitle != null ? currentTitle : "Tiêu đề");
        txtContent.setText(currentContent != null ? currentContent : "");
        txtSender.setText(getString(R.string.notification_sender, sender != null ? sender : "Ban quản lý"));
        txtDate.setText("Hạn: " + (expiredDate != null ? expiredDate : "N/A"));

        // 🔥 Hiển thị file bằng Glide
        displayAttachment(fileUrl, fileType);

        if (currentNotificationId != -1) {
            fetchFeedbackList((int) currentNotificationId);
        } else {
            Toast.makeText(this, "Lỗi ID thông báo", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void displayAttachment(String fileUrl, String fileType) {
        if (fileUrl != null && !fileUrl.isEmpty() && !fileUrl.equals("null")) {
            if ("image".equals(fileType) || (fileType != null && fileType.startsWith("image"))) {
                // HIỂN THỊ ẢNH
                if (cardAttachment != null) cardAttachment.setVisibility(View.VISIBLE);
                if (btnViewFile != null) btnViewFile.setVisibility(View.GONE);

                // 🔥 Dùng Glide load ảnh
                Glide.with(this)
                        .load(fileUrl)
                        .placeholder(R.drawable.bg_white_rounded) // Đảm bảo đã tạo drawable này
                        .error(R.drawable.ic_warning_circle) // Đảm bảo đã tạo icon error
                        .into(imgAttachment);

                imgAttachment.setOnClickListener(v -> openWebBrowser(fileUrl));
            } else {
                // HIỂN THỊ NÚT TẢI FILE
                if (cardAttachment != null) cardAttachment.setVisibility(View.GONE);
                if (btnViewFile != null) btnViewFile.setVisibility(View.VISIBLE);

                String btnText = "Xem tài liệu đính kèm";
                if ("video".equals(fileType) || (fileType != null && fileType.startsWith("video"))) btnText = "Xem Video";
                if ("pdf".equals(fileType)) btnText = "Mở tài liệu PDF";

                btnViewFile.setText(btnText);
                btnViewFile.setOnClickListener(v -> openWebBrowser(fileUrl));
            }
        } else {
            // KHÔNG CÓ FILE
            if (cardAttachment != null) cardAttachment.setVisibility(View.GONE);
            if (btnViewFile != null) btnViewFile.setVisibility(View.GONE);
        }
    }

    private void openWebBrowser(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Không thể mở liên kết", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchFeedbackList(int notificationId) {
        String url = ApiConfig.BASE_URL + "/api/feedback/notification/" + notificationId;
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    feedbackList.clear();
                    parseFeedbackList(response);
                    adapter.notifyDataSetChanged();
                    if (feedbackList.isEmpty()) {
                        layoutEmpty.setVisibility(View.VISIBLE);
                        txtFeedbackCount.setText("Chưa có phản hồi");
                    } else {
                        layoutEmpty.setVisibility(View.GONE);
                        txtFeedbackCount.setText(feedbackList.size() + " phản hồi");
                    }
                },
                error -> Log.e("AdminNotif", "Error fetching feedbacks", error));
        requestQueue.add(request);
    }

    private void parseFeedbackList(JSONArray response) {
        for (int i = 0; i < response.length(); i++) {
            try {
                JSONObject obj = response.getJSONObject(i);
                FeedbackItem_Admin item = new FeedbackItem_Admin();
                item.setId(obj.optInt("feedback_id"));
                item.setTitle(obj.optString("content"));
                item.setDate(obj.optString("created_at"));
                item.setSender(obj.optString("full_name"));
                feedbackList.add(item);
            } catch (JSONException e) {}
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notification_admin, menu);
        for (int i = 0; i < menu.size(); i++) {
            android.view.MenuItem item = menu.getItem(i);
            CharSequence title = item.getTitle();
            if (title != null) {
                android.text.SpannableString spanString = new android.text.SpannableString(title);
                spanString.setSpan(new android.text.style.ForegroundColorSpan(android.graphics.Color.BLACK), 0, spanString.length(), 0);
                item.setTitle(spanString);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_edit) {
            openEditScreen();
            return true;
        } else if (id == R.id.action_delete) {
            confirmDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa thông báo này không?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteNotification())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteNotification() {
        String url = ApiConfig.BASE_URL + "/api/notification/delete/" + currentNotificationId;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.DELETE, url, null,
                response -> {
                    Toast.makeText(this, "Đã xóa!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(CreateNotificationActivity.ACTION_NOTIFICATION_CREATED);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                    finish();
                },
                error -> Toast.makeText(this, "Lỗi xóa", Toast.LENGTH_SHORT).show()
        );
        requestQueue.add(request);
    }

    private void openEditScreen() {
        Intent intent = new Intent(this, CreateNotificationActivity.class);
        intent.putExtra("IS_EDIT_MODE", true);
        intent.putExtra("notification_id", currentNotificationId);
        intent.putExtra("title", currentTitle);
        intent.putExtra("content", currentContent);
        intent.putExtra("type", currentType);
        startActivity(intent);
        finish();
    }
}