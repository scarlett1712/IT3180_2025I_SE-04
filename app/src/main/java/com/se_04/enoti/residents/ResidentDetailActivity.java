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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.account.Role; // Import Role enum
import com.se_04.enoti.account.UserItem; // Import UserItem
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ResidentDetailActivity extends BaseActivity {

    // Các TextView hiển thị giá trị (Value)
    private TextView tvName, tvGender, tvDob, tvJob, tvIdentity, tvHomeTown;
    private TextView tvEmail, tvPhone;
    private TextView tvRoom, tvRelationship, tvRole; // tvRole lấy từ layout rowRole
    private TextView txtResidentLiving;
    private ImageView imgResident;

    private int userId;
    private boolean isLiving;

    // 🔥 BIẾN KIỂM TRA QUYỀN AGENCY
    private boolean isAgency = false;

    // API URLs
    private static final String API_UPDATE = ApiConfig.BASE_URL + "/api/residents/update/";
    private static final String API_DELETE = ApiConfig.BASE_URL + "/api/residents/delete/";
    private static final String API_STATUS = ApiConfig.BASE_URL + "/api/residents/status/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_detail);

        // 🔥 KIỂM TRA ROLE NGAY KHI KHỞI TẠO
        UserItem currentUser = UserManager.getInstance(this).getCurrentUser();
        if (currentUser != null && currentUser.getRole() == Role.AGENCY) {
            isAgency = true;
        }

        setupToolbar();
        initViews();

        // Lấy dữ liệu từ Intent
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            userId = bundle.getInt("user_id", -1);
            updateUI(bundle);
            loadResidentAvatar(String.valueOf(userId));
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_resident_detail);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết cư dân");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void initViews() {
        // 1. Ánh xạ các View cố định
        imgResident = findViewById(R.id.imgResident);
        tvName = findViewById(R.id.txtResidentName);
        txtResidentLiving = findViewById(R.id.txtResidentLiving);

        // 2. Ánh xạ các dòng <include> bằng hàm helper
        // Hàm này sẽ gán Label cho dòng đó và trả về TextView Value để ta set dữ liệu sau này
        tvGender = setupRow(R.id.rowGender, "Giới tính");
        tvDob = setupRow(R.id.rowDob, "Ngày sinh");
        tvJob = setupRow(R.id.rowJob, "Nghề nghiệp");
        tvIdentity = setupRow(R.id.rowIdentity, "CCCD/CMND");
        tvHomeTown = setupRow(R.id.rowHomeTown, "Quê quán");

        tvEmail = setupRow(R.id.rowEmail, "Email");
        tvPhone = setupRow(R.id.rowPhone, "Số điện thoại");

        tvRoom = setupRow(R.id.rowRoom, "Căn hộ");
        tvRelationship = setupRow(R.id.rowRelationship, "Quan hệ chủ hộ");
        tvRole = setupRow(R.id.rowRole, "Vai trò");
    }

    /**
     * Hàm hỗ trợ tìm View trong layout <include>
     */
    private TextView setupRow(int includeId, String labelText) {
        View rowView = findViewById(includeId);
        if (rowView != null) {
            TextView lbl = rowView.findViewById(R.id.txtLabel);
            TextView val = rowView.findViewById(R.id.txtValue);
            if (lbl != null) lbl.setText(labelText);
            return val;
        }
        return null;
    }

    private void updateUI(Bundle bundle) {
        if (bundle == null) return;

        // Set text an toàn (kiểm tra null)
        if (tvName != null) tvName.setText(bundle.getString("Tên", ""));

        if (tvGender != null) tvGender.setText(bundle.getString("Giới tính", ""));
        if (tvDob != null) tvDob.setText(bundle.getString("Ngày sinh", ""));
        if (tvJob != null) tvJob.setText(bundle.getString("Nghề nghiệp", ""));

        String identity = bundle.getString("identity_card", "");
        if (tvIdentity != null) tvIdentity.setText(identity.isEmpty() ? "Chưa cập nhật" : identity);

        String homeTown = bundle.getString("home_town", "");
        if (tvHomeTown != null) tvHomeTown.setText(homeTown.isEmpty() ? "Chưa cập nhật" : homeTown);

        if (tvEmail != null) tvEmail.setText(bundle.getString("email", ""));
        if (tvPhone != null) tvPhone.setText(bundle.getString("phone", ""));

        if (tvRoom != null) tvRoom.setText(bundle.getString("room", ""));
        if (tvRelationship != null) tvRelationship.setText(bundle.getString("relationship", ""));

        // Vai trò
        if (tvRole != null) tvRole.setText("Cư dân");

        // Trạng thái sinh sống
        isLiving = bundle.getBoolean("is_living", true);
        if (txtResidentLiving != null) {
            txtResidentLiving.setText(isLiving ? "Đang sinh sống" : "Đã rời đi");
            txtResidentLiving.setTextColor(isLiving ?
                    ContextCompat.getColor(this, android.R.color.holo_green_dark) :
                    ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }
    }

    // --- XỬ LÝ MENU ---

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 🔥 NẾU LÀ AGENCY THÌ KHÔNG HIỆN MENU EDIT/DELETE
        if (!isAgency) {
            getMenuInflater().inflate(R.menu.menu_resident_options, menu);

            // Xử lý đổi tên nút (Ẩn / Kích hoạt)
            android.view.MenuItem statusItem = menu.findItem(R.id.action_toggle_status);
            if (statusItem != null) {
                statusItem.setTitle(isLiving ? "Ẩn cư dân" : "Kích hoạt lại");
            }

            // 🔥🔥🔥 CODE ÉP MÀU CHỮ THÀNH ĐEN (BẮT ĐẦU TỪ ĐÂY) 🔥🔥🔥
            for (int i = 0; i < menu.size(); i++) {
                android.view.MenuItem item = menu.getItem(i);
                CharSequence title = item.getTitle();

                if (title != null) {
                    android.text.SpannableString spanString = new android.text.SpannableString(title);
                    // Ép màu ĐEN (Color.BLACK)
                    spanString.setSpan(new android.text.style.ForegroundColorSpan(android.graphics.Color.BLACK), 0, spanString.length(), 0);
                    item.setTitle(spanString);
                }
            }
            return true;
        }

        // Nếu là Agency, trả về true để hiện menu trống (chỉ có nút Back)
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // 🔥 CHẶN AGENCY THỰC HIỆN HÀNH ĐỘNG (Dù menu ẩn, chặn thêm cho chắc)
        if (isAgency) {
            if (item.getItemId() == android.R.id.home) {
                onBackPressed();
                return true;
            }
            return false;
        }

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

    // --- CÁC HÀM UPDATE/DELETE (Giữ nguyên logic cũ, chỉ được gọi nếu !isAgency) ---

    private void showEditDialog() {
        if (isAgency) return; // Bảo vệ thêm

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_edit_resident, null);
        builder.setView(dialogView);

        EditText edtName = dialogView.findViewById(R.id.edtEditName);
        EditText edtPhone = dialogView.findViewById(R.id.edtEditPhone);
        EditText edtEmail = dialogView.findViewById(R.id.edtEditEmail);
        EditText edtGender = dialogView.findViewById(R.id.edtEditGender);
        EditText edtDob = dialogView.findViewById(R.id.edtEditDob);
        EditText edtJob = dialogView.findViewById(R.id.edtEditJob);
        EditText edtIdentity = dialogView.findViewById(R.id.edtEditIdentityCard);
        EditText edtHomeTown = dialogView.findViewById(R.id.edtEditHomeTown);
        Button btnSave = dialogView.findViewById(R.id.btnSaveEdit);

        // Lấy dữ liệu từ các TextView đã setup
        if (tvName != null) edtName.setText(tvName.getText());
        if (tvPhone != null) edtPhone.setText(tvPhone.getText());
        if (tvEmail != null) edtEmail.setText(tvEmail.getText());
        if (tvGender != null) edtGender.setText(tvGender.getText());
        if (tvDob != null) edtDob.setText(tvDob.getText());

        if (tvIdentity != null && !tvIdentity.getText().toString().equals("Chưa cập nhật")) {
            edtIdentity.setText(tvIdentity.getText());
        }
        if (tvHomeTown != null && !tvHomeTown.getText().toString().equals("Chưa cập nhật")) {
            edtHomeTown.setText(tvHomeTown.getText());
        }

        AlertDialog dialog = builder.create();

        btnSave.setOnClickListener(v -> {
            updateResidentInfo(
                    edtName.getText().toString().trim(),
                    edtPhone.getText().toString().trim(),
                    edtEmail.getText().toString().trim(),
                    edtGender.getText().toString().trim(),
                    edtDob.getText().toString().trim(),
                    edtJob.getText().toString().trim(),
                    edtIdentity.getText().toString().trim(),
                    edtHomeTown.getText().toString().trim(),
                    dialog
            );
        });

        dialog.show();
    }

    private void updateResidentInfo(String name, String phone, String email, String gender, String dob, String job, String identity, String homeTown, AlertDialog dialog) {
        JSONObject body = new JSONObject();
        try {
            body.put("full_name", name);
            body.put("phone", phone);
            body.put("email", email);
            body.put("gender", gender);
            body.put("dob", dob);
            body.put("job", job);
            body.put("identity_card", identity);
            body.put("home_town", homeTown);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, API_UPDATE + userId, body,
                response -> {
                    Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();
                    // Update UI
                    if (tvName != null) tvName.setText(name);
                    if (tvPhone != null) tvPhone.setText(phone);
                    if (tvEmail != null) tvEmail.setText(email);
                    if (tvGender != null) tvGender.setText(gender);
                    if (tvDob != null) tvDob.setText(dob);
                    if (tvJob != null) tvJob.setText(job);
                    if (tvIdentity != null) tvIdentity.setText(identity);
                    if (tvHomeTown != null) tvHomeTown.setText(homeTown);

                    dialog.dismiss();
                    setResult(RESULT_OK);
                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                        UserManager.getInstance(this).checkAndForceLogout(error);
                    } else {
                        Toast.makeText(this, "Lỗi cập nhật: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }

    private void showDeleteConfirmDialog() {
        if (isAgency) return;

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
                error -> {
                    if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                        UserManager.getInstance(this).checkAndForceLogout(error);
                    } else {
                        Toast.makeText(this, "Lỗi khi xóa.", Toast.LENGTH_SHORT).show();
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }

    private void toggleResidentStatus() {
        if (isAgency) return;

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

                    if (txtResidentLiving != null) {
                        txtResidentLiving.setText(isLiving ? "Đang sinh sống" : "Đã rời đi");
                        txtResidentLiving.setTextColor(isLiving ?
                                ContextCompat.getColor(this, android.R.color.holo_green_dark) :
                                ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    }
                    invalidateOptionsMenu();
                    setResult(RESULT_OK);
                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                        UserManager.getInstance(this).checkAndForceLogout(error);
                    } else {
                        Toast.makeText(this, "Lỗi cập nhật trạng thái.", Toast.LENGTH_SHORT).show();
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
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