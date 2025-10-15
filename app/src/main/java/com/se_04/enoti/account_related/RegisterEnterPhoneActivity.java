package com.se_04.enoti.account_related;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.isValidVietnamesePhoneNumber;
import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.normalizePhoneNumber;

// Assuming EnterOTPActivity has these constants, or you have a Constants.java
import static com.se_04.enoti.account_related.EnterOTPActivity.EXTRA_PREVIOUS_ACTIVITY;
import static com.se_04.enoti.account_related.EnterOTPActivity.FROM_REGISTER_PHONE;

import com.se_04.enoti.R;

public class RegisterEnterPhoneActivity extends AppCompatActivity {

    EditText editTextPhone;
    Button buttonGetOTP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_get_otp); // Link to your layout file

        editTextPhone = findViewById(R.id.enterPhoneNumberRegister);
        buttonGetOTP = findViewById(R.id.buttonGetOTPRegister);

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
                Intent intent = new Intent(RegisterEnterPhoneActivity.this, EnterOTPActivity.class);
                intent.putExtra("phone", normalizePhoneNumber(phone));
                intent.putExtra(EXTRA_PREVIOUS_ACTIVITY, FROM_REGISTER_PHONE);
                startActivity(intent);
            }
        });

    }
}
