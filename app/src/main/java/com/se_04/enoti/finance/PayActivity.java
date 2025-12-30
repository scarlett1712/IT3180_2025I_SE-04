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

    // UI Components
    private LinearLayout layoutMandatoryPrice;
    private LinearLayout layoutVoluntaryInput;
    private TextView txtReceiptTitle;
    private TextView txtBillInfoAmount;
    private EditText editVoluntaryAmount;
    private Button btnPay;

    // Các View hiển thị thông tin hóa đơn (Mới)
    private TextView txtOrderCode;
    private TextView txtCreatedDate;
    private TextView txtContent;

    // Data Variables
    private long price;
    private boolean isMandatory;
    private int financeId;
    private int currentUserId;
    private long currentOrderCode; // Mã đơn hàng tự sinh

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pay_2);

        // 1. Lấy User ID (Bắt buộc phải có để gạch nợ sau này)
        try {
            currentUserId = Integer.parseInt(UserManager.getInstance(this).getID());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Lỗi phiên đăng nhập. Vui lòng đăng nhập lại.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        readIntentData();
        setupToolbar();
        setupBillDisplay();

        // 2. Tạo và hiển thị dữ liệu (Mã đơn, Ngày, Nội dung) NGAY LẬP TỨC
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

        // Đảm bảo trong XML bạn đã đặt đúng ID cho các trường này
        txtOrderCode = findViewById(R.id.txtOrderCode);
        txtCreatedDate = findViewById(R.id.txtPayDate); // Hoặc txtDate tùy XML
        txtContent = findViewById(R.id.txtDetail);      // Hoặc txtDetail tùy XML
    }

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
    }

    // 🔥 LOGIC TẠO DỮ LIỆU HIỂN THỊ TRƯỚC
    private void generateAndShowOrderInfo() {
        // 1. Tạo OrderCode (Số ngẫu nhiên < 9007199254740991 theo chuẩn PayOS)
        // Lấy timestamp bỏ 3 số đầu + random 2 số để đảm bảo tính duy nhất và độ dài hợp lý
        String timePart = String.valueOf(System.currentTimeMillis()).substring(3);
        int randomPart = new Random().nextInt(99);
        // Kết hợp lại thành số long
        try {
            currentOrderCode = Long.parseLong(timePart + randomPart);
        } catch (NumberFormatException e) {
            currentOrderCode = System.currentTimeMillis(); // Fallback nếu lỗi
        }

        // 2. Lấy ngày hiện tại
        String currentDate = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());

        // 3. Lấy tiêu đề
        String title = getIntent().getStringExtra("title");
        if (title == null) title = "Thanh toán phí";

        // 4. Hiển thị lên giao diện
        if (txtOrderCode != null) txtOrderCode.setText(String.valueOf(currentOrderCode));
        if (txtCreatedDate != null) txtCreatedDate.setText(currentDate);
        if (txtContent != null) txtContent.setText(title);

        txtReceiptTitle.setText("Hóa đơn: " + title);
    }

    private void setupBillDisplay() {
        if (isMandatory) {
            // Khoản thu bắt buộc
            layoutMandatoryPrice.setVisibility(View.VISIBLE);
            layoutVoluntaryInput.setVisibility(View.GONE);

            DecimalFormat formatter = new DecimalFormat("#,###,###");
            txtBillInfoAmount.setText(formatter.format(price) + " đ");
        } else {
            // Khoản thu tự nguyện
            layoutMandatoryPrice.setVisibility(View.GONE);
            layoutVoluntaryInput.setVisibility(View.VISIBLE);

            editVoluntaryAmount.addTextChangedListener(new NumberTextWatcher(editVoluntaryAmount));
            editVoluntaryAmount.setHint("Nhập số tiền (VND)");
        }
    }

    private void handlePaymentClick() {
        String title = getIntent().getStringExtra("title");
        long finalAmount;

        if (isMandatory) {
            finalAmount = price;
        } else {
            String input = editVoluntaryAmount.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập số tiền.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                // Loại bỏ ký tự không phải số để parse
                finalAmount = Long.parseLong(input.replaceAll("[^\\d]", ""));
            } catch (Exception e) {
                Toast.makeText(this, "Số tiền không hợp lệ.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Validate số tiền
        if (finalAmount <= 0) {
            Toast.makeText(this, "Số tiền phải lớn hơn 0đ.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (finalAmount > 100000000) {
            Toast.makeText(this, "Số tiền quá lớn.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable nút để tránh spam
        btnPay.setEnabled(false);
        btnPay.setText("Đang tạo link...");

        // Gọi API tạo link thanh toán
        sendPaymentToServer(title, finalAmount);
    }

    private void sendPaymentToServer(String title, long amount) {
        new Thread(() -> {
            try {
                // Setup OkHttp
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();

                // Tạo JSON Body
                JSONObject json = new JSONObject();
                json.put("title", title);
                json.put("amount", amount);
                json.put("financeId", financeId);
                json.put("userId", currentUserId); // 🔥 Gửi ID người dùng
                json.put("orderCode", currentOrderCode); // 🔥 Gửi Mã đơn hàng đã hiển thị

                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                // URL API Backend (Sử dụng URL render của bạn)
                String url = "https://nmcnpm-se-04.onrender.com/create-payment-link";

                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JSONObject obj = new JSONObject(responseBody);

                    if (obj.has("checkoutUrl")) {
                        String checkoutUrl = obj.getString("checkoutUrl");

                        runOnUiThread(() -> {
                            // Mở trình duyệt hoặc WebView
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl));
                            startActivity(browserIntent);
                            // Đóng màn hình này để khi quay lại sẽ refresh ở màn hình chi tiết
                            finish();
                        });
                    } else {
                        throw new Exception("Không tìm thấy link thanh toán trong phản hồi.");
                    }
                } else {
                    throw new Exception("Lỗi Server: " + response.code() + " - " + response.message());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Mở lại nút nếu lỗi
                    btnPay.setEnabled(true);
                    btnPay.setText("Thanh toán ngay");
                });
            }
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --- Helper Class: Format số tiền khi nhập liệu (100,000) ---
    private static class NumberTextWatcher implements TextWatcher {
        private final EditText editText;
        private final DecimalFormat formatter = new DecimalFormat("#,###,###");
        private String current = "";

        public NumberTextWatcher(EditText editText) {
            this.editText = editText;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (!s.toString().equals(current)) {
                editText.removeTextChangedListener(this);

                try {
                    String cleanString = s.toString().replaceAll("[^\\d]", "");
                    if (!cleanString.isEmpty()) {
                        long parsed = Long.parseLong(cleanString);
                        String formatted = formatter.format(parsed);
                        current = formatted;
                        editText.setText(formatted);
                        editText.setSelection(formatted.length());
                    } else {
                        current = "";
                        editText.setText("");
                    }
                } catch (Exception ignored) {
                }

                editText.addTextChangedListener(this);
            }
        }
    }
}