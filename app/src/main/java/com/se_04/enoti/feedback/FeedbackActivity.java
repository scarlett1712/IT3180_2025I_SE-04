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
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONObject;

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
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
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

        btnSend.setOnClickListener(v -> {
            String content = edtContent.getText() != null ? edtContent.getText().toString().trim() : "";
            if (content.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập nội dung phản hồi", Toast.LENGTH_SHORT).show();
                return;
            }

            long notificationId = getIntent().getLongExtra("notification_id", -1);
            long userId = getIntent().getLongExtra("user_id", -1);

            if (notificationId == -1 || userId == -1) {
                Toast.makeText(this, "Thiếu dữ liệu phản hồi", Toast.LENGTH_SHORT).show();
                return;
            }

            // 🔹 Nếu có file, lấy URL tạm hoặc null
            String fileUrl = attachedFileUri != null ? attachedFileUri.toString() : null;

            // 🔹 Tạo JSON body
            JSONObject body = new JSONObject();
            try {
                body.put("notification_id", notificationId);
                body.put("user_id", userId);
                body.put("content", content);
                body.put("file_url", fileUrl);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 🔹 Gửi request POST
            String apiUrl = ApiConfig.BASE_URL + "/api/feedback"; // ⚠️ thay bằng URL thật, ví dụ: https://enoti-server.onrender.com/api/feedback

            RequestQueue queue = Volley.newRequestQueue(this);
            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    apiUrl,
                    body,
                    response -> {
                        Toast.makeText(this, "Gửi phản hồi thành công", Toast.LENGTH_SHORT).show();
                        finish();
                    },
                    error -> {
                        error.printStackTrace();
                        Toast.makeText(this, "Lỗi khi gửi phản hồi", Toast.LENGTH_SHORT).show();
                    }
            );

            queue.add(request);
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