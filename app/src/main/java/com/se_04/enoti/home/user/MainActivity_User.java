package com.se_04.enoti.home.user;

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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.volley.DefaultRetryPolicy; // 🔥 Import RetryPolicy
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
import com.se_04.enoti.feedback.FeedbackFragment;
import com.se_04.enoti.finance.FinanceFragment;
import com.se_04.enoti.maintenance.user.UserAssetFragment;
import com.se_04.enoti.notification.NotificationFragment;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class MainActivity_User extends BaseActivity {

    private static final String TAG = "MainActivity_User";
    private static final String SELECTED_ITEM_ID_KEY = "selectedItemIdKey";
    private static final String ACTIVE_FRAGMENT_TAG_KEY = "activeFragmentTagKey";

    // 🔥 Tăng thời gian chờ lên 30 giây để đợi Server Render khởi động
    private static final int MY_SOCKET_TIMEOUT_MS = 30000;

    private BottomNavigationView bottomNavigationView;

    // Khai báo các Fragment
    private HomeFragment_User homeFragmentUser;
    private NotificationFragment notificationFragment;
    private FinanceFragment financeFragment;
    private FeedbackFragment feedbackFragment;
    private UserAssetFragment assetFragment;
    private AccountFragment accountFragment;

    private Fragment activeFragment; // Fragment đang hiển thị
    private FragmentManager fragmentManager;
    private int currentSelectedItemId = R.id.nav_home; // Item mặc định

    // Launcher xin quyền thông báo
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Log.w(TAG, "Permission denied: POST_NOTIFICATIONS");
                    Toast.makeText(this, "Bạn cần cấp quyền để nhận thông báo mới.", Toast.LENGTH_LONG).show();
                } else {
                    Log.d(TAG, "Permission granted: POST_NOTIFICATIONS");
                    // Sau khi cấp quyền, thử update token lại
                    updateFcmToken();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "🚀 onCreate: Starting MainActivity_User...");

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_main_menu_user);

        // 1. Kiểm tra Google Play Services (Bắt buộc cho Firebase)
        if (checkPlayServices()) {
            Log.d(TAG, "onCreate: Google Play Services OK");
            // 2. Kiểm tra toàn diện quyền thông báo
            checkAndRequestNotificationPermission();

            // 3. 🔥 QUAN TRỌNG: Cập nhật FCM Token & Subscribe Topic
            updateFcmToken();
        } else {
            Log.e(TAG, "onCreate: Google Play Services MISSING or ERROR");
        }

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        fragmentManager = getSupportFragmentManager();

        if (savedInstanceState == null) {
            homeFragmentUser = new HomeFragment_User();
            notificationFragment = new NotificationFragment();
            financeFragment = new FinanceFragment();
            feedbackFragment = new FeedbackFragment();
            assetFragment = new UserAssetFragment();
            accountFragment = new AccountFragment();

            FragmentTransaction transaction = fragmentManager.beginTransaction();

            transaction.add(R.id.fragment_container, accountFragment, "account").hide(accountFragment);
            transaction.add(R.id.fragment_container, financeFragment, "finance").hide(financeFragment);
            transaction.add(R.id.fragment_container, notificationFragment, "notifications").hide(notificationFragment);
            transaction.add(R.id.fragment_container, feedbackFragment, "feedback").hide(feedbackFragment);
            transaction.add(R.id.fragment_container, assetFragment, "asset").hide(assetFragment);
            transaction.add(R.id.fragment_container, homeFragmentUser, "home");

            transaction.commit();

            activeFragment = homeFragmentUser;
            currentSelectedItemId = R.id.nav_home;

        } else {
            currentSelectedItemId = savedInstanceState.getInt(SELECTED_ITEM_ID_KEY, R.id.nav_home);
            String activeTag = savedInstanceState.getString(ACTIVE_FRAGMENT_TAG_KEY, "home");

            homeFragmentUser = (HomeFragment_User) fragmentManager.findFragmentByTag("home");
            notificationFragment = (NotificationFragment) fragmentManager.findFragmentByTag("notifications");
            financeFragment = (FinanceFragment) fragmentManager.findFragmentByTag("finance");
            feedbackFragment = (FeedbackFragment) fragmentManager.findFragmentByTag("feedback");
            assetFragment = (UserAssetFragment) fragmentManager.findFragmentByTag("asset");
            accountFragment = (AccountFragment) fragmentManager.findFragmentByTag("account");

            if (homeFragmentUser == null) homeFragmentUser = new HomeFragment_User();
            if (notificationFragment == null) notificationFragment = new NotificationFragment();
            if (financeFragment == null) financeFragment = new FinanceFragment();
            if (feedbackFragment == null) feedbackFragment = new FeedbackFragment();
            if (assetFragment == null) assetFragment = new UserAssetFragment();
            if (accountFragment == null) accountFragment = new AccountFragment();

            switch (activeTag) {
                case "notifications": activeFragment = notificationFragment; break;
                case "finance": activeFragment = financeFragment; break;
                case "feedback": activeFragment = feedbackFragment; break;
                case "asset": activeFragment = assetFragment; break;
                case "account": activeFragment = accountFragment; break;
                case "home":
                default: activeFragment = homeFragmentUser; break;
            }
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == currentSelectedItemId) return true;

            Fragment targetFragment = null;
            String targetTag = "";

            if (itemId == R.id.nav_home) {
                targetFragment = homeFragmentUser;
                targetTag = "home";
            } else if (itemId == R.id.nav_finance) {
                targetFragment = financeFragment;
                targetTag = "finance";
            } else if (itemId == R.id.nav_notifications) {
                targetFragment = notificationFragment;
                targetTag = "notifications";
            } else if (itemId == R.id.nav_asset) {
                targetFragment = assetFragment;
                targetTag = "asset";
            } else if (itemId == R.id.nav_account) {
                targetFragment = accountFragment;
                targetTag = "account";
            } else if (itemId == R.id.nav_feedback) {
                targetFragment = feedbackFragment;
                targetTag = "feedback";
            }

            if (targetFragment != null) {
                FragmentTransaction transaction = fragmentManager.beginTransaction();
                if (!targetFragment.isAdded()) {
                    transaction.add(R.id.fragment_container, targetFragment, targetTag);
                }
                transaction.hide(activeFragment).show(targetFragment);
                activeFragment = targetFragment;
                currentSelectedItemId = itemId;
                transaction.commit();
            }
            return true;
        });

        bottomNavigationView.setSelectedItemId(currentSelectedItemId);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SELECTED_ITEM_ID_KEY, currentSelectedItemId);
        if (activeFragment != null && activeFragment.getTag() != null) {
            outState.putString(ACTIVE_FRAGMENT_TAG_KEY, activeFragment.getTag());
        }
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        Log.d(TAG, "checkPlayServices: resultCode = " + resultCode);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                Log.w(TAG, "checkPlayServices: Recoverable error, showing dialog");
                apiAvailability.getErrorDialog(this, resultCode, 9000).show();
            } else {
                Log.e(TAG, "checkPlayServices: Device not supported (No Firebase)");
                Toast.makeText(this, "Thiết bị không hỗ trợ thông báo (Thiếu Google Play Services)", Toast.LENGTH_LONG).show();
            }
            return false;
        }
        return true;
    }

    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting permission: POST_NOTIFICATIONS");
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            } else {
                Log.d(TAG, "Permission POST_NOTIFICATIONS already granted");
            }
        }

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled in system settings");
            new AlertDialog.Builder(this)
                    .setTitle("Thông báo đang tắt")
                    .setMessage("Bạn cần bật thông báo cho ứng dụng để nhận tin tức mới nhất.")
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
                    Toast.makeText(this, "Đã kết nối hệ thống thông báo ✅", Toast.LENGTH_SHORT).show();
                },
                error -> {
                    String errorMsg = "Lỗi kết nối Server";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            Log.e(TAG, "Server Error Body: " + responseBody);
                            JSONObject data = new JSONObject(responseBody);
                            errorMsg = data.optString("error", errorMsg);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing error body", e);
                        }
                    }
                    Log.e(TAG, "sendRegistrationToServer: Failed to update token: " + error.toString());
                    Toast.makeText(this, "Lỗi Server: " + errorMsg, Toast.LENGTH_LONG).show();
                }
        );

        // 🔥 FIX: Áp dụng RetryPolicy (Tăng timeout lên 30s)
        request.setRetryPolicy(new DefaultRetryPolicy(
                MY_SOCKET_TIMEOUT_MS,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        Volley.newRequestQueue(this).add(request);
    }

    public void switchToNotificationsTab() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_notifications);
        }
    }

    public void switchToFinanceTab() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_finance);
        }
    }

    public void switchToFeedbackTab() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_feedback);
        }
    }

    public void switchToAssetTab() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_asset);
        }
    }
}