package com.se_04.enoti.home.accountant;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.messaging.FirebaseMessaging;
import com.se_04.enoti.R;
import com.se_04.enoti.account.AccountFragment;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.finance.admin.ManageFinanceFragment; // Dùng chung với Admin
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class MainActivity_Accountant extends BaseActivity {

    private static final String TAG = "MainActivity_Accountant";
    private static final String SELECTED_ID_KEY = "selected_id";

    // Khai báo các Fragment
    private HomeFragment_Accountant homeFragment;
    private ManageFinanceFragment financeFragment;
    private AccountFragment accountFragment;

    private FragmentManager fragmentManager;
    private Fragment activeFragment;
    private int currentSelectedId = R.id.nav_home; // Mặc định là Home
    private static final int MY_SOCKET_TIMEOUT_MS = 30000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔥 1. Set Layout chính xác (File activity_main_menu_accountant.xml)
        setContentView(R.layout.activity_main_menu_accountant);

        // 2. Cập nhật Token ngay khi vào màn hình chính
        updateFcmToken();

        // 3. Khởi tạo Fragment (Cơ chế add/hide/show)
        initFragments(savedInstanceState);

        // 4. Cài đặt sự kiện click menu
        setupBottomNavigation();
    }

    /**
     * Hàm này giúp server biết thiết bị này đang online để gửi thông báo
     */
    private void updateFcmToken() {
        Log.d(TAG, "updateFcmToken: Starting...");
        FirebaseMessaging.getInstance().subscribeToTopic("all_devices");

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "updateFcmToken: Fetching FCM registration token failed", task.getException());
                        Toast.makeText(this, "Lỗi lấy Token Firebase: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String token = task.getResult();
                    Log.d(TAG, "updateFcmToken: Token retrieved = " + token);

                    UserItem currentUser = UserManager.getInstance(this).getCurrentUser();
                    if (currentUser != null) {
                        Log.d(TAG, "updateFcmToken: Sending token to server for UserID: " + currentUser.getId());
                        sendRegistrationToServer(currentUser.getId(), token);
                    } else {
                        Log.e(TAG, "updateFcmToken: User is null, cannot send token.");
                    }
                });
    }

    private void sendRegistrationToServer(String userId, String token) {
        String url = ApiConfig.BASE_URL + "/api/users/update_fcm_token";
        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);
            body.put("fcm_token", token);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Sending Body: " + body.toString());

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Log.d(TAG, "sendRegistrationToServer: Token sent successfully");
                    // Không cần Toast mỗi lần mở app, tránh làm phiền user
                },
                error -> {
                    String errorMsg = "Lỗi kết nối Server";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            Log.e(TAG, "Server Error Body: " + responseBody);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    Log.e(TAG, "sendRegistrationToServer: Failed to update token: " + error.toString());
                }
        ) {
            // 🔥🔥🔥 PHẦN QUAN TRỌNG NHẤT: THÊM HEADER ĐỂ HẾT LỖI 401 🔥🔥🔥
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                String authToken = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (authToken != null && !authToken.isEmpty()) {
                    headers.put("Authorization", "Bearer " + authToken);
                }
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                MY_SOCKET_TIMEOUT_MS,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        Volley.newRequestQueue(this).add(request);
    }

    /**
     * Khởi tạo các Fragment hoặc khôi phục lại khi xoay màn hình
     */
    private void initFragments(Bundle savedInstanceState) {
        fragmentManager = getSupportFragmentManager();

        if (savedInstanceState == null) {
            // --- LẦN ĐẦU CHẠY APP ---
            homeFragment = new HomeFragment_Accountant();
            financeFragment = new ManageFinanceFragment();
            accountFragment = new AccountFragment();

            FragmentTransaction transaction = fragmentManager.beginTransaction();

            // Thêm tất cả vào nhưng ẨN đi, trừ Home
            transaction.add(R.id.fragment_container, accountFragment, "account").hide(accountFragment);
            transaction.add(R.id.fragment_container, financeFragment, "finance").hide(financeFragment);
            transaction.add(R.id.fragment_container, homeFragment, "home"); // Add cuối cùng để hiển thị

            transaction.commit();
            activeFragment = homeFragment;
        } else {
            // --- KHI XOAY MÀN HÌNH (Khôi phục) ---
            currentSelectedId = savedInstanceState.getInt(SELECTED_ID_KEY, R.id.nav_home);

            homeFragment = (HomeFragment_Accountant) fragmentManager.findFragmentByTag("home");
            financeFragment = (ManageFinanceFragment) fragmentManager.findFragmentByTag("finance");
            accountFragment = (AccountFragment) fragmentManager.findFragmentByTag("account");

            // Tìm xem fragment nào đang active dựa trên ID menu đã lưu
            if (currentSelectedId == R.id.nav_manage_finance) activeFragment = financeFragment;
            else if (currentSelectedId == R.id.nav_profile) activeFragment = accountFragment;
            else activeFragment = homeFragment;
        }
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment targetFragment = null;

            // 🔥 Mapping ID từ file bottom_nav_menu_accountant.xml
            if (itemId == R.id.nav_home) {
                targetFragment = homeFragment;
            } else if (itemId == R.id.nav_manage_finance) {
                targetFragment = financeFragment;
            } else if (itemId == R.id.nav_profile) {
                targetFragment = accountFragment;
            }

            if (targetFragment != null) {
                switchFragment(targetFragment, itemId);
                return true;
            }
            return false;
        });

        // Đánh dấu icon đang chọn
        bottomNav.setSelectedItemId(currentSelectedId);
    }

    /**
     * Chuyển đổi giữa các tab mà không load lại dữ liệu (chỉ ẩn/hiện)
     */
    private void switchFragment(Fragment targetFragment, int itemId) {
        if (targetFragment == activeFragment) return;

        FragmentTransaction transaction = fragmentManager.beginTransaction();

        // Hiệu ứng chuyển cảnh (Optional)
        transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);

        transaction.hide(activeFragment).show(targetFragment);

        activeFragment = targetFragment;
        currentSelectedId = itemId;
        transaction.commit();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SELECTED_ID_KEY, currentSelectedId);
    }
}