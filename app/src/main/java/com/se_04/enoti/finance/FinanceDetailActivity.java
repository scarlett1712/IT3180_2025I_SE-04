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
        final String title = intent.getStringExtra("title") != null ? intent.getStringExtra("title") : getString(R.string.no_data);
        String content = intent.getStringExtra("content") != null ? intent.getStringExtra("content") : getString(R.string.no_content_detail);
        String date = intent.getStringExtra("due_date") != null ? intent.getStringExtra("due_date") : "N/A";
        String sender = intent.getStringExtra("sender") != null ? intent.getStringExtra("sender") : "N/A";
        final long price = intent.getLongExtra("price", 0L);

        // Determine the bill type: Mandatory ("Bắt buộc") if price > 0, Voluntary ("Tự nguyện") otherwise.
        final boolean isMandatory = (price > 0);

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
            // Assuming this text corresponds to "Tự nguyện"
            txtPrice.setText(R.string.contribution_text);
        }

        // --- [NEW] Payment Button Logic Open PayActivity and pass data ---
        btnPay.setOnClickListener(v -> {
            Intent intent1 = new Intent(FinanceDetailActivity.this, PayActivity.class);
            intent1.putExtra("title", title);
            intent1.putExtra("price", price);
            // Pass the mandatory flag for PayActivity to determine UI layout
            intent1.putExtra("is_mandatory", isMandatory);
            startActivity(intent1);
        });
    }

    /**
     * Creates and shows a dialog with a fixed QR code image.
     */

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}