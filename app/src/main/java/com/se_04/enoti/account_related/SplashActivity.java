package com.se_04.enoti.account_related;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager; // Giữ lại để dùng hằng số Environment.DIRECTORY_DOWNLOADS
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
import androidx.core.content.FileProvider; // 🔥 THÊM MỚI

import com.se_04.enoti.R; // 🔥 Cần R.layout.dialog_progress (Tôi sẽ giả định bạn tự tạo)
import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.home.admin.MainActivity_Admin;
import com.se_04.enoti.home.user.MainActivity_User;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream; // 🔥 THÊM MỚI
import java.io.InputStream; // 🔥 THÊM MỚI
import java.io.OutputStream; // 🔥 THÊM MỚI
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody; // 🔥 THÊM MỚI

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends BaseActivity {

    private ActivityResultLauncher<String[]> permissionLauncher;
    // 🔥 THÊM MỚI: Launcher để xin quyền "Cài đặt ứng dụng không rõ nguồn gốc"
    private ActivityResultLauncher<Intent> installPermissionLauncher;

    private static final String UPDATE_FILE_NAME = "enoti_update.apk";
    private Handler mainThreadHandler; // 🔥 THÊM MỚI: Để cập nhật UI từ luồng nền

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        mainThreadHandler = new Handler(Looper.getMainLooper());

        // 🔥 THÊM MỚI: Đăng ký launcher cho quyền cài đặt
        installPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Sau khi người dùng quay lại từ màn hình Cài đặt
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (getPackageManager().canRequestPackageInstalls()) {
                            proceedWithInstall(); // Nếu họ đã cấp quyền, tiến hành cài đặt
                        } else {
                            Toast.makeText(this, "Bạn đã từ chối quyền cài đặt. Vui lòng cập nhật thủ công.", Toast.LENGTH_LONG).show();
                            navigateNext(); // Đi tiếp vào app
                        }
                    }
                });

        // 🔥 THÊM MỚI: Chạy logic dọn dẹp ở background
        new Thread(() -> cleanupOldApk(this)).start();

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
            if (hasRequiredPermissions()) {
                checkUpdateFromGitHub();
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
    // 🔥 CHECK UPDATE (Đã sửa)
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
                    mainThreadHandler.post(this::navigateNext);
                    return;
                }

                String json = response.body().string();
                JSONObject obj = new JSONObject(json);

                String latestVersion = obj.getString("tag_name").replace("v", "");
                // 🔥 THÊM MỚI: Lấy nội dung Release Notes
                String releaseNotes = obj.getString("body");

                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                String currentVersion = pInfo.versionName;

                if (isNewer(latestVersion, currentVersion)) {
                    JSONArray assets = obj.getJSONArray("assets");
                    if (assets.length() > 0) {
                        // Lấy link APK (giả sử là asset đầu tiên, hoặc bạn có thể lặp để tìm .apk)
                        String apkUrl = assets.getJSONObject(0).getString("browser_download_url");

                        // 🔥 THAY ĐỔI: Gửi tất cả thông tin sang dialog
                        mainThreadHandler.post(() -> showUpdateDialog(apkUrl, latestVersion, releaseNotes));
                    }
                } else {
                    mainThreadHandler.post(this::navigateNext);
                }

            } catch (Exception e) {
                mainThreadHandler.post(this::navigateNext);
            }
        }).start();
    }

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

    // -----------------------------
    // 🔥 HÀM TẢI VỀ (Đã viết lại hoàn toàn)
    // -----------------------------

    private void showUpdateDialog(String url, String version, String releaseNotes) {
        // Thay thế \r\n (từ Markdown) bằng \n (cho TextView)
        String notes = releaseNotes.replace("\r\n", "\n");

        new AlertDialog.Builder(this)
                .setTitle("Có bản cập nhật mới")
                .setMessage("Phiên bản mới: v" + version + "\n\nNội dung cập nhật:\n" + notes)
                .setPositiveButton("Cập nhật", (d, w) -> downloadAndInstall(url)) // Chuyển sang hàm tải mới
                .setNegativeButton("Sau", (d, w) -> navigateNext())
                .setCancelable(false)
                .show();
    }

    // 🔥 HÀM MỚI: Tải về thủ công bằng OkHttp để hiển thị ProgressBar
    private void downloadAndInstall(String url) {
        // 1. Tạo Dialog với ProgressBar
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Đang tải cập nhật...");

        // Tạo ProgressBar theo chương trình
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        progressBar.setProgress(0);

        // Thêm ProgressBar vào một layout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.addView(progressBar);

        builder.setView(layout);
        builder.setCancelable(false);
        AlertDialog progressDialog = builder.show();

        // 2. Bắt đầu tải trên luồng nền
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

                    // Tính toán % và cập nhật ProgressBar
                    int progress = (int) ((bytesRead * 100) / totalBytes);
                    mainThreadHandler.post(() -> progressBar.setProgress(progress));
                }

                output.flush();
                output.close();
                input.close();

                // 3. Tải xong, đóng dialog và kích hoạt cài đặt
                mainThreadHandler.post(() -> {
                    progressDialog.dismiss();
                    triggerInstall(); // Kích hoạt cài đặt
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainThreadHandler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Tải về thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    navigateNext(); // Đi tiếp nếu tải lỗi
                });
            }
        }).start();
    }

    // 🔥 HÀM MỚI: Kích hoạt cài đặt (kiểm tra quyền)
    private void triggerInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Với Android 8+, cần quyền "Cài đặt ứng dụng không rõ nguồn gốc"
            if (!getPackageManager().canRequestPackageInstalls()) {
                // Hiển thị dialog giải thích
                new AlertDialog.Builder(this)
                        .setTitle("Cần cấp quyền cài đặt")
                        .setMessage("Để cài đặt bản cập nhật, bạn cần cấp quyền cho ENoti.")
                        .setPositiveButton("Đến Cài đặt", (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName()));
                            installPermissionLauncher.launch(intent); // Mở màn hình Cài đặt
                        })
                        .setNegativeButton("Hủy", (d, w) -> navigateNext())
                        .show();
                return;
            }
        }
        // Nếu đã có quyền (hoặc < Android 8), tiến hành cài đặt
        proceedWithInstall();
    }

    // 🔥 HÀM MỚI: Tiến hành cài đặt (sử dụng FileProvider)
    private void proceedWithInstall() {
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_FILE_NAME);
        if (file.exists()) {
            // BẮT BUỘC: Sử dụng FileProvider để lấy Uri
            // (Hãy chắc chắn bạn đã khai báo provider trong Manifest)
            Uri apkUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", // Đây là authority, phải khớp Manifest
                    file);

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                startActivity(installIntent);
                finish(); // Đóng SplashActivity khi mở trình cài đặt
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


    // 🔥 Hàm dọn dẹp (Giữ nguyên)
    private void cleanupOldApk(Context context) {
        // ... (Code gốc của bạn giữ nguyên) ...
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
    //  QUYỀN VÀ ĐIỀU HƯỚNG (Code gốc)
    // -----------------------------
    private void requestAppPermissions() {
        // ... (Code gốc của bạn giữ nguyên)
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
        // ... (Code gốc của bạn giữ nguyên)
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
        // ... (Code gốc của bạn giữ nguyên)
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
        // ... (Code gốc của bạn giữ nguyên)
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