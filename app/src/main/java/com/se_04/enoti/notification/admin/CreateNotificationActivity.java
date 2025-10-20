package com.se_04.enoti.notification.admin;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.se_04.enoti.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CreateNotificationActivity extends AppCompatActivity {

    private EditText edtNotificationTitle, edtNotificationContent, edtExpirationDate;
    private Spinner spinnerNotificationType, spinnerNotificationTarget;
    private Button btnSendNotification;

    private Calendar calendar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_notification);

        // Ánh xạ view
        edtNotificationTitle = findViewById(R.id.edtNotificationTitle);
        edtNotificationContent = findViewById(R.id.edtNotificationContent);
        edtExpirationDate = findViewById(R.id.edtExpirationDate);
        spinnerNotificationType = findViewById(R.id.spinnerNotificationType);
        spinnerNotificationTarget = findViewById(R.id.spinnerNotificationTarget);
        btnSendNotification = findViewById(R.id.btnSendNotification);

        calendar = Calendar.getInstance();

        // Spinner: loại thông báo
        String[] notiTypes = {"Thông báo", "Tin khẩn", "Sự kiện", "Bảo trì"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, notiTypes);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerNotificationType.setAdapter(typeAdapter);

        // Spinner: người nhận
        String[] targets = {"Tất cả cư dân", "Tòa A", "Tòa B", "Tầng 1", "Tầng 2"};
        ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, targets);
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerNotificationTarget.setAdapter(targetAdapter);

        // Chọn ngày hết hạn
        edtExpirationDate.setOnClickListener(v -> showDatePickerDialog());

        // Nút gửi
        btnSendNotification.setOnClickListener(v -> createNotification());
    }

    private void showDatePickerDialog() {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (DatePicker view, int selectedYear, int selectedMonth, int selectedDay) -> {
                    calendar.set(selectedYear, selectedMonth, selectedDay);
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    edtExpirationDate.setText(sdf.format(calendar.getTime()));
                },
                year, month, day
        );
        datePickerDialog.show();
    }

    private void createNotification() {
        String title = edtNotificationTitle.getText().toString().trim();
        String content = edtNotificationContent.getText().toString().trim();
        String date = edtExpirationDate.getText().toString().trim();
        String type = spinnerNotificationType.getSelectedItem().toString();
        String target = spinnerNotificationTarget.getSelectedItem().toString();

        if (title.isEmpty() || content.isEmpty() || date.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        // Giả lập lưu thông báo (sau này có thể thay bằng lưu DB hoặc gửi API)
        SharedPreferences prefs = getSharedPreferences("Notifications", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String key = "notification_" + System.currentTimeMillis();

        String data = "Tiêu đề: " + title + "\n"
                + "Loại: " + type + "\n"
                + "Nội dung: " + content + "\n"
                + "Ngày hết hạn: " + date + "\n"
                + "Gửi đến: " + target;

        editor.putString(key, data);
        editor.apply();

        Toast.makeText(this, "Tạo thông báo thành công!", Toast.LENGTH_SHORT).show();

        // Reset form
        edtNotificationTitle.setText("");
        edtNotificationContent.setText("");
        edtExpirationDate.setText("");
        spinnerNotificationType.setSelection(0);
        spinnerNotificationTarget.setSelection(0);
    }
}