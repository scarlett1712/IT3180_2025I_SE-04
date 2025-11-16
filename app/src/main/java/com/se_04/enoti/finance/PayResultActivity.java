package com.se_04.enoti.finance;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.se_04.enoti.R;
import com.se_04.enoti.home.user.MainActivity_User;

public class PayResultActivity extends AppCompatActivity {

    private TextView tvStatus;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pay_result);

        tvStatus = findViewById(R.id.tvPayStatus);

        handleDeepLink();
    }

    private void handleDeepLink() {
        Uri data = getIntent().getData();

        if (data == null) {
            tvStatus.setText("Không nhận được kết quả thanh toán");
            return;
        }

        String path = data.getPath();  // "/success" hoặc "/cancel"

        if ("/success".equals(path)) {
            tvStatus.setText("Thanh toán thành công!");
            Toast.makeText(this, "Thanh toán thành công!", Toast.LENGTH_SHORT).show();
        }
        else if ("/cancel".equals(path)) {
            tvStatus.setText("Bạn đã hủy thanh toán.");
            Toast.makeText(this, "Thanh toán bị hủy!", Toast.LENGTH_SHORT).show();
        }
        else {
            tvStatus.setText("Không rõ trạng thái thanh toán.");
        }

        // Tự quay về app sau 2 giây
        tvStatus.postDelayed(() -> {
            Intent intent = new Intent(this, MainActivity_User.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }, 2000);
    }
}
