package com.se_04.enoti.finance;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PayActivity extends BaseActivity {

    private LinearLayout layoutMandatoryPrice;
    private LinearLayout layoutVoluntaryInput;
    private TextView txtReceiptTitle;
    private TextView txtBillInfoAmount;
    private EditText editVoluntaryAmount;
    private Button btnPay;

    // 🔥 CÁC VIEW HIỂN THỊ THÔNG TIN
    private TextView txtOrderCode;
    private TextView txtCreatedDate;
    private TextView txtContent;

    private long price;
    private boolean isMandatory;
    private int financeId;
    private int currentUserId;
    private long currentOrderCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pay_2);

        try {
            currentUserId = Integer.parseInt(UserManager.getInstance(this).getID());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Lỗi phiên đăng nhập", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        readIntentData();
        setupToolbar();
        setupBillDisplay();

        // 🔥 GỌI HÀM NÀY ĐỂ ĐIỀN DỮ LIỆU
        generateAndShowOrderInfo();

        btnPay.setOnClickListener(v -> handlePaymentClick());
    }

    private void initViews() {
        txtReceiptTitle = findViewById(R.id.txtReceiptTitle);
        txtBillInfoAmount = findViewById(R.id.txtBillInfoAmount);
        btnPay = findViewById(R.id.buttonPay);
        layoutMandatoryPrice = findViewById(R.id.layoutMandatoryPrice);
        layoutVoluntaryInput = findViewById(R.id.layoutVoluntaryInput);
        editVoluntaryAmount = findViewById(R.id.editVoluntaryAmount);

        // 🔥 ÁNH XẠ ĐÚNG ID VỚI XML MỚI
        txtOrderCode = findViewById(R.id.txtReceiptID);
        txtCreatedDate = findViewById(R.id.txtReceiptDate);
        txtContent = findViewById(R.id.txtBillInfoDetail);
    }

    private void generateAndShowOrderInfo() {
        // 1. Tạo Mã đơn
        String timePart = String.valueOf(System.currentTimeMillis()).substring(3);
        int randomPart = new Random().nextInt(99);
        try {
            currentOrderCode = Long.parseLong(timePart + randomPart);
        } catch (Exception e) { currentOrderCode = System.currentTimeMillis(); }

        // 2. Ngày hiện tại
        String currentDate = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());

        // 3. Tiêu đề
        String title = getIntent().getStringExtra("title");
        if (title == null) title = "Thanh toán";

        // 4. Set Text vào View (Đảm bảo view không null)
        if (txtOrderCode != null) txtOrderCode.setText(String.valueOf(currentOrderCode));
        if (txtCreatedDate != null) txtCreatedDate.setText(currentDate);
        if (txtContent != null) txtContent.setText(title);

        txtReceiptTitle.setText("Hóa đơn: " + title);
    }

    // ... (Các phần readIntentData, setupToolbar, setupBillDisplay, handlePaymentClick giữ nguyên như code trước)
    // ... Copy từ code trước của tôi là chạy tốt ...

    private void readIntentData() {
        Intent intent = getIntent();
        price = intent.getLongExtra("price", 0L);
        isMandatory = intent.getBooleanExtra("is_mandatory", price > 0);
        financeId = intent.getIntExtra("financeId", -1);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết thanh toán");
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupBillDisplay() {
        if (isMandatory) {
            layoutMandatoryPrice.setVisibility(View.VISIBLE);
            layoutVoluntaryInput.setVisibility(View.GONE);
            DecimalFormat formatter = new DecimalFormat("#,###,###");
            txtBillInfoAmount.setText(formatter.format(price) + " đ");
        } else {
            layoutMandatoryPrice.setVisibility(View.GONE);
            layoutVoluntaryInput.setVisibility(View.VISIBLE);
            editVoluntaryAmount.addTextChangedListener(new NumberTextWatcher(editVoluntaryAmount));
        }
    }

    private void handlePaymentClick() {
        String title = getIntent().getStringExtra("title");
        long finalAmount;
        if (isMandatory) {
            finalAmount = price;
        } else {
            String input = editVoluntaryAmount.getText().toString().trim();
            if (input.isEmpty()) { Toast.makeText(this, "Nhập số tiền", Toast.LENGTH_SHORT).show(); return; }
            try { finalAmount = Long.parseLong(input.replaceAll("[^\\d]", "")); } catch (Exception e) { return; }
        }
        if (finalAmount <= 0) return;

        btnPay.setEnabled(false);
        btnPay.setText("Đang tạo link...");
        sendPaymentToServer(title, finalAmount);
    }

    private void sendPaymentToServer(String title, long amount) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build();
                JSONObject json = new JSONObject();
                json.put("title", title);
                json.put("amount", amount);
                json.put("financeId", financeId);
                json.put("userId", currentUserId);

                RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder().url("https://it3180-2025i-se-04.onrender.com/create-payment-link").post(body).build();
                Response response = client.newCall(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    JSONObject obj = new JSONObject(response.body().string());
                    if (obj.has("checkoutUrl")) {
                        String checkoutUrl = obj.getString("checkoutUrl");
                        runOnUiThread(() -> {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl));
                            startActivity(browserIntent);
                            finish();
                        });
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnPay.setEnabled(true);
                    btnPay.setText("Thanh toán ngay");
                });
            }
        }).start();
    }

    private static class NumberTextWatcher implements TextWatcher {
        private final EditText editText;
        private final DecimalFormat formatter = new DecimalFormat("#,###,###");
        private String current = "";
        public NumberTextWatcher(EditText editText) { this.editText = editText; }
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable s) {
            if (!s.toString().equals(current)) {
                editText.removeTextChangedListener(this);
                try {
                    String clean = s.toString().replaceAll("[^\\d]", "");
                    if (!clean.isEmpty()) {
                        current = formatter.format(Long.parseLong(clean));
                        editText.setText(current);
                        editText.setSelection(current.length());
                    } else { current = ""; editText.setText(""); }
                } catch (Exception e) {}
                editText.addTextChangedListener(this);
            }
        }
    }
}