package com.se_04.enoti.notification;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;

public class NotificationDetailActivity extends AppCompatActivity {

    private final NotificationRepository repository = NotificationRepository.getInstance();

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

        long notificationId = getIntent().getLongExtra("notification_id", -1);
        String title = getIntent().getStringExtra("title");
        String date = getIntent().getStringExtra("expired_date");
        String expired_date = getIntent().getStringExtra("expired_date");
        String content = getIntent().getStringExtra("content");
        String sender = getIntent().getStringExtra("sender");
        boolean isRead = getIntent().getBooleanExtra("is_read", false);

        if (title != null) txtTitle.setText(title);
        if (date != null) txtDate.setText(expired_date);
        if (content != null) txtContent.setText(content);
        if (sender != null) txtSender.setText(getString(R.string.notification_sender, sender));

        // If not read, mark read (both local UI and backend)
        if (!isRead && notificationId > 0) {
            repository.markAsRead(notificationId, new NotificationRepository.SimpleCallback() {
                @Override
                public void onSuccess() {
                    // no-op UI update required here (we already marked read in adapter)
                }

                @Override
                public void onError(String message) {
                    // optionally show toast
                }
            });
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