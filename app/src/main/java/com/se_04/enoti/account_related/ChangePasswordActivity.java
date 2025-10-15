package com.se_04.enoti.account_related; // Or your package name

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.se_04.enoti.R;

public class ChangePasswordActivity extends AppCompatActivity {

    private EditText editTextOldPassword;
    private EditText editTextNewPassword;
    private EditText editTextConfirmPassword;
    private Button buttonChangePassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password); // Link to your layout file

        editTextOldPassword = findViewById(R.id.editTextOldPassword);
        editTextNewPassword = findViewById(R.id.editTextNewPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmNewPassword);
        buttonChangePassword = findViewById(R.id.buttonChangePassword);


        buttonChangePassword.setOnClickListener(v -> {
            handleForgetPassword(editTextOldPassword, editTextNewPassword, editTextConfirmPassword);
        });
    }

    protected void handleForgetPassword(EditText oldPasswordField, EditText newPasswordField, EditText confirmPasswordField) {
        String oldPassword = oldPasswordField.getText().toString().trim();
        String newPassword = newPasswordField.getText().toString().trim();
        String confirmPassword = confirmPasswordField.getText().toString().trim();
        int minPasswordLength = 6, maxPasswordLength = 16;

        if (oldPassword.isEmpty()) {
            oldPasswordField.setError("Mật khẩu cũ không thể để trống");
            oldPasswordField.requestFocus();
            return;
        }

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

        if (oldPassword.length() < minPasswordLength || oldPassword.length() > maxPasswordLength) {
            oldPasswordField.setError("Mật khẩu cũ của bạn dài trong khoảng từ " + minPasswordLength + " tới " + maxPasswordLength + " ký tự");
            oldPasswordField.requestFocus();
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

        if (oldPassword.equals(newPassword)) {
            newPasswordField.setError("Mật khẩu mới không được trùng với mật khẩu cũ");
            newPasswordField.requestFocus();
            return;
        }

        oldPasswordField.setError(null);
        newPasswordField.setError(null);
        confirmPasswordField.setError(null);

        Toast.makeText(this, "Đổi mật khẩu thành công.", Toast.LENGTH_LONG).show();
    }

}