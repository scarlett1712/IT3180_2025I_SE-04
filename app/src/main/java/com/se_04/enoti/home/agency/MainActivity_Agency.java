package com.se_04.enoti.home.agency;

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
import com.se_04.enoti.maintenance.admin.ManageAssetFragment; // 🔥 Đã đổi sang Asset
import com.se_04.enoti.residents.ManageResidentFragment;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MainActivity_Agency extends BaseActivity {

    private static final String TAG = "MainActivity_Agency";
    private static final String SELECTED_ITEM_ID_KEY = "selectedItemIdKey";
    private static final String ACTIVE_FRAGMENT_TAG_KEY = "activeFragmentTagKey";

    private static final int MY_SOCKET_TIMEOUT_MS = 30000;

    private HomeFragment_Agency homeFragmentAgency;
    private ManageResidentFragment manageResidentFragment;
    private ManageAssetFragment manageAssetFragment; // 🔥 Đổi từ Feedback sang Asset
    private AccountFragment accountFragment;

    private Fragment activeFragment;
    private FragmentManager fragmentManager;
    private int currentSelectedItemId = R.id.nav_home;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Log.w(TAG, "Permission denied: POST_NOTIFICATIONS");
                    Toast.makeText(this, "Bạn cần cấp quyền để nhận thông báo công việc.", Toast.LENGTH_LONG).show();
                } else {
                    Log.d(TAG, "Permission granted: POST_NOTIFICATIONS");
                    updateFcmToken();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_main_menu_agency);

        if (checkPlayServices()) {
            checkAndRequestNotificationPermission();
            updateFcmToken();
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        fragmentManager = getSupportFragmentManager();

        if (savedInstanceState == null) {
            homeFragmentAgency = new HomeFragment_Agency();
            manageResidentFragment = new ManageResidentFragment();
            manageAssetFragment = new ManageAssetFragment(); // 🔥 Khởi tạo Asset Fragment
            accountFragment = new AccountFragment();

            FragmentTransaction transaction = fragmentManager.beginTransaction();

            transaction.add(R.id.fragment_container, accountFragment, "account").hide(accountFragment);
            transaction.add(R.id.fragment_container, manageAssetFragment, "manageasset").hide(manageAssetFragment); // 🔥 Tag là manageasset
            transaction.add(R.id.fragment_container, manageResidentFragment, "manageresidents").hide(manageResidentFragment);
            transaction.add(R.id.fragment_container, homeFragmentAgency, "home");

            transaction.commitNow();

            activeFragment = homeFragmentAgency;
            currentSelectedItemId = R.id.nav_home;
        } else {
            currentSelectedItemId = savedInstanceState.getInt(SELECTED_ITEM_ID_KEY, R.id.nav_home);
            String activeTag = savedInstanceState.getString(ACTIVE_FRAGMENT_TAG_KEY, "home");

            homeFragmentAgency = (HomeFragment_Agency) fragmentManager.findFragmentByTag("home");
            manageResidentFragment = (ManageResidentFragment) fragmentManager.findFragmentByTag("manageresidents");
            manageAssetFragment = (ManageAssetFragment) fragmentManager.findFragmentByTag("manageasset"); // 🔥 Tìm theo tag manageasset
            accountFragment = (AccountFragment) fragmentManager.findFragmentByTag("account");

            if (homeFragmentAgency == null) homeFragmentAgency = new HomeFragment_Agency();
            if (manageResidentFragment == null) manageResidentFragment = new ManageResidentFragment();
            if (manageAssetFragment == null) manageAssetFragment = new ManageAssetFragment(); // 🔥
            if (accountFragment == null) accountFragment = new AccountFragment();

            switch (activeTag) {
                case "manageresidents": activeFragment = manageResidentFragment; break;
                case "manageasset": activeFragment = manageAssetFragment; break; // 🔥
                case "account": activeFragment = accountFragment; break;
                case "home":
                default: activeFragment = homeFragmentAgency; break;
            }
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment targetFragment = null;
            String targetTag = "";

            if (itemId == R.id.nav_home) {
                targetFragment = homeFragmentAgency;
                targetTag = "home";
            } else if (itemId == R.id.nav_manage_residents) {
                targetFragment = manageResidentFragment;
                targetTag = "manageresidents";
            } else if (itemId == R.id.nav_manage_asset) { // 🔥 ID menu mới cho Asset
                targetFragment = manageAssetFragment;
                targetTag = "manageasset";
            } else if (itemId == R.id.nav_profile) {
                targetFragment = accountFragment;
                targetTag = "account";
            }

            if (targetFragment != null) {
                switchFragment(targetFragment, itemId);
                return true;
            }
            return false;
        });

        bottomNav.setSelectedItemId(currentSelectedItemId);
    }

    private void switchFragment(Fragment targetFragment, int itemId) {
        if (targetFragment == activeFragment) return;

        FragmentTransaction transaction = fragmentManager.beginTransaction();

        if (!targetFragment.isAdded()) {
            String tag = getTagForFragment(targetFragment);
            transaction.add(R.id.fragment_container, targetFragment, tag);
        }

        transaction.hide(activeFragment).show(targetFragment);

        activeFragment = targetFragment;
        currentSelectedItemId = itemId;
        transaction.commit();
    }

    private String getTagForFragment(Fragment fragment) {
        if (fragment == homeFragmentAgency) return "home";
        if (fragment == manageResidentFragment) return "manageresidents";
        if (fragment == manageAssetFragment) return "manageasset"; // 🔥
        if (fragment == accountFragment) return "account";
        return "";
    }

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
                    .setMessage("Bạn cần bật thông báo để nhận tin tức quan trọng.")
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
        FirebaseMessaging.getInstance().subscribeToTopic("agencies");

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

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SELECTED_ITEM_ID_KEY, currentSelectedItemId);
        if (activeFragment != null && activeFragment.getTag() != null) {
            outState.putString(ACTIVE_FRAGMENT_TAG_KEY, activeFragment.getTag());
        } else {
            outState.putString(ACTIVE_FRAGMENT_TAG_KEY, "home");
        }
    }

    public void switchToManageResidentsTab() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.nav_manage_residents);
    }

    // 🔥 Đã đổi tên hàm và ID để chuyển sang tab Tài sản
    public void switchToManageAssetTab() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.nav_manage_asset);
    }
}