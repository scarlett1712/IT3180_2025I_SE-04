package com.se_04.enoti.notification;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.se_04.enoti.R;
import com.se_04.enoti.feedback.FeedbackActivity;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

public class NotificationDetailActivity extends BaseActivity {

    private NotificationRepository repository;
    private long notificationId;
    private boolean wasMarkedAsRead = false; // 🔥 Track if we marked this as read

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = NotificationRepository.getInstance(this);

        setContentView(R.layout.activity_notification_detail);

        MaterialButton btnReply = findViewById(R.id.btnReply);

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

        notificationId = getIntent().getLongExtra("notification_id", -1);
        String title = getIntent().getStringExtra("title");
        String expired_date = getIntent().getStringExtra("expired_date");
        String content = getIntent().getStringExtra("content");
        String sender = getIntent().getStringExtra("sender");
        boolean isRead = getIntent().getBooleanExtra("is_read", false);

        if (title != null) txtTitle.setText(title);
        if (expired_date != null) txtDate.setText(expired_date);
        if (content != null) txtContent.setText(content);
        if (sender != null) txtSender.setText(getString(R.string.notification_sender, sender));

        // 🔥 Mark as read if it hasn't been read yet
        if (notificationId != -1 && !isRead) {
            Log.d("NotificationDetail", "Auto marking notification " + notificationId + " as read");
            repository.markAsRead(notificationId, new NotificationRepository.SimpleCallback() {
                @Override
                public void onSuccess() {
                    Log.d("NotificationDetail", "✅ Successfully marked as read");
                    wasMarkedAsRead = true; // 🔥 Set flag when successful
                }

                @Override
                public void onError(String message) {
                    Log.e("NotificationDetail", "❌ Failed to mark as read: " + message);
                }
            });
        }

        btnReply.setOnClickListener(v -> {
            Intent intent = new Intent(NotificationDetailActivity.this, FeedbackActivity.class);
            intent.putExtra("notification_id", notificationId);
            intent.putExtra("title", title);
            intent.putExtra("sender", sender);

            long userId = 0;
            try {
                userId = Long.parseLong(UserManager.getInstance(this).getCurrentUser().getId());
            } catch (Exception e) {
                e.printStackTrace();
            }
            intent.putExtra("user_id", userId);

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
    public void finish() {
        // 🔥 If we marked this notification as read, tell the previous activity to refresh
        if (wasMarkedAsRead) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("notification_marked_read", notificationId);
            setResult(RESULT_OK, resultIntent);
        }
        super.finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
            toolbar.setBackgroundColor(typedValue.data);
            toolbar.setTitleTextColor(getResources().getColor(android.R.color.white, getTheme()));
        }
    }
}