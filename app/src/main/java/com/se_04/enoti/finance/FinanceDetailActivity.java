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
import androidx.appcompat.app.AppCompatActivity;
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
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.Objects;

public class FinanceDetailActivity extends BaseActivity {

    private static final String TAG = "FinanceDetailActivity";

    private int financeId;
    private long price;
    private String title, content, dueDate, sender, paymentStatus;

    private Button btnPay;
    private TextView txtPaymentStatus;

    // INVOICE UI
    private TextView txtOrderCode, txtAmount, txtAmountInText, txtDetail, txtPayDate;
    private View invoiceDetailView;

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
        invoiceDetailView = findViewById(R.id.invoiceDetail);
        txtOrderCode = findViewById(R.id.txtOrderCode);
        txtAmount = findViewById(R.id.txtAmount);
        txtAmountInText = findViewById(R.id.txtAmountInText);
        txtDetail = findViewById(R.id.txtDetail);
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

        Log.d(TAG, "📋 onCreate - financeId: " + financeId + ", paymentStatus: " + paymentStatus);

        updatePaymentUI();

        btnPay.setOnClickListener(v -> {
            Intent payIntent = new Intent(FinanceDetailActivity.this, PayActivity.class);
            payIntent.putExtra("title", title);
            payIntent.putExtra("price", price);
            payIntent.putExtra("financeId", financeId);
            payIntent.putExtra("is_mandatory", price > 0);
            startActivity(payIntent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "🔄 onResume - Refreshing payment status");

        // 🔥 Add a small delay to ensure server has processed the payment
        new android.os.Handler().postDelayed(() -> {
            refreshPaymentStatus();
        }, 500);
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

        Log.d(TAG, "📋 Finance ID: " + financeId);
        Log.d(TAG, "💰 Price: " + price);
        Log.d(TAG, "📊 Payment Status: " + paymentStatus);
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

    private void refreshPaymentStatus() {
        String url = ApiConfig.BASE_URL + "/api/finance/user/payment-status/" + financeId;

        int userId;
        try {
            userId = Integer.parseInt(UserManager.getInstance(getApplicationContext()).getID());
        } catch (Exception e) {
            Log.e(TAG, "Failed to get user ID", e);
            return;
        }

        url += "?user_id=" + userId;

        Log.d(TAG, "🔍 Checking payment status: " + url);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        String status = response.optString("status", "chua_thanh_toan");
                        Log.d(TAG, "✅ Current payment status: " + status);

                        paymentStatus = status;
                        updatePaymentUI();

                        // If paid, fetch invoice with a small delay
                        if ("da_thanh_toan".equalsIgnoreCase(status)) {
                            // 🔥 Add delay to ensure invoice is created
                            new android.os.Handler().postDelayed(() -> {
                                fetchInvoice();
                            }, 1000);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing payment status", e);
                    }
                },
                error -> {
                    Log.e(TAG, "❌ Error fetching payment status: " + error.toString());

                    if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                        UserManager.getInstance(this).checkAndForceLogout(error);
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null && !token.isEmpty()) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }

    private void updatePaymentUI() {
        Log.d(TAG, "🎨 Updating UI - paymentStatus: " + paymentStatus);

        if ("da_thanh_toan".equalsIgnoreCase(paymentStatus)) {
            btnPay.setVisibility(View.GONE);
            txtPaymentStatus.setVisibility(View.VISIBLE);
            txtPaymentStatus.setText("✅ Đã thanh toán");
            invoiceDetailView.setVisibility(View.VISIBLE);
            Log.d(TAG, "✅ Showing invoice section");
        } else {
            btnPay.setVisibility(View.VISIBLE);
            txtPaymentStatus.setVisibility(View.GONE);
            invoiceDetailView.setVisibility(View.GONE);
            Log.d(TAG, "💳 Showing payment button");
        }
    }

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
                    Log.d(TAG, "✅ Payment status updated successfully");
                    if (success) {
                        new android.os.Handler().postDelayed(this::fetchInvoice, 500);
                    }
                },
                error -> {
                    Log.e(TAG, "❌ Error updating payment status: " + error.toString());
                    Toast.makeText(this, "Lỗi API cập nhật", Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null && !token.isEmpty()) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(req);
    }

    private void fetchInvoice() {
        int userId;
        try {
            userId = Integer.parseInt(UserManager.getInstance(getApplicationContext()).getID());
        } catch (Exception e) {
            Log.e(TAG, "Failed to get user ID", e);
            Toast.makeText(this, "Lỗi lấy thông tin người dùng", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/invoice/by-finance/" + financeId + "?user_id=" + userId;

        Log.d(TAG, "🔍 Fetching invoice from: " + url);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    Log.d(TAG, "✅ Invoice response: " + response.toString());
                    try {
                        String ordercode = response.getString("ordercode");
                        long amount = response.getLong("amount");
                        String desc = response.getString("description");
                        String rawPayTime = response.optString("pay_time_formatted", "");

                        // 🔥 Update UI on main thread
                        runOnUiThread(() -> {
                            txtOrderCode.setText(ordercode);
                            txtAmount.setText(new DecimalFormat("#,###,###").format(amount) + " đ");
                            txtAmountInText.setText(convertNumberToWords(amount));
                            txtDetail.setText(desc);
                            txtPayDate.setText(rawPayTime.isEmpty() ? "Vừa xong" : rawPayTime);

                            invoiceDetailView.setVisibility(View.VISIBLE);

                            Log.d(TAG, "✅ Invoice displayed successfully");
                            Log.d(TAG, "📋 Order: " + ordercode + ", Amount: " + amount);
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error parsing invoice: " + e.getMessage(), e);
                        runOnUiThread(() ->
                                Toast.makeText(this, "Lỗi hiển thị hóa đơn: " + e.getMessage(), Toast.LENGTH_LONG).show()
                        );
                    }
                },
                error -> {
                    Log.e(TAG, "❌ Error fetching invoice: " + error.toString());

                    if (error.networkResponse != null) {
                        Log.e(TAG, "Status code: " + error.networkResponse.statusCode);

                        // 🔥 Log full error response
                        if (error.networkResponse.data != null) {
                            String errorBody = new String(error.networkResponse.data);
                            Log.e(TAG, "Error body: " + errorBody);
                        }

                        if (error.networkResponse.statusCode == 401) {
                            UserManager.getInstance(this).checkAndForceLogout(error);
                            return;
                        }

                        // 🔥 Show specific error message
                        if (error.networkResponse.statusCode == 404) {
                            runOnUiThread(() ->
                                    Toast.makeText(this, "Hóa đơn chưa được tạo. Vui lòng thử lại sau.", Toast.LENGTH_LONG).show()
                            );

                            // Retry after 2 seconds
                            new android.os.Handler().postDelayed(this::fetchInvoice, 2000);
                            return;
                        }
                    }

                    runOnUiThread(() ->
                            Toast.makeText(this, "Không lấy được hóa đơn!", Toast.LENGTH_SHORT).show()
                    );
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null && !token.isEmpty()) {
                    headers.put("Authorization", "Bearer " + token);
                    Log.d(TAG, "✅ Sending token: " + token.substring(0, Math.min(10, token.length())) + "...");
                } else {
                    Log.e(TAG, "⚠️ WARNING: No token available!");
                }
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }

    private String convertUtcToLocal(String utcTime) {
        if (utcTime == null || utcTime.isEmpty()) return "Vừa xong";
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            java.util.Date date = inputFormat.parse(utcTime);

            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            outputFormat.setTimeZone(TimeZone.getDefault());

            return outputFormat.format(date);
        } catch (Exception e) {
            Log.e(TAG, "Error converting UTC time: " + e.getMessage());
            return utcTime;
        }
    }

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