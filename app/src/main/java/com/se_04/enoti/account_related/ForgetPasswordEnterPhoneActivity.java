package com.se_04.enoti.account_related; // Or your package name

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.isValidVietnamesePhoneNumber;
import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.normalizePhoneNumber;

import com.se_04.enoti.R;

public class ForgetPasswordEnterPhoneActivity extends AppCompatActivity {

    EditText editTextPhone;
    Button buttonGetOTP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forget_password); // Link to your layout file

        editTextPhone = findViewById(R.id.enterPhoneNumberForget);
        buttonGetOTP = findViewById(R.id.buttonGetOTPForget);
        TextView textBackToLogin = findViewById(R.id.textBackToLogin);

        buttonGetOTP.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v){
                String phone = editTextPhone.getText().toString().trim();
                if (!isValidVietnamesePhoneNumber(phone)) {
                    editTextPhone.setError("Nhập số điện thoại chính xác.");
                    editTextPhone.requestFocus();
                    return;
                } else {
                    phone = normalizePhoneNumber(phone);
                }
                Intent intent = new Intent(ForgetPasswordEnterPhoneActivity.this, EnterOTPActivity.class);
                intent.putExtra("phone", normalizePhoneNumber(phone));
                intent.putExtra(EnterOTPActivity.EXTRA_PREVIOUS_ACTIVITY, EnterOTPActivity.FROM_FORGOT_PASSWORD);
                startActivity(intent);
            }
        });

        textBackToLogin.setOnClickListener(v -> {
            finish();
        });

    }
}