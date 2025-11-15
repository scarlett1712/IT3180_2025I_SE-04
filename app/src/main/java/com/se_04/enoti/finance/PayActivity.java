package com.se_04.enoti.finance;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import org.json.JSONObject;

import java.text.DecimalFormat;

public class PayActivity extends AppCompatActivity {

    private LinearLayout layoutMandatoryPrice;
    private LinearLayout layoutVoluntaryInput;
    private TextView txtReceiptTitle;
    private TextView txtBillInfoAmount;
    private EditText editVoluntaryAmount;

    private long price;
    private boolean isMandatory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pay_2);

        txtReceiptTitle = findViewById(R.id.txtReceiptTitle);
        txtBillInfoAmount = findViewById(R.id.txtBillInfoAmount);
        Button btnPay = findViewById(R.id.buttonPay);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        layoutMandatoryPrice = findViewById(R.id.layoutMandatoryPrice);
        layoutVoluntaryInput = findViewById(R.id.layoutVoluntaryInput);
        editVoluntaryAmount = findViewById(R.id.editVoluntaryAmount);

        Intent intent = getIntent();
        String title = intent.getStringExtra("title");
        price = intent.getLongExtra("price", 0L);
        isMandatory = intent.getBooleanExtra("is_mandatory", price > 0);

        // Toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Thanh toán: " + title);
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        setupBillDisplay(title);

        btnPay.setOnClickListener(v -> handlePaymentClick(title));
    }

    private void setupBillDisplay(String title) {
        txtReceiptTitle.setText("Hóa đơn: " + title);

        if (isMandatory) {
            layoutMandatoryPrice.setVisibility(View.VISIBLE);
            layoutVoluntaryInput.setVisibility(View.GONE);

            DecimalFormat formatter = new DecimalFormat("#,###,###");
            txtBillInfoAmount.setText(formatter.format(price) + " đ");

        } else {
            layoutMandatoryPrice.setVisibility(View.GONE);
            layoutVoluntaryInput.setVisibility(View.VISIBLE);

            editVoluntaryAmount.addTextChangedListener(new NumberTextWatcher(editVoluntaryAmount));
            editVoluntaryAmount.setHint("Nhập số tiền muốn đóng góp (VND)");
        }
    }

    private void handlePaymentClick(String title) {
        long finalAmount;

        if (isMandatory) {
            finalAmount = price;
        } else {
            String input = editVoluntaryAmount.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập số tiền đóng góp.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                finalAmount = Long.parseLong(input.replace(",", ""));
            } catch (Exception e) {
                Toast.makeText(this, "Số tiền không hợp lệ.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (finalAmount <= 0) {
            Toast.makeText(this, "Số tiền phải lớn hơn 0.", Toast.LENGTH_SHORT).show();
            return;
        }

        sendPaymentToServer(title, finalAmount);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --- FORMAT NUMBER INPUT ---
    private static class NumberTextWatcher implements android.text.TextWatcher {
        private final EditText editText;
        private final DecimalFormat formatter = new DecimalFormat("#,###,###");
        private String current = "";

        public NumberTextWatcher(EditText editText) {
            this.editText = editText;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(android.text.Editable s) {
            String clean = s.toString().replaceAll("[^\\d]", "");

            if (!clean.equals(current.replaceAll("[^\\d]", ""))) {
                editText.removeTextChangedListener(this);

                try {
                    long parsed = Long.parseLong(clean);
                    String formatted = formatter.format(parsed);

                    current = formatted;
                    editText.setText(formatted);
                    editText.setSelection(formatted.length());
                } catch (Exception ignored) {}

                editText.addTextChangedListener(this);
            }
        }
    }

    // --- SEND TO BACKEND USING OKHTTP ---
    private void sendPaymentToServer(String title, long amount) {

        new Thread(() -> {
            try {
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();

                JSONObject json = new JSONObject();
                json.put("title", title);
                json.put("amount", amount);

                okhttp3.RequestBody body = okhttp3.RequestBody.create(
                        json.toString(),
                        okhttp3.MediaType.parse("application/json; charset=utf-8")
                );

                // ⚠ Android emulator cannot use localhost → must use 10.0.2.2
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url("https://it3180-2025i-se-04.onrender.com/create-payment-link")
                        .post(body)
                        .build();

                okhttp3.Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                JSONObject obj = new JSONObject(responseBody);
                String checkoutUrl = obj.getString("checkoutUrl");

                runOnUiThread(() -> {
                    Intent browserIntent =
                            new Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl));
                    startActivity(browserIntent);
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Lỗi API: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
}
