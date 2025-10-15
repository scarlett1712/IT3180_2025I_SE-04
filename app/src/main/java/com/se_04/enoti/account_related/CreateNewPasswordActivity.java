package com.se_04.enoti.account_related;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.se_04.enoti.R;
import com.se_04.enoti.home.user.MainActivity_User;

public class CreateNewPasswordActivity extends AppCompatActivity {

    private EditText editTextNewPassword;
    private EditText editTextConfirmPassword;
    private Button buttonConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_new_password); // Link to your layout file

        editTextNewPassword = findViewById(R.id.editTextNewPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmNewPassword);
        buttonConfirm = findViewById(R.id.buttonConfirm);

        buttonConfirm.setOnClickListener(v -> {
            handleCreateNewPassword(editTextNewPassword, editTextConfirmPassword);
        });
    }

    protected void handleCreateNewPassword(EditText newPasswordField, EditText confirmPasswordField) {
        String newPassword = newPasswordField.getText().toString().trim();
        String confirmPassword = confirmPasswordField.getText().toString().trim();
        int minPasswordLength = 6, maxPasswordLength = 16;

        if (newPassword.isEmpty()) {
            newPasswordField.setError("Mật khẩu mới không thể để trống");
            newPasswordField.requestFocus();
            return;
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordField.setError("Mật khẩu xác nhận không thể để trống");
            confirmPasswordField.requestFocus();
            return;
        }

        if (newPassword.length() < minPasswordLength || newPassword.length() > maxPasswordLength) {
            newPasswordField.setError("Mật khẩu cần phải dài trong khoảng từ " + minPasswordLength + " tới " + maxPasswordLength + " ký tự");
            newPasswordField.requestFocus();
        }

        if (confirmPassword.length() < minPasswordLength || confirmPassword.length() > maxPasswordLength) {
            confirmPasswordField.setError("Mật khẩu cần phải dài trong khoảng từ " + minPasswordLength + " tới " + maxPasswordLength + " ký tự");
            confirmPasswordField.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            confirmPasswordField.setError("Mật khẩu xác nhận không khớp");
            confirmPasswordField.requestFocus();
            return;
        }

        newPasswordField.setError(null);
        confirmPasswordField.setError(null);

        Toast.makeText(this, "Tạo mật khẩu mới thành công.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(CreateNewPasswordActivity.this, MainActivity_User.class);
        startActivity(intent);
        finish();
    }
}
