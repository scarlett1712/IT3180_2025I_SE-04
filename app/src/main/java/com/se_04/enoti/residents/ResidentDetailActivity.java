package com.se_04.enoti.residents;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
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
import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ResidentDetailActivity extends BaseActivity {

    private TextView tvName, tvGender, tvDob, tvJob, tvIdentity, tvHomeTown;
    private TextView tvEmail, tvPhone;
    private TextView tvRoom, tvRelationship, tvRole;
    private TextView txtResidentLiving;
    private ImageView imgResident;

    private int userId;
    private boolean isLiving;
    private boolean isAgency = false;

    private static final String API_UPDATE = ApiConfig.BASE_URL + "/api/residents/update/";
    private static final String API_DELETE = ApiConfig.BASE_URL + "/api/residents/delete/";
    private static final String API_STATUS = ApiConfig.BASE_URL + "/api/residents/status/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_detail);

        UserItem currentUser = UserManager.getInstance(this).getCurrentUser();
        if (currentUser != null && currentUser.getRole() == Role.AGENCY) {
            isAgency = true;
        }

        setupToolbar();
        initViews();

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
        imgResident = findViewById(R.id.imgResident);
        tvName = findViewById(R.id.txtResidentName);
        txtResidentLiving = findViewById(R.id.txtResidentLiving);

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
        if (tvName != null) tvName.setText(getValidText(bundle.getString("name", "")));
        if (tvGender != null) tvGender.setText(getValidText(bundle.getString("gender", "")));
        if (tvDob != null) tvDob.setText(getValidText(bundle.getString("dob", "")));
        if (tvJob != null) tvJob.setText(getValidText(bundle.getString("job", "")));

        String identity = getValidText(bundle.getString("identity_card", ""));
        if (tvIdentity != null) tvIdentity.setText(identity.isEmpty() ? "Chưa cập nhật" : identity);

        String homeTown = getValidText(bundle.getString("home_town", ""));
        if (tvHomeTown != null) tvHomeTown.setText(homeTown.isEmpty() ? "Chưa cập nhật" : homeTown);

        if (tvEmail != null) tvEmail.setText(getValidText(bundle.getString("email", "")));
        if (tvPhone != null) tvPhone.setText(getValidText(bundle.getString("phone", "")));
        if (tvRoom != null) tvRoom.setText(getValidText(bundle.getString("room", "")));
        if (tvRelationship != null) tvRelationship.setText(getValidText(bundle.getString("relationship", "")));
        if (tvRole != null) tvRole.setText("Cư dân");

        isLiving = bundle.getBoolean("is_living", true);
        if (txtResidentLiving != null) {
            txtResidentLiving.setText(isLiving ? "Đang sinh sống" : "Đã rời đi");
            txtResidentLiving.setTextColor(isLiving ?
                    ContextCompat.getColor(this, android.R.color.holo_green_dark) :
                    ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!isAgency) {
            getMenuInflater().inflate(R.menu.menu_resident_options, menu);
            MenuItem statusItem = menu.findItem(R.id.action_toggle_status);
            if (statusItem != null) {
                statusItem.setTitle(isLiving ? "Ẩn cư dân" : "Kích hoạt lại");
            }
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                CharSequence title = item.getTitle();
                if (title != null) {
                    SpannableString spanString = new SpannableString(title);
                    spanString.setSpan(new ForegroundColorSpan(android.graphics.Color.BLACK), 0, spanString.length(), 0);
                    item.setTitle(spanString);
                }
            }
            return true;
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
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showEditDialog() {
        if (isAgency) return;

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

        edtPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        edtPhone.setFilters(new InputFilter[] { new InputFilter.LengthFilter(12) });

        edtIdentity.setInputType(InputType.TYPE_CLASS_NUMBER);
        edtIdentity.setFilters(new InputFilter[] { new InputFilter.LengthFilter(12) });

        edtDob.setFocusable(false);
        edtDob.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            DatePickerDialog dpd = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String selectedDate = String.format(Locale.getDefault(), "%02d-%02d-%d", dayOfMonth, month + 1, year);
                edtDob.setText(selectedDate);
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
            dpd.getDatePicker().setMaxDate(System.currentTimeMillis());
            dpd.show();
        });

        if (tvName != null) edtName.setText(tvName.getText());
        if (tvPhone != null) edtPhone.setText(tvPhone.getText());
        if (tvEmail != null) edtEmail.setText(tvEmail.getText());
        if (tvGender != null) edtGender.setText(tvGender.getText());
        if (tvDob != null) edtDob.setText(tvDob.getText());
        if (tvJob != null) edtJob.setText(tvJob.getText());
        if (tvIdentity != null && !tvIdentity.getText().toString().equals("Chưa cập nhật")) {
            edtIdentity.setText(tvIdentity.getText());
        }
        if (tvHomeTown != null && !tvHomeTown.getText().toString().equals("Chưa cập nhật")) {
            edtHomeTown.setText(tvHomeTown.getText());
        }

        AlertDialog dialog = builder.create();

        btnSave.setOnClickListener(v -> {
            String phoneInput = edtPhone.getText().toString().trim();
            String identityInput = edtIdentity.getText().toString().trim();

            if (!isValidPhoneNumber(phoneInput)) {
                Toast.makeText(this, "Số điện thoại không hợp lệ!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!identityInput.isEmpty() && identityInput.length() != 9 && identityInput.length() != 12) {
                Toast.makeText(this, "Số CCCD/CMND phải là 9 hoặc 12 chữ số!", Toast.LENGTH_SHORT).show();
                edtIdentity.requestFocus();
                return;
            }

            String normalizedPhone = phoneInput.replace("+84", "0");

            updateResidentInfo(
                    edtName.getText().toString().trim(),
                    normalizedPhone,
                    edtEmail.getText().toString().trim(),
                    edtGender.getText().toString().trim(),
                    edtDob.getText().toString().trim(),
                    edtJob.getText().toString().trim(),
                    identityInput,
                    edtHomeTown.getText().toString().trim(),
                    dialog
            );
        });

        dialog.show();
    }

    private boolean isValidPhoneNumber(String phone) {
        if (phone == null) return false;
        return phone.matches("^(0|\\+84)[0-9]{9}$");
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
                    // Cập nhật lại UI sau khi lưu
                    if (tvName != null) tvName.setText(name);
                    if (tvPhone != null) tvPhone.setText(phone);
                    if (tvEmail != null) tvEmail.setText(email);
                    if (tvGender != null) tvGender.setText(gender);
                    if (tvDob != null) tvDob.setText(dob);
                    if (tvJob != null) tvJob.setText(job);
                    if (tvIdentity != null) tvIdentity.setText(identity.isEmpty() ? "Chưa cập nhật" : identity);
                    if (tvHomeTown != null) tvHomeTown.setText(homeTown.isEmpty() ? "Chưa cập nhật" : homeTown);

                    dialog.dismiss();
                    setResult(RESULT_OK);
                },
                error -> {
                    if (error.networkResponse != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            JSONObject data = new JSONObject(responseBody);

                            // Tìm message từ các key phổ biến
                            String serverMsg = data.optString("message", data.optString("error", ""));

                            if (serverMsg.isEmpty() && (error.networkResponse.statusCode == 400 || error.networkResponse.statusCode == 409)) {
                                serverMsg = "SĐT hoặc CCCD này đã thuộc về cư dân khác!";
                            }

                            String lowerMsg = serverMsg.toLowerCase();
                            if (lowerMsg.contains("phone") || lowerMsg.contains("số điện thoại")) {
                                Toast.makeText(this, "Số điện thoại này đã tồn tại!", Toast.LENGTH_LONG).show();
                            } else if (lowerMsg.contains("identity") || lowerMsg.contains("cccd") || lowerMsg.contains("cmnd")) {
                                Toast.makeText(this, "Số CCCD này đã tồn tại!", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, "Lỗi: " + serverMsg, Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "Cập nhật thất bại: Dữ liệu bị trùng lặp!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(this, "Không thể kết nối máy chủ!", Toast.LENGTH_SHORT).show();
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
        new AlertDialog.Builder(this).setTitle("Xóa cư dân?").setMessage("Hành động này không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) -> deleteResident()).setNegativeButton("Hủy", null).show();
    }

    private void deleteResident() {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.DELETE, API_DELETE + userId, null,
                response -> { Toast.makeText(this, "Đã xóa.", Toast.LENGTH_SHORT).show(); setResult(RESULT_OK); finish(); },
                error -> Toast.makeText(this, "Lỗi khi xóa.", Toast.LENGTH_SHORT).show()
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
        try { body.put("is_living", newStatus); } catch (JSONException e) { e.printStackTrace(); }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, API_STATUS + userId, body,
                response -> {
                    isLiving = newStatus;
                    txtResidentLiving.setText(isLiving ? "Đang sinh sống" : "Đã rời đi");
                    txtResidentLiving.setTextColor(isLiving ? ContextCompat.getColor(this, android.R.color.holo_green_dark) : ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    invalidateOptionsMenu();
                    setResult(RESULT_OK);
                },
                error -> Toast.makeText(this, "Lỗi!", Toast.LENGTH_SHORT).show()
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
            if (avatarFile.exists()) imgResident.setImageBitmap(BitmapFactory.decodeFile(avatarFile.getAbsolutePath()));
            else setDefaultAvatar(getIntent().getExtras().getString("gender", ""));
        } catch (Exception e) { setDefaultAvatar(getIntent().getExtras().getString("gender", "")); }
    }

    private void setDefaultAvatar(String gender) {
        if (gender != null && gender.toLowerCase().contains("nữ")) imgResident.setImageResource(R.drawable.ic_person_female);
        else imgResident.setImageResource(R.drawable.ic_person);
    }

    private File getAvatarFile(String userId) {
        File avatarDir = new File(getFilesDir(), "avatars");
        return new File(avatarDir, userId + ".jpg");
    }

    private String getValidText(String value) {
        if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("null")) return "Không";
        return value;
    }
}