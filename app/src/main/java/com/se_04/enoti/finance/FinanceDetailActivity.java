package com.se_04.enoti.finance;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.Objects;

public class FinanceDetailActivity extends AppCompatActivity {

    private int financeId;
    private Button btnPay;
    private TextView txtPaymentStatus;

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
        btnPay = findViewById(R.id.buttonPay);
        txtPaymentStatus = findViewById(R.id.txtPaymentStatus);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        // --- Toolbar Setup ---
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.finance_detail_title);
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        // --- Get Intent Data ---
        Intent intent = getIntent();
        financeId = intent.getIntExtra("financeId", -1);

        final String title = intent.getStringExtra("title");
        final String content = intent.getStringExtra("content");
        final String dueDate = intent.getStringExtra("due_date");
        final String sender = intent.getStringExtra("sender");
        final long price = intent.getLongExtra("price", 0L);
        final String paymentStatus = intent.getStringExtra("payment_status");

        txtReceiptTitle.setText(title);
        txtReceiptDeadline.setText("Hạn: " + dueDate);
        txtSender.setText("Người gửi: " + sender);
        txtDetailContent.setText(content);

        if (price > 0) {
            txtPrice.setText(new DecimalFormat("#,###,###").format(price) + " đ");
        } else {
            txtPrice.setText("Khoản tự nguyện");
        }

        // --- UI Thanh toán ban đầu ---
        if (Objects.equals(paymentStatus, "da_thanh_toan")) {
            btnPay.setVisibility(View.GONE);
            txtPaymentStatus.setVisibility(View.VISIBLE);
        } else {
            btnPay.setVisibility(View.VISIBLE);
            txtPaymentStatus.setVisibility(View.GONE);

            btnPay.setOnClickListener(v -> {
                Intent payIntent = new Intent(FinanceDetailActivity.this, PayActivity.class);
                payIntent.putExtra("title", title);
                payIntent.putExtra("price", price);
                payIntent.putExtra("financeId", financeId);
                payIntent.putExtra("is_mandatory", price > 0);
                startActivity(payIntent);
            });
        }

        // --- Handle deep link ---
        handlePayOSDeepLink();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // =======================================
    //        HANDLE DEEP LINK
    // =======================================
    private void handlePayOSDeepLink() {
        Uri data = getIntent().getData();
        if (data == null) return;

        String path = data.getPath(); // /success hoặc /cancel

        if ("/success".equals(path)) {
            Toast.makeText(this, "Thanh toán thành công!", Toast.LENGTH_SHORT).show();
            updatePaymentStatus(true);

        } else if ("/cancel".equals(path)) {
            Toast.makeText(this, "Bạn đã hủy thanh toán", Toast.LENGTH_SHORT).show();
            updatePaymentStatus(false);
        }
    }

    // =======================================
    //        UPDATE PAYMENT STATUS API
    // =======================================
    private void updatePaymentStatus(boolean success) {
        String newStatus = success ? "da_thanh_toan" : "da_huy";

        String userId = UserManager.getInstance(getApplicationContext()).getID();

        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);
            body.put("finance_id", financeId);
            body.put("status", newStatus);
        } catch (Exception ignored) {}

        String url = ApiConfig.BASE_URL + "/finance/user/update-status";

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.PUT, url, body,
                response -> {
                    Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();

                    // Cập nhật UI
                    btnPay.setVisibility(View.GONE);
                    txtPaymentStatus.setVisibility(View.VISIBLE);
                },
                error -> {
                    Toast.makeText(this, "Lỗi API cập nhật", Toast.LENGTH_SHORT).show();
                }
        );

        Volley.newRequestQueue(this).add(req);
    }
}