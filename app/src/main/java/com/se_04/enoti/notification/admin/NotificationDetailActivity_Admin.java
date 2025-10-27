package com.se_04.enoti.notification.admin;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.feedback.admin.FeedbackItem_Admin;
import com.se_04.enoti.feedback.admin.FeedbackAdapter_Admin;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ✅ Màn hình chi tiết thông báo cho ADMIN
 * Hiển thị thông tin thông báo + danh sách feedback mà cư dân gửi về
 */
public class NotificationDetailActivity_Admin extends AppCompatActivity {

    private TextView txtTitle, txtDate, txtSender, txtContent, txtFeedbackCount;
    private RecyclerView recyclerFeedback;
    private LinearLayout layoutEmpty;
    private FeedbackAdapter_Admin adapter;
    private final List<FeedbackItem_Admin> feedbackList = new ArrayList<>();
    private RequestQueue requestQueue;
    private int notificationId;

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
            toolbar.setTitleTextColor(getResources().getColor(android.R.color.white, getTheme()));
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
        long notificationId = getIntent().getLongExtra("notification_id", -1);
        String title = getIntent().getStringExtra("title");
        String content = getIntent().getStringExtra("content");
        String sender = getIntent().getStringExtra("sender");
        String expiredDate = getIntent().getStringExtra("expired_date");

        txtTitle.setText(title != null ? title : "Không rõ tiêu đề");
        txtContent.setText(content != null ? content : "(Không có nội dung)");
        txtSender.setText(getString(R.string.notification_sender, sender != null ? sender : "Ban quản lý"));
        txtDate.setText("Hạn phản hồi: " + (expiredDate != null ? expiredDate : "Không rõ"));

        // --- Gọi API feedback ---
        if (notificationId != -1) {
            fetchFeedbackList((int) notificationId); // nếu backend cần int
        } else {
            Toast.makeText(this, "Thiếu notification_id", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * 🔹 Lấy danh sách feedback của thông báo
     */
    private void fetchFeedbackList(int notificationId) {
        String url = ApiConfig.BASE_URL + "/api/feedback/notification/" + notificationId;
        Log.d("AdminNotifDetail", "Fetching feedbacks: " + url);

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
                    Toast.makeText(this, "Không thể tải danh sách phản hồi", Toast.LENGTH_SHORT).show();
                });

        requestQueue.add(request);
    }

    /**
     * 🔹 Parse JSON response thành danh sách FeedbackItem_Admin
     */
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
                Log.e("AdminNotifDetail", "JSON parse error", e);
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
