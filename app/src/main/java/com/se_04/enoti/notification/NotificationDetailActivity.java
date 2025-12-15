package com.se_04.enoti.notification;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView; // Import CardView

import com.bumptech.glide.Glide; // 🔥 Import Glide
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.se_04.enoti.R;
import com.se_04.enoti.feedback.FeedbackActivity;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

public class NotificationDetailActivity extends BaseActivity {

    private NotificationRepository repository;
    private long notificationId;
    private boolean wasMarkedAsRead = false;

    // 🔥 Views mới
    private ImageView imgAttachment;
    private CardView cardAttachment;
    private MaterialButton btnViewFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = NotificationRepository.getInstance(this);
        setContentView(R.layout.activity_notification_detail);

        // Ánh xạ views cũ
        MaterialButton btnReply = findViewById(R.id.btnReply);
        TextView txtTitle = findViewById(R.id.txtDetailTitle);
        TextView txtDate = findViewById(R.id.txtDetailDate);
        TextView txtSender = findViewById(R.id.txtDetailSender);
        TextView txtContent = findViewById(R.id.txtDetailContent);

        // 🔥 Ánh xạ Views mới (File đính kèm)
        imgAttachment = findViewById(R.id.imgAttachment);
        cardAttachment = findViewById(R.id.cardAttachment);
        btnViewFile = findViewById(R.id.btnViewFile);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setTitleTextColor(getResources().getColor(android.R.color.white, getTheme()));
            getSupportActionBar().setTitle("Chi tiết thông báo");
        }

        // Lấy dữ liệu Intent
        notificationId = getIntent().getLongExtra("notification_id", -1);
        String title = getIntent().getStringExtra("title");
        String expired_date = getIntent().getStringExtra("expired_date");
        String content = getIntent().getStringExtra("content");
        String sender = getIntent().getStringExtra("sender");
        boolean isRead = getIntent().getBooleanExtra("is_read", false);

        // 🔥 Lấy thông tin file
        String fileUrl = getIntent().getStringExtra("file_url");
        String fileType = getIntent().getStringExtra("file_type");

        if (title != null) txtTitle.setText(title);
        if (expired_date != null) txtDate.setText(expired_date);
        if (content != null) txtContent.setText(content);
        if (sender != null) txtSender.setText(getString(R.string.notification_sender, sender));

        // 🔥 LOGIC HIỂN THỊ FILE
        displayAttachment(fileUrl, fileType);

        // Đánh dấu đã đọc
        if (notificationId != -1 && !isRead) {
            repository.markAsRead(notificationId, new NotificationRepository.SimpleCallback() {
                @Override
                public void onSuccess() { wasMarkedAsRead = true; }
                @Override
                public void onError(String message) { Log.e("NotiDetail", "Err: " + message); }
            });
        }

        btnReply.setOnClickListener(v -> {
            Intent intent = new Intent(NotificationDetailActivity.this, FeedbackActivity.class);
            intent.putExtra("notification_id", notificationId);
            intent.putExtra("title", title);
            intent.putExtra("sender", sender);
            try {
                long userId = Long.parseLong(UserManager.getInstance(this).getCurrentUser().getId());
                intent.putExtra("user_id", userId);
            } catch (Exception e) { e.printStackTrace(); }
            startActivity(intent);
        });
    }

    private void displayAttachment(String fileUrl, String fileType) {
        if (fileUrl != null && !fileUrl.isEmpty() && !fileUrl.equals("null")) {
            if ("image".equals(fileType) || (fileType != null && fileType.startsWith("image"))) {
                // Hiển thị ảnh
                cardAttachment.setVisibility(View.VISIBLE);
                btnViewFile.setVisibility(View.GONE);

                Glide.with(this)
                        .load(fileUrl)
                        .placeholder(R.drawable.bg_white_rounded)
                        .into(imgAttachment);

                imgAttachment.setOnClickListener(v -> openWebBrowser(fileUrl));
            } else {
                // Hiển thị nút tải file (PDF, Video...)
                cardAttachment.setVisibility(View.GONE);
                btnViewFile.setVisibility(View.VISIBLE);

                String btnText = "Xem tài liệu đính kèm";
                if ("video".equals(fileType)) btnText = "Xem Video đính kèm";
                if ("pdf".equals(fileType)) btnText = "Mở tài liệu PDF";

                btnViewFile.setText(btnText);
                btnViewFile.setOnClickListener(v -> openWebBrowser(fileUrl));
            }
        } else {
            // Không có file
            cardAttachment.setVisibility(View.GONE);
            btnViewFile.setVisibility(View.GONE);
        }
    }

    private void openWebBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
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

    @Override
    public void finish() {
        if (wasMarkedAsRead) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("notification_marked_read", notificationId);
            setResult(RESULT_OK, resultIntent);
        }
        super.finish();
    }
}