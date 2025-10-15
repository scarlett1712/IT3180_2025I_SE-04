package com.se_04.enoti.feedback;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;

import java.io.IOException;

public class FeedbackActivity extends AppCompatActivity {

    private TextInputEditText edtContent;
    private TextView txtTitle;
    private ImageView imgPreview;
    private Uri attachedFileUri;

    private ActivityResultLauncher<String[]> filePickerLauncher;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_feedback);

        // Ánh xạ view
        MaterialToolbar toolbar = findViewById(R.id.toolbar_feedback);
        txtTitle = findViewById(R.id.txtFeedbackTitle);
        edtContent = findViewById(R.id.edtFeedbackContent);
        MaterialButton btnSend = findViewById(R.id.btnSendFeedback);
        MaterialButton btnAttach = findViewById(R.id.btnAttach);
        imgPreview = findViewById(R.id.imgPreview);

        // Toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Phản hồi");
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        // Nhận dữ liệu từ Intent
        String title = getIntent().getStringExtra("title");
        int position = getIntent().getIntExtra("position", -1);
        txtTitle.setText(title != null && !title.isEmpty()
                ? "Phản hồi cho: " + title
                : "Phản hồi thông báo");

        // Khởi tạo pickers
        setupFilePickers();

        // Xử lý nút Đính kèm
        btnAttach.setOnClickListener(v -> showAttachmentOptions());

        // Xử lý nút Gửi phản hồi
        btnSend.setOnClickListener(v -> {
            String content = edtContent.getText() != null ? edtContent.getText().toString().trim() : "";
            if (content.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập nội dung phản hồi", Toast.LENGTH_SHORT).show();
                return;
            }

            String message = "Phản hồi đã được gửi";
            if (position != -1) message += " cho thông báo #" + position;
            if (attachedFileUri != null) message += " (kèm tệp)";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            edtContent.setText("");
            imgPreview.setVisibility(View.GONE);
            attachedFileUri = null;
            finish();
        });
    }

    private void setupFilePickers() {
        // 1️⃣ Chọn ảnh từ thư viện
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        attachedFileUri = uri;
                        imgPreview.setImageURI(uri);
                        imgPreview.setVisibility(View.VISIBLE);
                    }
                });

        // 2️⃣ Chọn tệp bất kỳ
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        attachedFileUri = uri;
                        imgPreview.setVisibility(View.GONE);
                        Toast.makeText(this, "Đã đính kèm: " + uri.getLastPathSegment(), Toast.LENGTH_SHORT).show();
                    }
                });

        // 3️⃣ Mở camera chụp ảnh
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        Bitmap bitmap = (Bitmap) extras.get("data");
                        if (bitmap != null) {
                            imgPreview.setImageBitmap(bitmap);
                            imgPreview.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    private void showAttachmentOptions() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet, null);
        dialog.setContentView(view);

        view.findViewById(R.id.btnPickImage).setOnClickListener(v -> {
            dialog.dismiss();
            imagePickerLauncher.launch("image/*");
        });

        view.findViewById(R.id.btnPickFile).setOnClickListener(v -> {
            dialog.dismiss();
            String[] types = {"image/*", "application/pdf", "application/msword"};
            filePickerLauncher.launch(types);
        });

        view.findViewById(R.id.btnTakePhoto).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(intent);
        });

        dialog.show();
    }
}