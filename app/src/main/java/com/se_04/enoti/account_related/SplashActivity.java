package com.se_04.enoti.account_related;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.home.admin.MainActivity_Admin;
import com.se_04.enoti.home.user.MainActivity_User;
import com.se_04.enoti.utils.UserManager;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                        Toast.makeText(this, "Quyền đã được cấp đầy đủ!", Toast.LENGTH_SHORT).show();
                        navigateNext();
                    } else {
                        // Kiểm tra nếu người dùng đã chọn "Không hỏi lại nữa"
                        if (isPermissionPermanentlyDenied()) {
                            showPermissionSettingsDialog();
                        } else {
                            Toast.makeText(this, "Bạn cần cấp quyền Ảnh và Máy ảnh để sử dụng ứng dụng.", Toast.LENGTH_LONG).show();
                            requestAppPermissions();
                        }
                    }
                });

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean firstRun = prefs.getBoolean("first_run", true);

        if (firstRun) {
            requestAppPermissions();
            prefs.edit().putBoolean("first_run", false).apply();
        } else {
            navigateNext();
        }
    }

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
        // Kiểm tra xem quyền đã bị từ chối vĩnh viễn (Không hỏi lại nữa)
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
                .setTitle("Cần cấp quyền thủ công")
                .setMessage("Một số quyền cần thiết đã bị tắt vĩnh viễn. Vui lòng mở Cài đặt để cấp lại quyền cho ứng dụng.")
                .setPositiveButton("Mở cài đặt", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Hủy", (dialog, which) -> {
                    Toast.makeText(this, "Ứng dụng có thể không hoạt động đầy đủ nếu thiếu quyền.", Toast.LENGTH_LONG).show();
                    navigateNext();
                })
                .setCancelable(false)
                .show();
    }

    private void navigateNext() {
        UserManager userManager = UserManager.getInstance(this);
        UserItem user = userManager.getCurrentUser();

        if (userManager.isLoggedIn() && user != null) {
            android.util.Log.d("SPLASH", "Current user role: " + user.getRole());

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