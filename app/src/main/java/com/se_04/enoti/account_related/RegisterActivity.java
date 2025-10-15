package com.se_04.enoti.account_related;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.se_04.enoti.R;
import com.se_04.enoti.home.user.MainActivity_User;

public class RegisterActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize your Button here, after setContentView
        Button buttonRegister = findViewById(R.id.buttonRegister);

        // Set the OnClickListener here, inside the onCreate method
        buttonRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an Intent to start MainActivity_User
                Intent intent = new Intent(RegisterActivity.this, MainActivity_User.class);
                startActivity(intent);
                Toast.makeText(RegisterActivity.this, "Đăng ký thành công.", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}