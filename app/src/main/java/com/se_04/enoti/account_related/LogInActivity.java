package com.se_04.enoti.account_related;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.isValidVietnamesePhoneNumber;
import static com.se_04.enoti.utils.ValidatePhoneNumberUtil.normalizePhoneNumber;

import com.se_04.enoti.R;
import com.se_04.enoti.account.Gender;
import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.home.admin.MainActivity_Admin;
import com.se_04.enoti.home.user.MainActivity_User;
import com.se_04.enoti.utils.UserManager;

public class LogInActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_in);

        final EditText editTextPhone = findViewById(R.id.enterPhoneNumber);
        final EditText editTextPassword = findViewById(R.id.enterPassword);
        Button loginButton = findViewById(R.id.buttonSignIn);
        TextView textViewForgotPassword = findViewById(R.id.forgetPassword);
        TextView textViewChangePassword = findViewById(R.id.changePassword);
        TextView textViewRegister = findViewById(R.id.textViewRegister);

        // Đăng nhập
        loginButton.setOnClickListener(v -> handleLogin(editTextPhone, editTextPassword));

        // Quên mật khẩu
        textViewForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LogInActivity.this, ForgetPasswordEnterPhoneActivity.class);
            startActivity(intent);
        });

        // Đổi mật khẩu
        textViewChangePassword.setOnClickListener(v -> {
            Intent intent = new Intent(LogInActivity.this, ChangePasswordActivity.class);
            startActivity(intent);
        });

        // Đăng ký
        textViewRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LogInActivity.this, RegisterEnterPhoneActivity.class);
            startActivity(intent);
        });
    }

    private void handleLogin(EditText phoneField, EditText passwordField) {
        String phone = phoneField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();
        int minPasswordLength = 6, maxPasswordLength = 16;

        // 🔹 Kiểm tra số điện thoại
        if (!isValidVietnamesePhoneNumber(phone)) {
            phoneField.setError("Nhập số điện thoại chính xác.");
            phoneField.requestFocus();
            return;
        } else {
            phone = normalizePhoneNumber(phone);
        }

        // 🔹 Kiểm tra mật khẩu
        if (password.isEmpty()) {
            passwordField.setError("Mật khẩu không thể để trống");
            passwordField.requestFocus();
            return;
        }

        if (password.length() < minPasswordLength || password.length() > maxPasswordLength) {
            passwordField.setError("Mật khẩu phải dài từ " + minPasswordLength + " đến " + maxPasswordLength + " ký tự");
            passwordField.requestFocus();
            return;
        }

        // 🔹 Xác định role và username
        boolean admin = isAdmin(phone);
        Role roleType = admin ? Role.ADMIN : Role.USER;
        String username = admin ? "Quản trị viên" : phone.substring(phone.length() - 3);

        UserItem user = new UserItem(
                phone,              // userId (tạm thời dùng số điện thoại làm id)
                "FAMILY001",        // familyId (nếu có thể lấy từ DB thì thay vào)
                admin ? "admin@enoti.com" : phone + "@gmail.com", // email
                username,           // tên hiển thị
                "01-01-2000",       // ngày sinh (tạm)
                Gender.MALE,        // hoặc FEMALE nếu có thông tin
                admin ? "Quản trị viên" : "Thành viên", // mối quan hệ
                roleType,
                phone               // số điện thoại
        );

        UserManager userManager = UserManager.getInstance(this);
        userManager.saveCurrentUser(user);
        userManager.setLoggedIn(true);



        // 🔹 Thông báo và chuyển màn hình
        Toast.makeText(this, "Đăng nhập thành công. Xin chào " + username, Toast.LENGTH_LONG).show();

        Intent intent = admin
                ? new Intent(this, MainActivity_Admin.class)
                : new Intent(this, MainActivity_User.class);

        startActivity(intent);
        finish();
    }

    private boolean isAdmin(String normalizedPhoneNumber) {
        return normalizedPhoneNumber.equals("+84936363636");
    }
}
