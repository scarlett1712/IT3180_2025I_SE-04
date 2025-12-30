package com.se_04.enoti.finance;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;
import com.se_04.enoti.utils.VnNumberToWords;

import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class FinanceDetailActivity extends BaseActivity {

    private static final String TAG = "FinanceDetailActivity";

    // Data Variables
    private int financeId;
    private long price;
    private String title, content, dueDate, sender, paymentStatus;

    // UI Variables
    private Button btnPay;
    private TextView txtPaymentStatus;

    // Invoice Detail UI (Ẩn/Hiện)
    private View invoiceDetailView;
    private TextView txtOrderCode, txtAmount, txtAmountInText, txtDetail, txtPayDate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_detail);

        initViews();
        setupToolbar();

        // 1. Lấy dữ liệu từ Intent
        readIntentData(getIntent());

        // 2. Điền thông tin cơ bản lên màn hình
        fillBasicData();

        Log.d(TAG, "📋 onCreate - financeId: " + financeId + ", paymentStatus: " + paymentStatus);

        // 3. Kiểm tra trạng thái thanh toán từ Server (để quyết định ẩn/hiện nút Pay)
        refreshPaymentStatus();

        // Sự kiện bấm nút thanh toán
        btnPay.setOnClickListener(v -> {
            Intent payIntent = new Intent(FinanceDetailActivity.this, PayActivity.class);
            payIntent.putExtra("title", title);
            payIntent.putExtra("price", price);
            payIntent.putExtra("financeId", financeId);
            payIntent.putExtra("is_mandatory", price > 0);
            startActivity(payIntent);
        });
    }

    private void initViews() {
        btnPay = findViewById(R.id.buttonPay);
        txtPaymentStatus = findViewById(R.id.txtPaymentStatus);

        // Phần chi tiết hóa đơn (Ban đầu ẩn)
        invoiceDetailView = findViewById(R.id.invoiceDetail);
        txtOrderCode = findViewById(R.id.txtOrderCode);
        txtAmount = findViewById(R.id.txtAmount);
        txtAmountInText = findViewById(R.id.txtAmountInText);
        txtDetail = findViewById(R.id.txtDetail);
        txtPayDate = findViewById(R.id.txtPayDate);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.finance_detail_title);
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }
    }

    private void fillBasicData() {
        TextView txtReceiptTitle = findViewById(R.id.txtReceiptTitle);
        TextView txtReceiptDeadline = findViewById(R.id.txtReceiptDeadline);
        TextView txtPrice = findViewById(R.id.txtPrice);
        TextView txtDetailContent = findViewById(R.id.txtDetailContent);
        TextView txtSender = findViewById(R.id.txtSender);

        txtReceiptTitle.setText(title);
        txtReceiptDeadline.setText("Hạn: " + dueDate);
        txtSender.setText(sender);
        txtDetailContent.setText(content);

        if (price > 0) {
            txtPrice.setText(new DecimalFormat("#,###,###").format(price) + " đ");
        } else {
            txtPrice.setText("Khoản tự nguyện");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Khi quay lại từ màn hình thanh toán, check lại trạng thái
        new android.os.Handler().postDelayed(this::refreshPaymentStatus, 500);
    }

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

    // Xử lý khi PayOS trả về kết quả qua DeepLink
    private void handlePayOSDeepLink(Intent intent) {
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri data = intent.getData();
        if (data == null || data.getPath() == null) return;

        if (data.getPath().contains("success")) {
            Toast.makeText(this, "Thanh toán thành công!", Toast.LENGTH_SHORT).show();
            updatePaymentStatus(true);
        } else if (data.getPath().contains("cancel")) {
            Toast.makeText(this, "Bạn đã hủy thanh toán", Toast.LENGTH_SHORT).show();
            updatePaymentStatus(false);
        }
    }

    // Kiểm tra trạng thái thanh toán từ Server
    private void refreshPaymentStatus() {
        int userId;
        try {
            userId = Integer.parseInt(UserManager.getInstance(getApplicationContext()).getID());
        } catch (Exception e) { return; }

        String url = ApiConfig.BASE_URL + "/api/finance/user/payment-status/" + financeId + "?user_id=" + userId;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    String status = response.optString("status", "chua_thanh_toan");
                    paymentStatus = status;
                    updatePaymentUI(); // Cập nhật giao diện (Ẩn nút Pay / Hiện Invoice)

                    // Nếu đã thanh toán -> Gọi API lấy chi tiết hóa đơn
                    if ("da_thanh_toan".equalsIgnoreCase(status)) {
                        new android.os.Handler().postDelayed(this::fetchInvoice, 500);
                    }
                },
                error -> Log.e(TAG, "Error fetching status: " + error.toString())
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }

    // Cập nhật giao diện dựa trên trạng thái thanh toán
    private void updatePaymentUI() {
        if ("da_thanh_toan".equalsIgnoreCase(paymentStatus)) {
            btnPay.setVisibility(View.GONE);
            txtPaymentStatus.setVisibility(View.VISIBLE);
            txtPaymentStatus.setText("✅ Đã thanh toán");
            // Hiện khung chi tiết hóa đơn
            invoiceDetailView.setVisibility(View.VISIBLE);
        } else {
            btnPay.setVisibility(View.VISIBLE);
            txtPaymentStatus.setVisibility(View.GONE);
            invoiceDetailView.setVisibility(View.GONE);
        }
    }

    // Cập nhật trạng thái lên Server (khi PayOS trả về thành công)
    private void updatePaymentStatus(boolean success) {
        String newStatus = success ? "da_thanh_toan" : "da_huy";
        paymentStatus = newStatus;
        updatePaymentUI();

        int userId = Integer.parseInt(UserManager.getInstance(getApplicationContext()).getID());
        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);
            body.put("finance_id", financeId);
            body.put("status", newStatus);
        } catch (Exception ignored) {}

        String url = ApiConfig.BASE_URL + "/api/finance/user/update-status";

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.PUT, url, body,
                response -> {
                    if (success) {
                        // Sau khi update thành công, lấy hóa đơn về hiển thị
                        new android.os.Handler().postDelayed(this::fetchInvoice, 500);
                    }
                },
                error -> Toast.makeText(this, "Lỗi cập nhật trạng thái", Toast.LENGTH_SHORT).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(req);
    }

    // Lấy chi tiết hóa đơn (Mã GD, Thời gian, Số tiền thực)
    private void fetchInvoice() {
        int userId = Integer.parseInt(UserManager.getInstance(getApplicationContext()).getID());
        String url = ApiConfig.BASE_URL + "/api/invoice/by-finance/" + financeId + "?user_id=" + userId;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        String ordercode = response.getString("ordercode");
                        long amount = response.getLong("amount");
                        String desc = response.getString("description");
                        String rawPayTime = response.optString("pay_time_formatted", "Vừa xong");

                        // Điền dữ liệu vào phần Chi tiết giao dịch
                        txtOrderCode.setText(ordercode);
                        txtAmount.setText(new DecimalFormat("#,###,###").format(amount) + " đ");
                        txtAmountInText.setText(VnNumberToWords.convert(amount));
                        txtDetail.setText(desc);
                        txtPayDate.setText(rawPayTime);

                        // Đảm bảo View hiện lên
                        invoiceDetailView.setVisibility(View.VISIBLE);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        // Nếu chưa có hóa đơn (server xử lý chậm), thử lại sau 2s
                        new android.os.Handler().postDelayed(this::fetchInvoice, 2000);
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
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