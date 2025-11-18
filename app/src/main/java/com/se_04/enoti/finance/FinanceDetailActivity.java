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
import com.se_04.enoti.utils.VnNumberToWords;

import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.Objects;

public class FinanceDetailActivity extends AppCompatActivity {

    private int financeId;
    private long price;
    private String title, content, dueDate, sender, paymentStatus;

    private Button btnPay;
    private TextView txtPaymentStatus;

    // INVOICE UI
    // 🔥 1. Thêm txtPayDate vào danh sách biến
    private TextView txtOrderCode, txtAmount, txtAmountInText, txtDetail, txtPayDate;

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

        // Invoice UI
        txtOrderCode = findViewById(R.id.txtOrderCode);
        txtAmount = findViewById(R.id.txtAmount);
        txtAmountInText = findViewById(R.id.txtAmountInText);
        txtDetail = findViewById(R.id.txtDetail);
        // 🔥 2. Ánh xạ view mới từ layout
        txtPayDate = findViewById(R.id.txtPayDate);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        // Toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.finance_detail_title);
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        // Intent data
        readIntentData(getIntent());

        txtReceiptTitle.setText(title);
        txtReceiptDeadline.setText("Hạn: " + dueDate);
        txtSender.setText("Người gửi: " + sender);
        txtDetailContent.setText(content);

        if (price > 0) {
            txtPrice.setText(new DecimalFormat("#,###,###").format(price) + " đ");
        } else {
            txtPrice.setText("Khoản tự nguyện");
        }

        updatePaymentUI();

        // Nếu đã thanh toán → tự động load invoice
        if (Objects.equals(paymentStatus, "da_thanh_toan")) {
            fetchInvoice();
        }

        btnPay.setOnClickListener(v -> {
            Intent payIntent = new Intent(FinanceDetailActivity.this, PayActivity.class);
            payIntent.putExtra("title", title);
            payIntent.putExtra("price", price);
            payIntent.putExtra("financeId", financeId);
            payIntent.putExtra("is_mandatory", price > 0);
            startActivity(payIntent);
        });
    }

    // ------------------------ PAYOS DEEP LINK -----------------------------
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handlePayOSDeepLink(intent);
    }

    private void readIntentData(Intent intent) {
        financeId = intent.getIntExtra("financeId", -1);
        title = intent.getStringExtra("title");
        content = intent.getStringExtra("content");
        dueDate = intent.getStringExtra("due_date");
        sender = intent.getStringExtra("sender");
        price = intent.getLongExtra("price", 0L);
        paymentStatus = intent.getStringExtra("payment_status");
    }

    private void handlePayOSDeepLink(Intent intent) {
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) return;

        Uri data = intent.getData();
        if (data == null) return;

        String path = data.getPath();
        if (path == null) return;

        if (path.contains("success")) {
            Toast.makeText(this, "Thanh toán thành công!", Toast.LENGTH_SHORT).show();
            updatePaymentStatus(true);

        } else if (path.contains("cancel")) {
            Toast.makeText(this, "Bạn đã hủy thanh toán", Toast.LENGTH_SHORT).show();
            updatePaymentStatus(false);
        }
    }

    // ------------------------- UI STATUS -------------------------------
    private void updatePaymentUI() {
        if (Objects.equals(paymentStatus, "da_thanh_toan")) {
            btnPay.setVisibility(View.GONE);
            txtPaymentStatus.setVisibility(View.VISIBLE);
            findViewById(R.id.invoiceDetail).setVisibility(View.VISIBLE);

        } else {
            btnPay.setVisibility(View.VISIBLE);
            txtPaymentStatus.setVisibility(View.GONE);
            findViewById(R.id.invoiceDetail).setVisibility(View.GONE);
        }
    }

    // ----------------------- UPDATE PAYMENT STATUS ---------------------
    private void updatePaymentStatus(boolean success) {
        String newStatus = success ? "da_thanh_toan" : "da_huy";
        paymentStatus = newStatus;

        updatePaymentUI();

        int userId = Integer.parseInt(
                UserManager.getInstance(getApplicationContext()).getID()
        );

        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);
            body.put("finance_id", financeId);
            body.put("status", newStatus);
        } catch (Exception ignored) {}

        String url = ApiConfig.BASE_URL + "/api/finance/user/update-status";

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.PUT, url, body,
                response -> {
                    if (success) {
                        // Thêm delay nhỏ để đảm bảo DB bên server (Invoice) đã commit transaction xong
                        new android.os.Handler().postDelayed(this::fetchInvoice, 500);
                    }
                },
                error -> Toast.makeText(this, "Lỗi API cập nhật", Toast.LENGTH_SHORT).show()
        );

        Volley.newRequestQueue(this).add(req);
    }

    // -------------------------- FETCH INVOICE ---------------------------
    private void fetchInvoice() {
        String url = ApiConfig.BASE_URL + "/api/invoice/by-finance/" + financeId;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        String ordercode = response.getString("ordercode");
                        long amount = response.getLong("amount");
                        String desc = response.getString("description");

                        // 🔥 3. Lấy thời gian từ JSON (đã được server format)
                        String payTime = response.optString("pay_time_formatted", "");

                        txtOrderCode.setText(ordercode);
                        txtAmount.setText(new DecimalFormat("#,###,###").format(amount) + " đ");
                        txtAmountInText.setText(convertNumberToWords(amount));
                        txtDetail.setText(desc);

                        // 🔥 4. Hiển thị thời gian
                        txtPayDate.setText(payTime.isEmpty() ? "Vừa xong" : payTime);

                        findViewById(R.id.invoiceDetail).setVisibility(View.VISIBLE);

                    } catch (Exception ignored) {}
                },
                error -> Toast.makeText(this, "Không lấy được hóa đơn!", Toast.LENGTH_SHORT).show()
        );

        Volley.newRequestQueue(this).add(request);
    }

    // ----------------------- CHUYỂN SỐ → CHỮ ---------------------------
    private String convertNumberToWords(long number) {
        return VnNumberToWords.convert(number);
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