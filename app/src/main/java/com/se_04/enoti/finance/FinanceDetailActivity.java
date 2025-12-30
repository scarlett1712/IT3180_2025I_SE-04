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
    private String title, content, dueDate, sender;
    private String paymentStatus = "chua_thanh_toan";

    // UI Variables
    private Button btnPay;
    private TextView txtPaymentStatus;

    // Invoice Detail UI
    private View invoiceDetailView;
    private TextView txtOrderCode, txtAmount, txtAmountInText, txtDetail, txtPayDate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_detail);

        initViews();
        setupToolbar();
        readIntentData(getIntent());
        fillBasicData();

        // 3. Cập nhật UI ban đầu (tránh lag)
        updatePaymentUI();

        // Sự kiện nút thanh toán
        btnPay.setOnClickListener(v -> {
            Intent payIntent = new Intent(FinanceDetailActivity.this, PayActivity.class);
            payIntent.putExtra("title", title);
            payIntent.putExtra("price", price);
            payIntent.putExtra("financeId", financeId);
            payIntent.putExtra("is_mandatory", price > 0);
            startActivity(payIntent);
        });
    }

    // 🔥 QUAN TRỌNG: Kiểm tra lại trạng thái mỗi khi màn hình hiện lên
    @Override
    protected void onResume() {
        super.onResume();
        // Delay 0.5s để đảm bảo server đã xử lý xong nếu vừa thanh toán
        new android.os.Handler().postDelayed(this::refreshPaymentStatus, 500);
    }

    // Xử lý DeepLink PayOS
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handlePayOSDeepLink(intent);
    }

    private void initViews() {
        btnPay = findViewById(R.id.buttonPay);
        txtPaymentStatus = findViewById(R.id.txtPaymentStatus);

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
            getSupportActionBar().setTitle("Chi tiết khoản thu");
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }
    }

    private void fillBasicData() {
        ((TextView) findViewById(R.id.txtReceiptTitle)).setText(title);
        ((TextView) findViewById(R.id.txtReceiptDeadline)).setText("Hạn: " + dueDate);
        ((TextView) findViewById(R.id.txtSender)).setText(sender);
        ((TextView) findViewById(R.id.txtDetailContent)).setText(content);

        TextView txtPrice = findViewById(R.id.txtPrice);
        if (price > 0) {
            txtPrice.setText(new DecimalFormat("#,###,###").format(price) + " đ");
        } else {
            txtPrice.setText("Khoản tự nguyện");
        }
    }

    private void readIntentData(Intent intent) {
        financeId = intent.getIntExtra("financeId", -1);
        title = intent.getStringExtra("title");
        content = intent.getStringExtra("content");
        dueDate = intent.getStringExtra("due_date");
        sender = intent.getStringExtra("sender");
        price = intent.getLongExtra("price", 0L);

        String status = intent.getStringExtra("payment_status");
        if (status != null) paymentStatus = status;
    }

    private void handlePayOSDeepLink(Intent intent) {
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri data = intent.getData();
        if (data == null || data.getPath() == null) return;

        if (data.getPath().contains("success")) {
            Toast.makeText(this, "Thanh toán thành công!", Toast.LENGTH_SHORT).show();
            updatePaymentStatusToServer(true);
        } else if (data.getPath().contains("cancel")) {
            Toast.makeText(this, "Bạn đã hủy thanh toán", Toast.LENGTH_SHORT).show();
            updatePaymentStatusToServer(false);
        }
    }

    // 🔥 API Lấy trạng thái mới nhất
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
                    updatePaymentUI();

                    // Nếu đã thanh toán -> Tải hóa đơn về
                    if ("da_thanh_toan".equalsIgnoreCase(status)) {
                        fetchInvoice();
                    }
                },
                error -> Log.e(TAG, "Lỗi lấy trạng thái: " + error.toString())
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

    private void updatePaymentUI() {
        if ("da_thanh_toan".equalsIgnoreCase(paymentStatus)) {
            btnPay.setVisibility(View.GONE);
            txtPaymentStatus.setVisibility(View.VISIBLE);
            txtPaymentStatus.setText("✅ Đã thanh toán");
            invoiceDetailView.setVisibility(View.VISIBLE);
        } else {
            btnPay.setVisibility(View.VISIBLE);
            txtPaymentStatus.setVisibility(View.GONE);
            invoiceDetailView.setVisibility(View.GONE);
        }
    }

    // API update trạng thái (khi PayOS trả về)
    private void updatePaymentStatusToServer(boolean success) {
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
                        new android.os.Handler().postDelayed(this::fetchInvoice, 500);
                    }
                },
                error -> Log.e(TAG, "Lỗi update status")
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

    // 🔥 API Lấy hóa đơn
    private void fetchInvoice() {
        int userId = Integer.parseInt(UserManager.getInstance(getApplicationContext()).getID());
        String url = ApiConfig.BASE_URL + "/api/invoice/by-finance/" + financeId + "?user_id=" + userId;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        String ordercode = response.optString("ordercode", "---");
                        long amount = response.optLong("amount", 0);
                        String desc = response.optString("description", "");
                        String rawPayTime = response.optString("pay_time_formatted", "Vừa xong");
                        String paidBy = response.optString("paid_by_name", "");

                        txtOrderCode.setText(ordercode);
                        txtAmount.setText(new DecimalFormat("#,###,###").format(amount) + " đ");
                        txtAmountInText.setText(VnNumberToWords.convert(amount));

                        if (!paidBy.isEmpty()) {
                            txtDetail.setText(desc + "\n(Người thanh toán: " + paidBy + ")");
                        } else {
                            txtDetail.setText(desc);
                        }

                        txtPayDate.setText(rawPayTime);
                        invoiceDetailView.setVisibility(View.VISIBLE);

                    } catch (Exception e) { e.printStackTrace(); }
                },
                error -> {
                    // Nếu lỗi 404 thì thôi, không retry loop nữa để tránh lỗi
                    Log.w(TAG, "Chưa tìm thấy hóa đơn (có thể server chưa tạo xong)");
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
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}