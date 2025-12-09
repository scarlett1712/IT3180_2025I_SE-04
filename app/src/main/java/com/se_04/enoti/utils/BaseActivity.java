package com.se_04.enoti.utils;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.account_related.LogInActivity;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onResume() {
        super.onResume();
        // Mỗi khi màn hình hiện lên, kiểm tra xem còn hạn đăng nhập không
        checkSessionValidity();
    }

    private void checkSessionValidity() {
        UserItem user = UserManager.getInstance(this).getCurrentUser();
        if (user == null) return;

        // Gọi 1 API nhẹ bất kỳ để check token (Ví dụ API lấy profile)
        String url = ApiConfig.BASE_URL + "/api/users/profile/" + user.getId();

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    // Token sống -> OK, không làm gì cả
                },
                error -> {
                    // Nếu lỗi 401 -> Token chết -> ĐĂNG XUẤT NGAY
                    if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                        UserManager.getInstance(this).forceLogout();
                    }
                }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                String token = UserManager.getInstance(BaseActivity.this).getAuthToken();
                if (token != null) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };

        // Gửi request ngầm
        Volley.newRequestQueue(this).add(request);
    }
}