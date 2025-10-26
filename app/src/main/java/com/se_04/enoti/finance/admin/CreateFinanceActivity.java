package com.se_04.enoti.finance.admin;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.residents.ResidentAdapter;
import com.se_04.enoti.residents.ResidentItem;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CreateFinanceActivity extends AppCompatActivity {

    private TextInputEditText edtFinanceTitle, edtFinanceContent, edtAmount, edtDueDate;
    private Spinner spinnerFinanceType;
    private Button btnCreateFee;
    private RecyclerView recyclerResidents;
    private ResidentAdapter residentAdapter;
    private TextView txtSelectedResidents;

    private final List<ResidentItem> allResidents = new ArrayList<>();
    private Set<ResidentItem> selectedResidents = new HashSet<>();

    private static final String API_GET_RESIDENTS_URL = ApiConfig.BASE_URL + "/api/residents";
    private static final String API_CREATE_FINANCE_URL = ApiConfig.BASE_URL + "/api/finance/create";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_finance);

        Toolbar toolbar = findViewById(R.id.toolbar_finance);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Tạo khoản thu mới");
        toolbar.setNavigationOnClickListener(v -> finish());

        // Ánh xạ views
        edtFinanceTitle = findViewById(R.id.edtFinanceTitle);
        edtFinanceContent = findViewById(R.id.edtFinanceContent);
        edtAmount = findViewById(R.id.edtAmount);
        edtDueDate = findViewById(R.id.edtExpirationDate);
        spinnerFinanceType = findViewById(R.id.spinnerFinanceType);
        btnCreateFee = findViewById(R.id.btnCreateFee);
        recyclerResidents = findViewById(R.id.recyclerResidents);
        txtSelectedResidents = findViewById(R.id.txtSelectedResidents);

        setupSpinner();
        setupResidentList();
        setupDueDate();

        btnCreateFee.setOnClickListener(v -> createFee());

        fetchResidentsFromAPI();
    }

    private void setupSpinner() {
        String[] types = {"Bắt buộc", "Tự nguyện"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFinanceType.setAdapter(adapter);
    }

    private void setupDueDate() {
        edtDueDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, month + 1, year);
                edtDueDate.setText(selectedDate);
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
            dialog.show();
        });
    }

    private void setupResidentList() {
        recyclerResidents.setLayoutManager(new LinearLayoutManager(this));
        residentAdapter = new ResidentAdapter(new ArrayList<>(), ResidentAdapter.MODE_SELECT_FOR_NOTIFICATION, selected -> {
            selectedResidents = selected;
            updateSelectedResidentsDisplay();
        });
        recyclerResidents.setAdapter(residentAdapter);
    }
    
    private void fetchResidentsFromAPI() {
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, API_GET_RESIDENTS_URL, null,
                response -> {
                    try {
                        allResidents.clear();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            if (obj.optInt("role_id", 0) == 1) { // Chỉ lấy user
                                allResidents.add(new ResidentItem(
                                    obj.optInt("user_item_id"),
                                    obj.optInt("user_id"),
                                    obj.optString("full_name"),
                                    obj.optString("gender"),
                                    obj.optString("dob"),
                                    obj.optString("email"),
                                    obj.optString("phone"),
                                    obj.optString("relationship_with_the_head_of_household"),
                                    obj.optString("family_id"),
                                    obj.optBoolean("is_living"),
                                    obj.optString("apartment_number")
                                ));
                            }
                        }
                        residentAdapter.updateList(allResidents);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(this, "Lỗi tải danh sách cư dân", Toast.LENGTH_SHORT).show()
        );
        queue.add(request);
    }

    private void updateSelectedResidentsDisplay() {
        if (selectedResidents.isEmpty()) {
            txtSelectedResidents.setText("Áp dụng cho: Tất cả cư dân");
        } else {
            txtSelectedResidents.setText("Áp dụng cho: " + selectedResidents.size() + " cư dân được chọn");
        }
    }

    private void createFee() {
        String title = edtFinanceTitle.getText().toString().trim();
        String content = edtFinanceContent.getText().toString().trim();
        String amount = edtAmount.getText().toString().trim();
        String dueDateRaw = edtDueDate.getText().toString().trim();
        String type = spinnerFinanceType.getSelectedItem().toString();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(amount) || TextUtils.isEmpty(dueDateRaw)) {
            Toast.makeText(this, "Vui lòng nhập đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("title", title);
            body.put("content", content);
            body.put("amount", Double.parseDouble(amount));
            body.put("type", type);
            
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String dueDate = outputFormat.format(inputFormat.parse(dueDateRaw));
            body.put("due_date", dueDate);

            JSONArray targetUserIds = new JSONArray();
            if (selectedResidents.isEmpty()) { // Gửi cho tất cả
                 for (ResidentItem resident : allResidents) {
                    targetUserIds.put(resident.getUserId());
                }
            } else { // Gửi cho những người được chọn
                for (ResidentItem resident : selectedResidents) {
                    targetUserIds.put(resident.getUserId());
                }
            }
            body.put("target_user_ids", targetUserIds);

            RequestQueue queue = Volley.newRequestQueue(this);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API_CREATE_FINANCE_URL, body,
                    response -> {
                        Toast.makeText(this, "Tạo khoản thu thành công!", Toast.LENGTH_SHORT).show();
                        finish();
                    },
                    this::handleError
            );
            queue.add(request);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Có lỗi xảy ra khi chuẩn bị dữ liệu", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleError(VolleyError error) {
        NetworkResponse response = error.networkResponse;
        String errorMessage = "Lỗi không xác định";
        if (response != null && response.data != null) {
            try {
                String body = new String(response.data, StandardCharsets.UTF_8);
                JSONObject errorJson = new JSONObject(body);
                errorMessage = errorJson.optString("error", "Lỗi từ server");
            } catch (Exception e) {
                errorMessage = "Status code: " + response.statusCode;
            }
        } else {
            errorMessage = error.getMessage() != null ? error.getMessage() : "Lỗi kết nối server";
        }
        Log.e("CreateFinance", "Error: " + errorMessage, error);
        Toast.makeText(this, "Lỗi: " + errorMessage, Toast.LENGTH_LONG).show();
    }
}
