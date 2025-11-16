package com.se_04.enoti.account_related;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.home.admin.MainActivity_Admin;
import com.se_04.enoti.home.user.MainActivity_User;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);

        // --- XIN QUYỀN NHƯ BẢN GỐC ---
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean cameraGranted = Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA));
                    boolean imageGranted = false;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        imageGranted = Boolean.TRUE.equals(result.get(Manifest.permission.READ_MEDIA_IMAGES));
                    } else {
                        imageGranted = Boolean.TRUE.equals(result.get(Manifest.permission.READ_EXTERNAL_STORAGE));
                    }

                    if (cameraGranted && imageGranted) {
                        checkUpdateFromGitHub();
                    } else {
                        if (isPermissionPermanentlyDenied()) {
                            showPermissionSettingsDialog();
                        } else {
                            Toast.makeText(this, "Bạn cần cấp quyền Ảnh và Máy ảnh.", Toast.LENGTH_LONG).show();
                            requestAppPermissions();
                        }
                    }
                }
        );

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean firstRun = prefs.getBoolean("first_run", true);

        if (firstRun) {
            requestAppPermissions();
            prefs.edit().putBoolean("first_run", false).apply();
        } else {
            checkUpdateFromGitHub();
        }
    }

    // -----------------------------
    // 🔥 PHẦN MỚI: CHECK UPDATE
    // -----------------------------
    private void checkUpdateFromGitHub() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                Request request = new Request.Builder()
                        .url("https://api.github.com/repos/scarlett1712/IT3180_2025I_SE-04/releases/latest")
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    runOnUiThread(this::navigateNext);
                    return;
                }

                String json = response.body().string();
                JSONObject obj = new JSONObject(json);

                String latestVersion = obj.getString("tag_name").replace("v", "");

                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                String currentVersion = pInfo.versionName;

                if (isNewer(latestVersion, currentVersion)) {
                    JSONArray assets = obj.getJSONArray("assets");
                    if (assets.length() > 0) {
                        String apkUrl = assets.getJSONObject(0).getString("browser_download_url");
                        runOnUiThread(() -> showUpdateDialog(apkUrl, latestVersion));
                    }
                } else {
                    runOnUiThread(this::navigateNext);
                }

            } catch (Exception e) {
                runOnUiThread(this::navigateNext);
            }
        }).start();
    }

    // So sánh version "1.0.5" > "1.0.3"
    private boolean isNewer(String latest, String current) {
        String[] l = latest.split("\\.");
        String[] c = current.split("\\.");
        int len = Math.max(l.length, c.length);

        for (int i = 0; i < len; i++) {
            int lv = i < l.length ? Integer.parseInt(l[i]) : 0;
            int cv = i < c.length ? Integer.parseInt(c[i]) : 0;
            if (lv > cv) return true;
            if (lv < cv) return false;
        }
        return false;
    }

    private void showUpdateDialog(String url, String version) {
        new AlertDialog.Builder(this)
                .setTitle("Có bản cập nhật mới")
                .setMessage("Phiên bản mới: v" + version + "\nBạn có muốn tải và cập nhật không?")
                .setPositiveButton("Cập nhật", (d, w) -> downloadApk(url))
                .setNegativeButton("Sau", (d, w) -> navigateNext())
                .show();
    }

    private void downloadApk(String url) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("Đang tải bản cập nhật");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "enoti_update.apk");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        dm.enqueue(request);

        Toast.makeText(this, "Đang tải cập nhật… Sau khi tải xong hãy mở file để cài đặt.", Toast.LENGTH_LONG).show();
    }

    // -----------------------------
    //  QUYỀN GIỐNG BẢN GỐC
    // -----------------------------
    private void requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.POST_NOTIFICATIONS
            });
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        }
    }

    private boolean isPermissionPermanentlyDenied() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                    || (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED
                    && !shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES));
        } else {
            return (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                    || (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    && !shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE));
        }
    }

    private void showPermissionSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Cần cấp quyền")
                .setMessage("Vui lòng mở Cài đặt và cấp lại quyền.")
                .setPositiveButton("Mở cài đặt", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Hủy", (d, w) -> navigateNext())
                .show();
    }

    private void navigateNext() {
        UserManager userManager = UserManager.getInstance(this);
        UserItem user = userManager.getCurrentUser();

        if (userManager.isLoggedIn() && user != null) {
            if (user.getRole() == Role.ADMIN) {
                startActivity(new Intent(this, MainActivity_Admin.class));
            } else {
                startActivity(new Intent(this, MainActivity_User.class));
            }
        } else {
            startActivity(new Intent(this, LogInActivity.class));
        }

        finish();
    }
}