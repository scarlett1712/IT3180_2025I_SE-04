package com.se_04.enoti.maintenance.admin;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.maintenance.AssetImageAdapter; // Import Adapter mới
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AddAssetActivity extends BaseActivity {

    private TextInputEditText edtName, edtLocation, edtDate;
    private Button btnAdd, btnSelectImages;
    private RecyclerView recyclerImages;

    private AssetImageAdapter imageAdapter;
    private final List<String> selectedImageUris = new ArrayList<>(); // List URI để hiển thị
    private final List<String> base64Images = new ArrayList<>();      // List Base64 để gửi đi

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_asset);

        // Ánh xạ View
        edtName = findViewById(R.id.edtAssetName);
        edtLocation = findViewById(R.id.edtAssetLocation);
        edtDate = findViewById(R.id.edtPurchaseDate);
        btnAdd = findViewById(R.id.btnAddAsset);

        // 🔥 Các view mới cho ảnh (Bạn cần thêm vào XML)
        btnSelectImages = findViewById(R.id.btnSelectImages);
        recyclerImages = findViewById(R.id.recyclerSelectedImages);

        setupRecyclerView();

        btnSelectImages.setOnClickListener(v -> openImagePicker());
        btnAdd.setOnClickListener(v -> submitAsset());
    }

    private void setupRecyclerView() {
        if (recyclerImages != null) {
            recyclerImages.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            imageAdapter = new AssetImageAdapter(selectedImageUris, null); // Null listener vì preview không cần click
            recyclerImages.setAdapter(imageAdapter);
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // 🔥 Cho phép chọn nhiều
        imagePickerLauncher.launch(Intent.createChooser(intent, "Chọn ảnh thiết bị"));
    }

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUris.clear();
                    base64Images.clear();

                    if (result.getData().getClipData() != null) {
                        // Trường hợp chọn nhiều ảnh
                        ClipData clipData = result.getData().getClipData();
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            Uri uri = clipData.getItemAt(i).getUri();
                            processImageUri(uri);
                        }
                    } else if (result.getData().getData() != null) {
                        // Trường hợp chọn 1 ảnh
                        Uri uri = result.getData().getData();
                        processImageUri(uri);
                    }

                    if (imageAdapter != null) imageAdapter.notifyDataSetChanged();

                    if (recyclerImages != null) {
                        recyclerImages.setVisibility(selectedImageUris.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                }
            }
    );

    private void processImageUri(Uri uri) {
        selectedImageUris.add(uri.toString());
        // Chuyển đổi sang Base64
        String base64 = uriToBase64(uri);
        if (base64 != null) {
            base64Images.add(base64);
        }
    }

    private String uriToBase64(Uri uri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            // Nén ảnh xuống chất lượng 70% để giảm tải server
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
            byte[] byteArray = outputStream.toByteArray();
            return Base64.encodeToString(byteArray, Base64.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void submitAsset() {
        String name = edtName.getText().toString().trim();
        String location = edtLocation.getText().toString().trim();
        String date = edtDate.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(location)) {
            Toast.makeText(this, "Vui lòng nhập tên và vị trí", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable nút để tránh spam click
        btnAdd.setEnabled(false);
        btnAdd.setText("Đang xử lý...");

        String url = ApiConfig.BASE_URL + "/api/maintenance/assets";
        JSONObject body = new JSONObject();
        try {
            body.put("asset_name", name);
            body.put("location", location);
            body.put("purchase_date", date);
            body.put("status", "Good");

            // 🔥 Thêm mảng ảnh vào JSON Body
            if (!base64Images.isEmpty()) {
                JSONArray imgArray = new JSONArray();
                for (String b64 : base64Images) {
                    // Cloudinary cần prefix này
                    imgArray.put("data:image/jpeg;base64," + b64);
                }
                body.put("images", imgArray);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(this, "Thêm thiết bị thành công!", Toast.LENGTH_SHORT).show();
                    finish();
                },
                error -> {
                    Toast.makeText(this, "Lỗi khi thêm thiết bị", Toast.LENGTH_SHORT).show();
                    btnAdd.setEnabled(true);
                    btnAdd.setText("Thêm thiết bị");
                }
        );

        // Tăng timeout vì upload ảnh có thể lâu
        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Volley.newRequestQueue(this).add(request);
    }
}