package com.se_04.enoti.finance;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View; // Import thêm View
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;

import java.text.DecimalFormat;
import java.util.Objects; // Import thêm Objects

public class FinanceDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_detail);

        // --- View Binding ---
        TextView txtReceiptTitle = findViewById(R.id.txtReceiptTitle);
        TextView txtReceiptDeadline = findViewById(R.id.txtReceiptDeadline);
        TextView txtPrice = findViewById(R.id.txtPrice);
        TextView txtDetailContent = findViewById(R.id.txtDetailContent);
        TextView txtSender = findViewById(R.id.txtSender);
        Button btnPay = findViewById(R.id.buttonPay);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        TextView txtPaymentStatus = findViewById(R.id.txtPaymentStatus);

        // --- Toolbar Setup ---
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.finance_detail_title);
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        // --- Data Handling ---
        Intent intent = getIntent();
        final String title = intent.getStringExtra("title") != null ? intent.getStringExtra("title") : getString(R.string.no_data);
        String content = intent.getStringExtra("content") != null ? intent.getStringExtra("content") : getString(R.string.no_content_detail);
        String date = intent.getStringExtra("due_date") != null ? intent.getStringExtra("due_date") : "N/A";
        String sender = intent.getStringExtra("sender") != null ? intent.getStringExtra("sender") : "N/A";
        final long price = intent.getLongExtra("price", 0L);

        // ✅ Nhận trạng thái thanh toán từ Intent
        final String paymentStatus = intent.getStringExtra("payment_status");

        // --- Display Data ---
        txtReceiptTitle.setText(title);
        txtReceiptDeadline.setText(getString(R.string.deadline_format, date));
        txtSender.setText(getString(R.string.collector_format, sender));
        txtDetailContent.setText(content);

        if (price > 0) {
            DecimalFormat formatter = new DecimalFormat("#,###,###");
            String formattedPrice = formatter.format(price) + " đ";
            txtPrice.setText(formattedPrice);
        } else {
            txtPrice.setText(R.string.contribution_text);
        }

        // Giả sử trạng thái "Đã thanh toán" được định nghĩa là "paid" hoặc "Đã thanh toán"
        if (Objects.equals(paymentStatus, "paid") || Objects.equals(paymentStatus, "da_thanh_toan")) {
            // Nếu đã thanh toán
            btnPay.setVisibility(View.GONE); // Ẩn nút "Thanh toán"
            txtPaymentStatus.setVisibility(View.VISIBLE); // Hiện thông báo "Đã thanh toán"
        } else {
            // Nếu chưa thanh toán
            btnPay.setVisibility(View.VISIBLE); // Hiện nút "Thanh toán"
            txtPaymentStatus.setVisibility(View.GONE); // Ẩn thông báo

            // Logic của nút thanh toán chỉ cần thiết khi chưa thanh toán
            btnPay.setOnClickListener(v -> {
                Intent payIntent = new Intent(FinanceDetailActivity.this, PayActivity.class);
                payIntent.putExtra("title", title);
                payIntent.putExtra("price", price);
                // isMandatory có thể được xác định từ price
                boolean isMandatory = (price > 0);
                payIntent.putExtra("is_mandatory", isMandatory);
                startActivity(payIntent);
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
