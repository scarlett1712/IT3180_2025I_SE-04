package com.se_04.enoti.settings;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.se_04.enoti.R;
import com.se_04.enoti.utils.UserManager;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
    }

    private void initViews() {
        // Nút Back
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // 1. Thông báo
        View layoutNotification = findViewById(R.id.layoutNotification);
        layoutNotification.setOnClickListener(v -> {
            Toast.makeText(this, "Cài đặt thông báo", Toast.LENGTH_SHORT).show();
            // TODO: Mở màn hình cài đặt thông báo
        });

        // 2. Ngôn ngữ
        View layoutLanguage = findViewById(R.id.layoutLanguage);
        layoutLanguage.setOnClickListener(v -> {
            Toast.makeText(this, "Thay đổi ngôn ngữ", Toast.LENGTH_SHORT).show();
        });

        // 3. Chế độ tối
        Switch switchDarkMode = findViewById(R.id.switchDarkMode);
        // Load trạng thái đã lưu (nếu có)
        // switchDarkMode.setChecked(isDarkModeOn);
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Toast.makeText(this, "Đã bật chế độ tối", Toast.LENGTH_SHORT).show();
                // TODO: Gọi hàm setNightMode
            } else {
                Toast.makeText(this, "Đã tắt chế độ tối", Toast.LENGTH_SHORT).show();
            }
        });

        // 4. Quyền riêng tư
        View layoutPrivacy = findViewById(R.id.layoutPrivacy);
        layoutPrivacy.setOnClickListener(v -> {
            Toast.makeText(this, "Chính sách quyền riêng tư", Toast.LENGTH_SHORT).show();
        });

        // 5. Hỗ trợ & Liên hệ
        View layoutSupport = findViewById(R.id.layoutSupport);
        layoutSupport.setOnClickListener(v -> {
            Toast.makeText(this, "Liên hệ tổng đài 1900xxxx", Toast.LENGTH_SHORT).show();
        });

        // 6. Thông tin ứng dụng
        View layoutAboutApp = findViewById(R.id.layoutAboutApp);
        layoutAboutApp.setOnClickListener(v -> {
            Toast.makeText(this, "E-Noti App v1.0.0", Toast.LENGTH_SHORT).show();
        });

    }
}