package com.se_04.enoti.finance;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;

import java.text.NumberFormat;
import java.util.Locale;

public class FinanceDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_detail);

        TextView txtReceiptTitle = findViewById(R.id.txtReceiptTitle);
        TextView txtReceiptDeadline = findViewById(R.id.txtReceiptDeadline);
        TextView txtPrice = findViewById(R.id.txtPrice);
        TextView txtDetailContent = findViewById(R.id.txtDetailContent);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setTitleTextColor(getResources().getColor(android.R.color.white, getTheme()));
            getSupportActionBar().setTitle("Hóa đơn cần thanh toán");
        }

        int position = getIntent().getIntExtra("position", -1);
        if (position >= 0) {
            FinanceItem item = FinanceRepository.getInstance().getReceipts().get(position);

            txtReceiptTitle.setText(item.getTitle());
            txtReceiptDeadline.setText(item.getDate());
            if (item.getPrice() != null) {
                String formatted = NumberFormat.getNumberInstance(Locale.getDefault())
                        .format(item.getPrice()) + " đ";
                txtPrice.setText(formatted);
            } else {
                txtPrice.setText("Tùy theo đóng góp");
            }
            txtDetailContent.setText(item.getType());
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
