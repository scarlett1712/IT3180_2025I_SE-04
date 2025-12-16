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
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.se_04.enoti.R;
import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.home.accountant.MainActivity_Accountant;
import com.se_04.enoti.home.admin.MainActivity_Admin;
import com.se_04.enoti.home.agency.MainActivity_Agency;
import com.se_04.enoti.home.user.MainActivity_User;
import com.se_04.enoti.utils.ApiConfig; // 🔥 Ensure this exists and has BASE_URL
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends BaseActivity {

    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> installPermissionLauncher;

    private static final String UPDATE_FILE_NAME = "enoti_update.apk";
    private Handler mainThreadHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        mainThreadHandler = new Handler(Looper.getMainLooper());

        installPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (getPackageManager().canRequestPackageInstalls()) {
                            proceedWithInstall();
                        } else {
                            Toast.makeText(this, "Bạn đã từ chối quyền cài đặt. Vui lòng cập nhật thủ công.", Toast.LENGTH_LONG).show();
                            navigateNext();
                        }
                    }
                });

        new Thread(() -> cleanupOldApk(this)).start();

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
                        checkUpdateFromBackend(); // 🔥 CHANGED to call Backend
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
            if (hasRequiredPermissions()) {
                checkUpdateFromBackend(); // 🔥 CHANGED to call Backend
            } else {
                requestAppPermissions();
            }
        }
    }

    private boolean hasRequiredPermissions() {
        boolean cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean imageGranted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            imageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            imageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return cameraGranted && imageGranted;
    }


    // -----------------------------
    // 🔥 CHECK UPDATE FROM BACKEND (MODIFIED)
    // -----------------------------
    private void checkUpdateFromBackend() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                // 🔥 Use your Backend URL instead of GitHub API directly
                String url = ApiConfig.BASE_URL + "/api/app-update/latest-version";

                Request request = new Request.Builder()
                        .url(url)
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    mainThreadHandler.post(this::navigateNext);
                    return;
                }

                String json = response.body().string();
                JSONObject obj = new JSONObject(json);

                // Backend returns: { "version": "v1.0.2", "download_url": "...", "release_notes": "..." }
                String latestVersion = obj.optString("version", "").replace("v", "");
                String releaseNotes = obj.optString("release_notes", "Cập nhật mới");
                String apkUrl = obj.optString("download_url", "");

                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                String currentVersion = pInfo.versionName;

                if (!latestVersion.isEmpty() && !apkUrl.isEmpty() && isNewer(latestVersion, currentVersion)) {
                    mainThreadHandler.post(() -> showUpdateDialog(apkUrl, latestVersion, releaseNotes));
                } else {
                    mainThreadHandler.post(this::navigateNext);
                }

            } catch (Exception e) {
                e.printStackTrace();
                mainThreadHandler.post(this::navigateNext);
            }
        }).start();
    }

    private boolean isNewer(String latest, String current) {
        try {
            String[] l = latest.split("\\.");
            String[] c = current.split("\\.");
            int len = Math.max(l.length, c.length);

            for (int i = 0; i < len; i++) {
                int lv = i < l.length ? Integer.parseInt(l[i]) : 0;
                int cv = i < c.length ? Integer.parseInt(c[i]) : 0;
                if (lv > cv) return true;
                if (lv < cv) return false;
            }
        } catch (NumberFormatException e) {
            return false; // Version parsing failed
        }
        return false;
    }

    // -----------------------------
    // 🔥 DOWNLOAD & INSTALL (Kept exactly as you wrote it)
    // -----------------------------

    private void showUpdateDialog(String url, String version, String releaseNotes) {
        String notes = releaseNotes.replace("\r\n", "\n");

        new AlertDialog.Builder(this)
                .setTitle("Có bản cập nhật mới")
                .setMessage("Phiên bản mới: v" + version + "\n\nNội dung cập nhật:\n" + notes)
                .setPositiveButton("Cập nhật", (d, w) -> downloadAndInstall(url))
                .setNegativeButton("Sau", (d, w) -> navigateNext())
                .setCancelable(false)
                .show();
    }

    private void downloadAndInstall(String url) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Đang tải cập nhật...");

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        progressBar.setProgress(0);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.addView(progressBar);

        builder.setView(layout);
        builder.setCancelable(false);
        AlertDialog progressDialog = builder.show();

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                ResponseBody body = response.body();
                if (body == null) throw new IOException("Lỗi: Body rỗng");

                long totalBytes = body.contentLength();
                File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_FILE_NAME);

                InputStream input = body.byteStream();
                OutputStream output = new FileOutputStream(file);

                byte[] data = new byte[4096];
                long bytesRead = 0;
                int count;

                while ((count = input.read(data)) != -1) {
                    bytesRead += count;
                    output.write(data, 0, count);

                    int progress = (totalBytes > 0) ? (int) ((bytesRead * 100) / totalBytes) : 0;
                    mainThreadHandler.post(() -> progressBar.setProgress(progress));
                }

                output.flush();
                output.close();
                input.close();

                mainThreadHandler.post(() -> {
                    progressDialog.dismiss();
                    triggerInstall();
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainThreadHandler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Tải về thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    navigateNext();
                });
            }
        }).start();
    }

    private void triggerInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(this)
                        .setTitle("Cần cấp quyền cài đặt")
                        .setMessage("Để cài đặt bản cập nhật, bạn cần cấp quyền cho ứng dụng ENoti.")
                        .setPositiveButton("Đến Cài đặt", (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName()));
                            installPermissionLauncher.launch(intent);
                        })
                        .setNegativeButton("Hủy", (d, w) -> navigateNext())
                        .show();
                return;
            }
        }
        proceedWithInstall();
    }

    private void proceedWithInstall() {
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_FILE_NAME);
        if (file.exists()) {
            Uri apkUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider",
                    file);

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                startActivity(installIntent);
                finish();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Không thể mở trình cài đặt.", Toast.LENGTH_LONG).show();
                navigateNext();
            }
        } else {
            Toast.makeText(this, "Không tìm thấy file APK đã tải.", Toast.LENGTH_LONG).show();
            navigateNext();
        }
    }


    private void cleanupOldApk(Context context) {
        try {
            File downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir == null || !downloadDir.isDirectory()) {
                return;
            }
            File apkFile = new File(downloadDir, UPDATE_FILE_NAME);
            if (apkFile.exists()) {
                if (apkFile.delete()) {
                    System.out.println("✅ Đã dọn dẹp file APK cũ thành công.");
                } else {
                    System.err.println("❌ Không thể xóa file APK cũ.");
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi dọn dẹp APK: " + e.getMessage());
        }
    }

    // -----------------------------
    //  PERMISSIONS & NAVIGATION (Kept exactly as is)
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

        if (!userManager.isLoggedIn()) {
            startActivity(new Intent(this, LogInActivity.class));
            finish();
            return;
        }

        String token = userManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            userManager.forceLogout();
            return;
        }

        UserItem user = userManager.getCurrentUser();
        if (user == null) {
            userManager.forceLogout();
            return;
        }

        Intent intent;
        Role userRole = user.getRole();

        if (userRole == Role.ADMIN) {
            intent = new Intent(this, MainActivity_Admin.class);
        } else if (userRole == Role.ACCOUNTANT) {
            intent = new Intent(this, MainActivity_Accountant.class);
        } else if (userRole == Role.AGENCY) {
            intent = new Intent(this, MainActivity_Agency.class);
        } else {
            intent = new Intent(this, MainActivity_User.class);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}