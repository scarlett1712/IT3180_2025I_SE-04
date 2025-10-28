package com.se_04.enoti.finance;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;

import java.text.NumberFormat;
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
        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        // --- Toolbar Setup ---
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.finance_detail_title);
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        // --- ROBUST Data Handling ---
        Intent intent = getIntent();
        // Provide default empty strings to prevent null pointer exceptions
        String title = intent.getStringExtra("title") != null ? intent.getStringExtra("title") : getString(R.string.no_data);
        String content = intent.getStringExtra("content") != null ? intent.getStringExtra("content") : getString(R.string.no_content_detail);
        String date = intent.getStringExtra("due_date") != null ? intent.getStringExtra("due_date") : "N/A";
        String sender = intent.getStringExtra("sender") != null ? intent.getStringExtra("sender") : "N/A";
        
        // This will now always work because the adapter guarantees to send a long
        long price = intent.getLongExtra("price", 0L);

        // --- Display Data ---
        txtReceiptTitle.setText(title);
        txtReceiptDeadline.setText(getString(R.string.deadline_format, date));
        txtSender.setText(getString(R.string.collector_format, sender));
        txtDetailContent.setText(content);

        if (price > 0) {
            String formattedPrice = NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(price);
            txtPrice.setText(formattedPrice);
        } else {
            txtPrice.setText(R.string.contribution_text);
        }
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
