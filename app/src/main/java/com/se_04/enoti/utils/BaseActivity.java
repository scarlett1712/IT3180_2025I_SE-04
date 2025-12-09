package com.se_04.enoti.utils;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;

import java.util.HashMap;
import java.util.Map;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupEdgeToEdgeUI(); // 🔥 Gọi hàm thiết lập giao diện ngay lập tức
    }

    private void setupEdgeToEdgeUI() {
        Window window = getWindow();

        // 1. Ép buộc vẽ tràn qua vùng Camera (Tai thỏ) bằng Code (Fix lỗi khoảng trắng cứng đầu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        // 2. Tắt chế độ tự động của hệ thống để ta tự kiểm soát màu
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // 3. Tô màu Tím cho thanh trạng thái
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.purple_primary));

        // 4. Chỉnh icon màu Trắng
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
        }
    }

    // =================================================================================
    // XỬ LÝ PADDING (Đẩy nội dung xuống để không bị che)
    // =================================================================================

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        applyManualInsets();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        applyManualInsets();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        applyManualInsets();
    }

    private void applyManualInsets() {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                // Đẩy nội dung xuống một đoạn bằng chiều cao thanh trạng thái
                v.setPadding(0, insets.top, 0, 0);
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    // ... (Giữ nguyên phần checkSessionValidity bên dưới)
    @Override
    protected void onResume() {
        super.onResume();
        checkSessionValidity();
    }

    private void checkSessionValidity() {
        UserItem user = UserManager.getInstance(this).getCurrentUser();
        if (user == null) return;

        String url = ApiConfig.BASE_URL + "/api/users/profile/" + user.getId();
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {},
                error -> {
                    if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                        UserManager.getInstance(this).forceLogout();
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(BaseActivity.this).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }
}