package com.se_04.enoti.finance;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View; // Required for View.VISIBLE/GONE
import android.widget.Button;
import android.widget.EditText; // Required for EditText
import android.widget.LinearLayout; // Required for LinearLayout
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;

import java.text.DecimalFormat;
import java.util.Locale;

public class PayActivity extends AppCompatActivity {

    // Global references for dynamic views
    private LinearLayout layoutMandatoryPrice;
    private LinearLayout layoutVoluntaryInput;
    private TextView txtReceiptTitle;
    private TextView txtBillInfoAmount;
    private EditText editVoluntaryAmount; // For Tự nguyện input
    private long price; // Store fixed price if mandatory
    private boolean isMandatory; // Store the bill type

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pay_2);

        // --- 1. Get View references (including the conditional layouts) ---
        txtReceiptTitle = findViewById(R.id.txtReceiptTitle);
        txtBillInfoAmount = findViewById(R.id.txtBillInfoAmount);
        Button btnPay = findViewById(R.id.buttonPay);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        // References to the layout blocks from activity_pay_2.xml
        layoutMandatoryPrice = findViewById(R.id.layoutMandatoryPrice);
        layoutVoluntaryInput = findViewById(R.id.layoutVoluntaryInput);
        editVoluntaryAmount = findViewById(R.id.editVoluntaryAmount);

        // 2. Get data from the Intent
        Intent intent = getIntent();

        String title = intent.getStringExtra("title");
        price = intent.getLongExtra("price", 0L);
        // Retrieve the flag passed from FinanceDetailActivity
        isMandatory = intent.getBooleanExtra("is_mandatory", price > 0);

        // --- Toolbar Setup ---
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Thanh toán cho hóa đơn: " + (title != null ? title : "N/A"));
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        // 3. Display and configure UI based on bill type
        setupBillDisplay(title);

        // --- Payment Button Click Listener ---
        btnPay.setOnClickListener(v -> handlePaymentClick());
    }

    // Extracted method to handle UI logic based on bill type
    private void setupBillDisplay(String title) {
        // Set the main receipt title
        if (title != null) {
            txtReceiptTitle.setText("Hóa đơn: " + title);
        } else {
            txtReceiptTitle.setText("Hóa đơn");
        }

        if (isMandatory) {
            // Bill is MANDATORY ("Bắt buộc"): Show fixed price, hide input.
            layoutMandatoryPrice.setVisibility(View.VISIBLE);
            layoutVoluntaryInput.setVisibility(View.GONE);

            // Format and display the fixed price
            if (price > 0) {
                DecimalFormat formatter = new DecimalFormat("#,###,###");
                String formattedPrice = formatter.format(price) + " đ";
                txtBillInfoAmount.setText(formattedPrice);
            } else {
                txtBillInfoAmount.setText("Lỗi: Số tiền bắt buộc là 0");
            }

        } else {
            // Bill is VOLUNTARY ("Tự nguyện"): Hide fixed price, show input field.
            layoutMandatoryPrice.setVisibility(View.GONE);
            layoutVoluntaryInput.setVisibility(View.VISIBLE);

            // Set hint for the voluntary input field
            editVoluntaryAmount.setHint("Nhập số tiền muốn đóng góp (VND)");
        }
    }

    // Placeholder for the actual payment initiation logic
    private void handlePaymentClick() {
        long finalAmount;
        String paymentDescription = "Thanh toán cho hóa đơn: " + txtReceiptTitle.getText().toString();

        if (isMandatory) {
            // Use the fixed price
            finalAmount = price;
        } else {
            // Get amount from EditText for voluntary payment
            String input = editVoluntaryAmount.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập số tiền đóng góp.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                // Remove formatting (commas) if present, then parse
                finalAmount = Long.parseLong(input.replace(",", ""));
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Số tiền không hợp lệ.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (finalAmount <= 0) {
            Toast.makeText(this, "Số tiền thanh toán phải lớn hơn 0.", Toast.LENGTH_SHORT).show();
            return;
        }

        // TODO: Call your server API (e.g., using OkHttp or Retrofit) with finalAmount and paymentDescription
        Toast.makeText(this, "Chuẩn bị thanh toán: " + finalAmount + " VND", Toast.LENGTH_LONG).show();
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