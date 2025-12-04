package com.se_04.enoti.residents;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class ResidentDetailActivity extends AppCompatActivity {

    private TextView txtName, txtGender, txtDob, txtEmail, txtPhone,
            txtRelationship, txtLiving, txtRoom;
    private ImageView imgResident;

    private int userId;
    private boolean isLiving;

    // API URLs
    private static final String API_UPDATE = ApiConfig.BASE_URL + "/api/residents/update/";
    private static final String API_DELETE = ApiConfig.BASE_URL + "/api/residents/delete/";
    private static final String API_STATUS = ApiConfig.BASE_URL + "/api/residents/status/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_resident_detail);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("Chi tiết cư dân");
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        txtName = findViewById(R.id.txtResidentName);
        txtGender = findViewById(R.id.txtResidentGender);
        txtDob = findViewById(R.id.txtResidentDob);
        txtEmail = findViewById(R.id.txtResidentEmail);
        txtPhone = findViewById(R.id.txtResidentPhone);
        txtRelationship = findViewById(R.id.txtResidentRelationship);
        txtLiving = findViewById(R.id.txtResidentLiving);
        txtRoom = findViewById(R.id.txtRoom);
        imgResident = findViewById(R.id.imgResident);

        // Lấy dữ liệu từ Intent
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            userId = bundle.getInt("user_id", -1);
            updateUI(bundle);
            loadResidentAvatar(String.valueOf(userId));
        }
    }

    private void updateUI(Bundle bundle) {
        if (bundle == null) return;
        txtName.setText(bundle.getString("name", ""));
        txtGender.setText(bundle.getString("gender", ""));
        txtDob.setText(bundle.getString("dob", ""));
        txtEmail.setText(bundle.getString("email", ""));
        txtPhone.setText(bundle.getString("phone", ""));
        txtRelationship.setText(bundle.getString("relationship", ""));
        txtRoom.setText(bundle.getString("room", ""));

        isLiving = bundle.getBoolean("is_living", true);
        txtLiving.setText(isLiving ? "Đang sinh sống" : "Đã rời đi");
        txtLiving.setTextColor(isLiving ?
                ContextCompat.getColor(this, android.R.color.holo_green_dark) :
                ContextCompat.getColor(this, android.R.color.holo_red_dark));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_resident_options, menu);
        MenuItem statusItem = menu.findItem(R.id.action_toggle_status);
        if (statusItem != null) {
            statusItem.setTitle(isLiving ? "Ẩn cư dân" : "Kích hoạt lại");
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            showEditDialog();
            return true;
        } else if (id == R.id.action_delete) {
            showDeleteConfirmDialog();
            return true;
        } else if (id == R.id.action_toggle_status) {
            toggleResidentStatus();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_edit_resident, null);
        builder.setView(dialogView);

        // Ánh xạ các trường trong Dialog
        EditText edtName = dialogView.findViewById(R.id.edtEditName);
        EditText edtPhone = dialogView.findViewById(R.id.edtEditPhone);
        EditText edtEmail = dialogView.findViewById(R.id.edtEditEmail);
        EditText edtGender = dialogView.findViewById(R.id.edtEditGender);
        EditText edtDob = dialogView.findViewById(R.id.edtEditDob);
        EditText edtIdentity = dialogView.findViewById(R.id.edtEditIdentityCard);
        EditText edtHomeTown = dialogView.findViewById(R.id.edtEditHomeTown);
        Button btnSave = dialogView.findViewById(R.id.btnSaveEdit);

        // Fill dữ liệu hiện tại vào Dialog
        edtName.setText(txtName.getText());
        edtPhone.setText(txtPhone.getText());
        edtEmail.setText(txtEmail.getText());
        edtGender.setText(txtGender.getText());
        // Lưu ý: Ngày sinh đang hiển thị dạng DD-MM-YYYY, nhưng có thể cần convert nếu muốn gửi YYYY-MM-DD
        edtDob.setText(txtDob.getText());

        // CCCD và Quê quán nếu chưa có trên UI chính thì để trống hoặc lấy từ Intent nếu có
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            edtIdentity.setText(bundle.getString("identity_card", ""));
            edtHomeTown.setText(bundle.getString("home_town", ""));
        }

        AlertDialog dialog = builder.create();

        btnSave.setOnClickListener(v -> {
            updateResidentInfo(
                    edtName.getText().toString().trim(),
                    edtPhone.getText().toString().trim(),
                    edtEmail.getText().toString().trim(),
                    edtGender.getText().toString().trim(),
                    edtDob.getText().toString().trim(),
                    edtIdentity.getText().toString().trim(),
                    edtHomeTown.getText().toString().trim(),
                    dialog
            );
        });

        dialog.show();
    }

    private void updateResidentInfo(String name, String phone, String email, String gender, String dob, String identity, String homeTown, AlertDialog dialog) {
        JSONObject body = new JSONObject();
        try {
            body.put("full_name", name);
            body.put("phone", phone);
            body.put("email", email);
            body.put("gender", gender);

            // Convert Date nếu cần thiết (Backend thường nhận YYYY-MM-DD)
            // Ở đây gửi nguyên chuỗi, backend cần xử lý hoặc user nhập đúng format
            body.put("dob", dob);

            body.put("identity_card", identity);
            body.put("home_town", homeTown);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, API_UPDATE + userId, body,
                response -> {
                    Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();

                    // Cập nhật UI ngay lập tức
                    txtName.setText(name);
                    txtPhone.setText(phone);
                    txtEmail.setText(email);
                    txtGender.setText(gender);
                    txtDob.setText(dob);

                    dialog.dismiss();
                    setResult(RESULT_OK);
                },
                error -> {
                    String errorMsg = "Lỗi cập nhật";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String errorData = new String(error.networkResponse.data);
                            JSONObject errorJson = new JSONObject(errorData);
                            errorMsg = errorJson.optString("error", errorMsg);
                        } catch (Exception e) {}
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                }
        );
        Volley.newRequestQueue(this).add(request);
    }

    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa cư dân?")
                .setMessage("Hành động này không thể hoàn tác. Bạn có chắc chắn muốn xóa cư dân này vĩnh viễn?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteResident())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteResident() {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.DELETE, API_DELETE + userId, null,
                response -> {
                    Toast.makeText(this, "Đã xóa cư dân.", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                },
                error -> Toast.makeText(this, "Lỗi khi xóa.", Toast.LENGTH_SHORT).show()
        );
        Volley.newRequestQueue(this).add(request);
    }

    private void toggleResidentStatus() {
        boolean newStatus = !isLiving;
        JSONObject body = new JSONObject();
        try {
            body.put("is_living", newStatus);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, API_STATUS + userId, body,
                response -> {
                    isLiving = newStatus;
                    String msg = isLiving ? "Đã kích hoạt lại." : "Đã ẩn cư dân.";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

                    txtLiving.setText(isLiving ? "Đang sinh sống" : "Đã rời đi");
                    txtLiving.setTextColor(isLiving ?
                            ContextCompat.getColor(this, android.R.color.holo_green_dark) :
                            ContextCompat.getColor(this, android.R.color.holo_red_dark));

                    invalidateOptionsMenu();
                    setResult(RESULT_OK);
                },
                error -> Toast.makeText(this, "Lỗi cập nhật trạng thái.", Toast.LENGTH_SHORT).show()
        );
        Volley.newRequestQueue(this).add(request);
    }

    private void loadResidentAvatar(String userId) {
        try {
            File avatarFile = getAvatarFile(userId);
            if (avatarFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
                imgResident.setImageBitmap(bitmap);
            } else {
                String gender = getIntent().getExtras().getString("gender", "");
                setDefaultAvatar(gender);
            }
        } catch (Exception e) {
            e.printStackTrace();
            String gender = getIntent().getExtras().getString("gender", "");
            setDefaultAvatar(gender);
        }
    }

    private void setDefaultAvatar(String gender) {
        if (gender != null && gender.toLowerCase().contains("nữ")) {
            imgResident.setImageResource(R.drawable.ic_person_female);
        } else {
            imgResident.setImageResource(R.drawable.ic_person);
        }
    }

    private File getAvatarFile(String userId) {
        File avatarDir = new File(getFilesDir(), "avatars");
        return new File(avatarDir, userId + ".jpg");
    }
}