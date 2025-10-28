package com.se_04.enoti.finance;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;

import java.text.DecimalFormat;
import java.util.Locale;

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

        // --- Toolbar Setup ---
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.finance_detail_title);
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        // --- Data Handling ---
        Intent intent = getIntent();
        String title = intent.getStringExtra("title") != null ? intent.getStringExtra("title") : getString(R.string.no_data);
        String content = intent.getStringExtra("content") != null ? intent.getStringExtra("content") : getString(R.string.no_content_detail);
        String date = intent.getStringExtra("due_date") != null ? intent.getStringExtra("due_date") : "N/A";
        String sender = intent.getStringExtra("sender") != null ? intent.getStringExtra("sender") : "N/A";
        long price = intent.getLongExtra("price", 0L);

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

        // --- [NEW] Payment Button Logic ---
        btnPay.setOnClickListener(v -> showQrCodeDialog());
    }

    /**
     * Creates and shows a dialog with a fixed QR code image.
     */
    private void showQrCodeDialog() {
        // Create a new Dialog
        final Dialog qrDialog = new Dialog(this);
        
        // Inflate the custom layout we created
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_qr_code, null);
        qrDialog.setContentView(dialogView);

        // Find the ImageView inside the dialog
        ImageView imgQrCode = qrDialog.findViewById(R.id.imgQrCode);

        // Set your static QR code image from the drawable folder
        // IMPORTANT: Make sure you have an image named 'qr_payment.png' in your res/drawable folder.
        imgQrCode.setImageResource(R.drawable.qr_payment);

        // Show the dialog
        qrDialog.show();
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
