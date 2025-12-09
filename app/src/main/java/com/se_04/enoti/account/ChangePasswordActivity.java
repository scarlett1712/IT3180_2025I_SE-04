package com.se_04.enoti.account;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem; // Import MenuItem
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;

import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ChangePasswordActivity extends BaseActivity {

    private EditText editTextOldPassword;
    private EditText editTextNewPassword;
    private EditText editTextConfirmPassword;
    private Button buttonChangePassword;
    private Toolbar toolbar;

    private static final String API_URL = ApiConfig.BASE_URL + "/api/changepassword";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        editTextOldPassword = findViewById(R.id.editTextOldPassword);
        editTextNewPassword = findViewById(R.id.editTextNewPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmNewPassword);
        buttonChangePassword = findViewById(R.id.buttonChangePassword);

        buttonChangePassword.setOnClickListener(v -> handleChangePassword());

        toolbar = findViewById(R.id.changePasswordToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Đổi mật khẩu"); // FIX: Correct title
        }
    }

    // FIX: Add this method to handle toolbar item clicks
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle arrow click here
        if (item.getItemId() == android.R.id.home) {
            finish(); // Close this activity and return to previous one
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleChangePassword() {
        String oldPassword = editTextOldPassword.getText().toString().trim();
        String newPassword = editTextNewPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();

        int minPasswordLength = 6, maxPasswordLength = 16;

        if (oldPassword.isEmpty()) {
            editTextOldPassword.setError("Vui lòng nhập mật khẩu cũ");
            editTextOldPassword.requestFocus();
            return;
        }

        if (newPassword.isEmpty()) {
            editTextNewPassword.setError("Mật khẩu mới không thể để trống");
            editTextNewPassword.requestFocus();
            return;
        }

        if (confirmPassword.isEmpty()) {
            editTextConfirmPassword.setError("Mật khẩu xác nhận không thể để trống");
            editTextConfirmPassword.requestFocus();
            return;
        }

        if (newPassword.length() < minPasswordLength || newPassword.length() > maxPasswordLength) {
            editTextNewPassword.setError("Mật khẩu phải dài từ " + minPasswordLength + " đến " + maxPasswordLength + " ký tự");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            editTextConfirmPassword.setError("Mật khẩu xác nhận không khớp");
            return;
        }

        if (oldPassword.equals(newPassword)) {
            editTextNewPassword.setError("Mật khẩu mới không được trùng với mật khẩu cũ");
            return;
        }

        UserItem currentUser = UserManager.getInstance(getApplicationContext()).getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Không tìm thấy thông tin người dùng", Toast.LENGTH_LONG).show();
            return;
        }

        int userId;
        try {
            userId = Integer.parseInt(currentUser.getId());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "ID người dùng không hợp lệ", Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(() -> sendChangePasswordRequest(userId, oldPassword, newPassword)).start();
    }

    private void sendChangePasswordRequest(int userId, String oldPassword, String newPassword) {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("old_password", oldPassword);
            body.put("new_password", newPassword);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream()
            ));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();

            conn.disconnect();

            String message;

            try {
                JSONObject jsonResponse = new JSONObject(response.toString());
                message = jsonResponse.optString("message", "Không rõ phản hồi từ máy chủ");
            } catch (Exception e) {
                Log.e("CHANGE_PASSWORD", "Phản hồi không phải JSON: " + response.toString());
                message = "Lỗi máy chủ hoặc sai endpoint. Kiểm tra lại đường dẫn API.";
            }

            String finalMessage = message;
            runOnUiThread(() -> {
                if (responseCode == 200) {
                    Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() ->
                    Toast.makeText(this, "Lỗi khi kết nối server: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    }
}
