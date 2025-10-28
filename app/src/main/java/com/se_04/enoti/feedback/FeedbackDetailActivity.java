package com.se_04.enoti.feedback;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.notification.NotificationRepository;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FeedbackDetailActivity extends AppCompatActivity {

    private LinearLayout adminReplySection;
    private TextView txtAdminReplyDate, txtAdminReplyContent;
    private RequestQueue queue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback_detail);

        queue = Volley.newRequestQueue(this);

        // Ánh xạ view
        TextView txtDetailTitle = findViewById(R.id.txtDetailTitle);
        TextView txtDetailDate = findViewById(R.id.txtDetailDate);
        TextView txtDetailRepliedNotification = findViewById(R.id.txtDetailRepliedNotification);
        TextView txtDetailContent = findViewById(R.id.txtDetailContent);
        adminReplySection = findViewById(R.id.adminReplySection);
        txtAdminReplyDate = findViewById(R.id.txtAdminReplyDate);
        txtAdminReplyContent = findViewById(R.id.txtAdminReplyContent);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết phản hồi");
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        // Lấy FeedbackItem từ Intent
        FeedbackItem item = (FeedbackItem) getIntent().getSerializableExtra("feedback_item");
        if (item == null) {
            Toast.makeText(this, "Không thể tải chi tiết phản hồi.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // --- Hiển thị nội dung phản hồi ---
        txtDetailTitle.setText("Mã phản hồi #" + item.getFeedbackId());
        txtDetailDate.setText(item.getCreatedAt());
        txtDetailContent.setText(item.getContent());

        // --- Hiển thị tên thông báo liên quan ---
        txtDetailRepliedNotification.setText("Đang tải thông báo...");

        NotificationRepository.getInstance().fetchNotificationTitle(
                item.getNotificationId(),
                new NotificationRepository.TitleCallback() {
                    @Override
                    public void onSuccess(String title) {
                        txtDetailRepliedNotification.setText("Phản hồi cho: " + title);
                    }

                    @Override
                    public void onError(String message) {
                        txtDetailRepliedNotification.setText("Không thể tải thông báo (" + item.getNotificationId() + ")");
                    }
                }
        );

        // --- Nếu feedback đã được admin phản hồi, gọi API để hiển thị ---
        if (item.getStatus() != null && item.getStatus().equalsIgnoreCase("replied")) {
            fetchAdminReply((int) item.getFeedbackId());
        } else {
            adminReplySection.setVisibility(View.GONE);
        }
    }

    private void fetchAdminReply(int feedbackId) {
        String url = ApiConfig.BASE_URL + "/api/feedback/" + feedbackId + "/replies";

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> handleAdminReplyResponse(response),
                error -> {
                    adminReplySection.setVisibility(View.GONE);
                    Toast.makeText(this, "Không thể tải phản hồi của quản trị viên", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void handleAdminReplyResponse(JSONArray response) {
        if (response == null || response.length() == 0) {
            adminReplySection.setVisibility(View.GONE);
            return;
        }

        try {
            // Giả sử chỉ có 1 phản hồi (hoặc lấy phản hồi mới nhất)
            JSONObject reply = response.getJSONObject(response.length() - 1);

            String replyContent = reply.optString("reply_content", "(Không có nội dung)");
            String adminName = reply.optString("admin_name", "Quản trị viên");
            String replyDate = reply.optString("created_at", "");


            adminReplySection.setVisibility(View.VISIBLE);
            txtAdminReplyContent.setText(adminName + ": " + replyContent);
            String formattedDate = formatDate(replyDate);
            txtAdminReplyDate.setText(formattedDate);


        } catch (JSONException e) {
            e.printStackTrace();
            adminReplySection.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "(Không rõ thời gian)";
        try {
            // Parse chuỗi ISO (ví dụ: 2025-10-27T16:27:57.257Z)
            java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

            java.util.Date date = isoFormat.parse(isoDate);

            // Định dạng lại thành dd-MM-yyyy HH:mm
            java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm");
            outputFormat.setTimeZone(java.util.TimeZone.getDefault());

            return outputFormat.format(date);
        } catch (Exception e) {
            e.printStackTrace();
            return isoDate;
        }
    }
}