package com.se_04.enoti.feedback;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;

public class FeedbackDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback_detail);

        // Ánh xạ các view chính
        TextView txtDetailTitle = findViewById(R.id.txtDetailTitle);
        TextView txtDetailDate = findViewById(R.id.txtDetailDate);
        TextView txtDetailRepliedNotification = findViewById(R.id.txtDetailRepliedNotification);
        TextView txtDetailContent = findViewById(R.id.txtDetailContent);

        // Ánh xạ phần phản hồi quản trị viên (được thêm trong XML)
        LinearLayout adminReplySection = findViewById(R.id.adminReplySection);
        TextView txtAdminReplyDate = findViewById(R.id.txtAdminReplyDate);
        TextView txtAdminReplyContent = findViewById(R.id.txtAdminReplyContent);

        // Thiết lập toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setTitleTextColor(getResources().getColor(android.R.color.white, getTheme()));
            getSupportActionBar().setTitle("Chi tiết phản hồi");
        }

        // Lấy phản hồi được chọn
        int position = getIntent().getIntExtra("position", -1);
        if (position >= 0) {
            FeedbackItem item = FeedbackRepository.getInstance().getFeedbacks().get(position);

            txtDetailTitle.setText(item.getTitle());
            txtDetailDate.setText(item.getDate());
            txtDetailRepliedNotification.setText(item.getRepliedNotification());
            txtDetailContent.setText(item.getContent());

            if (item.isReplied()) {
                adminReplySection.setVisibility(LinearLayout.VISIBLE);
                txtAdminReplyDate.setText(item.getDate());
                txtAdminReplyContent.setText(item.getRepliedNotification());
            } else {
                adminReplySection.setVisibility(LinearLayout.GONE);
            }
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
