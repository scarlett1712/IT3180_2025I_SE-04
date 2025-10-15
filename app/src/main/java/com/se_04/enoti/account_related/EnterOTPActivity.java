package com.se_04.enoti.account_related;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

import com.se_04.enoti.R;


public class EnterOTPActivity extends AppCompatActivity {

    public static String EXTRA_PREVIOUS_ACTIVITY;
    public static final String FROM_FORGOT_PASSWORD = "FROM_FORGOT_PASSWORD";
    public static final String FROM_REGISTER_PHONE = "FROM_REGISTER_PHONE";
    Button buttonConfirm;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_otp);

        buttonConfirm = findViewById(R.id.buttonConfirm);
        buttonConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getIntent().getStringExtra(EXTRA_PREVIOUS_ACTIVITY).equals(FROM_FORGOT_PASSWORD)) {
                    Intent intent = new Intent(EnterOTPActivity.this, CreateNewPasswordActivity.class);
                    startActivity(intent);
                }
                else if (getIntent().getStringExtra(EXTRA_PREVIOUS_ACTIVITY).equals(FROM_REGISTER_PHONE)) {
                    Intent intent = new Intent(EnterOTPActivity.this, RegisterActivity.class);
                    startActivity(intent);
                }
            }
        });
    }
}
