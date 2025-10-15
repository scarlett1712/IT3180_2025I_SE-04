package com.se_04.enoti.notification;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.se_04.enoti.R;
import com.se_04.enoti.feedback.FeedbackActivity;

public class NotificationDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_detail);

        TextView txtTitle = findViewById(R.id.txtDetailTitle);
        TextView txtDate = findViewById(R.id.txtDetailDate);
        TextView txtSender = findViewById(R.id.txtDetailSender);
        TextView txtContent = findViewById(R.id.txtDetailContent);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setTitleTextColor(getResources().getColor(android.R.color.white, getTheme()));
            getSupportActionBar().setTitle("Chi tiết thông báo");
        }

        // Nhận vị trí thông báo
        int position = getIntent().getIntExtra("position", -1);

        if (position >= 0) {
            NotificationItem item = NotificationRepository.getInstance().getNotifications().get(position);

            txtTitle.setText(item.getTitle());
            txtDate.setText(item.getDate());
            txtContent.setText(item.getContent());
            txtSender.setText(getString(R.string.notification_sender, item.getSender()));

            // Đánh dấu là đã đọc
            item.setRead(true);
        }

        MaterialButton btnReply = findViewById(R.id.btnReply);
        //Mở trang phản hồi khi nhấn nút
        btnReply.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), FeedbackActivity.class);
            intent.putExtra("title", txtTitle.getText().toString());
            intent.putExtra("position", position);
            startActivity(intent);
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Đặt lại màu nền cho toolbar khi quay lại
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
            toolbar.setBackgroundColor(typedValue.data);
            toolbar.setTitleTextColor(getResources().getColor(android.R.color.white, getTheme()));
        }
    }
}
