package com.se_04.enoti.feedback;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.notification.NotificationRepository;

public class FeedbackDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback_detail);

        // Ánh xạ các view
        TextView txtDetailTitle = findViewById(R.id.txtDetailTitle);
        TextView txtDetailDate = findViewById(R.id.txtDetailDate);
        TextView txtDetailRepliedNotification = findViewById(R.id.txtDetailRepliedNotification);
        TextView txtDetailContent = findViewById(R.id.txtDetailContent);
        LinearLayout adminReplySection = findViewById(R.id.adminReplySection);
        TextView txtAdminReplyDate = findViewById(R.id.txtAdminReplyDate);
        TextView txtAdminReplyContent = findViewById(R.id.txtAdminReplyContent);

        // Thiết lập toolbar
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

        // --- Hiển thị phản hồi của admin nếu có ---
        if (item.getStatus() != null && item.getStatus().equalsIgnoreCase("replied")) {
            adminReplySection.setVisibility(View.VISIBLE);
            txtAdminReplyDate.setText(item.getCreatedAt()); // hoặc trường khác nếu có reply_at
            txtAdminReplyContent.setText("Phản hồi từ admin sẽ hiển thị ở đây (cần thêm field trong model nếu backend có).");
        } else {
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
}
