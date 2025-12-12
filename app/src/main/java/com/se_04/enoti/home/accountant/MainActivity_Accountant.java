package com.se_04.enoti.home.accountant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.messaging.FirebaseMessaging;
import com.se_04.enoti.R;
import com.se_04.enoti.account.AccountFragment;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.finance.admin.ManageFinanceFragment;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MainActivity_Accountant extends BaseActivity {

    private static final String TAG = "MainActivity_Accountant";
    private static final String SELECTED_ID_KEY = "selected_id";

    // Tăng thời gian chờ lên 30s để đảm bảo mạng chậm vẫn gửi được token
    private static final int MY_SOCKET_TIMEOUT_MS = 30000;

    // Khai báo các Fragment
    private HomeFragment_Accountant homeFragment;
    private ManageFinanceFragment financeFragment;
    private AccountFragment accountFragment;

    private FragmentManager fragmentManager;
    private Fragment activeFragment;
    private int currentSelectedId = R.id.nav_home; // Mặc định là Home

    // Launcher xin quyền thông báo (Giống bên User)
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Log.w(TAG, "Permission denied: POST_NOTIFICATIONS");
                    Toast.makeText(this, "Bạn cần cấp quyền để nhận thông báo công việc.", Toast.LENGTH_LONG).show();
                } else {
                    Log.d(TAG, "Permission granted: POST_NOTIFICATIONS");
                    updateFcmToken(); // Cấp quyền xong thì gửi token ngay
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Tắt chế độ tối để giao diện đồng nhất
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_main_menu_accountant);

        // 2. Kiểm tra môi trường (Google Play Services & Quyền)
        if (checkPlayServices()) {
            checkAndRequestNotificationPermission();
            updateFcmToken();
        }

        // 3. Khởi tạo Fragment
        initFragments(savedInstanceState);

        // 4. Cài đặt sự kiện click menu
        setupBottomNavigation();
    }

    /**
     * Kiểm tra Google Play Services (Bắt buộc cho Firebase)
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, 9000).show();
            } else {
                Toast.makeText(this, "Thiết bị không hỗ trợ Google Play Services", Toast.LENGTH_LONG).show();
            }
            return false;
        }
        return true;
    }

    /**
     * Xin quyền thông báo cho Android 13+
     */
    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Thông báo đang tắt")
                    .setMessage("Kế toán cần bật thông báo để nhận tin duyệt chi.")
                    .setPositiveButton("Mở Cài đặt", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                        startActivity(intent);
                    })
                    .setNegativeButton("Để sau", null)
                    .show();
        }
    }

    private void updateFcmToken() {
        Log.d(TAG, "updateFcmToken: Starting...");
        FirebaseMessaging.getInstance().subscribeToTopic("all_devices");
        // Kế toán subscribe thêm topic riêng nếu cần
        FirebaseMessaging.getInstance().subscribeToTopic("accountants");

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Lỗi lấy FCM token", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    UserItem currentUser = UserManager.getInstance(this).getCurrentUser();

                    if (currentUser != null) {
                        Log.d(TAG, "User: " + currentUser.getName() + " - Token: " + token);
                        sendRegistrationToServer(currentUser.getId(), token);
                    }
                });
    }

    private void sendRegistrationToServer(String userId, String token) {
        String url = ApiConfig.BASE_URL + "/api/users/update_fcm_token";
        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);
            body.put("fcm_token", token);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Log.d(TAG, "Token sent successfully");
                    // Toast.makeText(this, "Kết nối hệ thống thành công ✅", Toast.LENGTH_SHORT).show();
                },
                error -> {
                    String errorMsg = "Lỗi kết nối Server";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            JSONObject data = new JSONObject(responseBody);
                            errorMsg = data.optString("error", errorMsg);
                        } catch (Exception e) {}
                    }
                    Log.e(TAG, "Failed to update token: " + errorMsg);
                }
        ) {
            // 🔥 Thêm Header Auth (Quan trọng)
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
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

    private void initFragments(Bundle savedInstanceState) {
        fragmentManager = getSupportFragmentManager();

        if (savedInstanceState == null) {
            homeFragment = new HomeFragment_Accountant();
            financeFragment = new ManageFinanceFragment();
            accountFragment = new AccountFragment();

            FragmentTransaction transaction = fragmentManager.beginTransaction();

            transaction.add(R.id.fragment_container, accountFragment, "account").hide(accountFragment);
            transaction.add(R.id.fragment_container, financeFragment, "finance").hide(financeFragment);
            transaction.add(R.id.fragment_container, homeFragment, "home");

            transaction.commit();
            activeFragment = homeFragment;
        } else {
            currentSelectedId = savedInstanceState.getInt(SELECTED_ID_KEY, R.id.nav_home);

            homeFragment = (HomeFragment_Accountant) fragmentManager.findFragmentByTag("home");
            financeFragment = (ManageFinanceFragment) fragmentManager.findFragmentByTag("finance");
            accountFragment = (AccountFragment) fragmentManager.findFragmentByTag("account");

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

        bottomNav.setSelectedItemId(currentSelectedId);
    }

    private void switchFragment(Fragment targetFragment, int itemId) {
        if (targetFragment == activeFragment) return;

        FragmentTransaction transaction = fragmentManager.beginTransaction();
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