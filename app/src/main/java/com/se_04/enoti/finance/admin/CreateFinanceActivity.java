package com.se_04.enoti.finance.admin;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.residents.ResidentAdapter;
import com.se_04.enoti.residents.ResidentItem;

import org.json.JSONArray;
import org.json.JSONObject;

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

    private static final String API_GET_RESIDENTS_URL = "http://10.0.2.2:5000/api/residents";
    private static final String API_CREATE_FINANCE_URL = "http://10.0.2.2:5000/api/finance/create";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_finance);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Tạo khoản thu mới");
        }

        // --- View Binding ---
        edtFinanceTitle = findViewById(R.id.edtFinanceTitle);
        edtFinanceContent = findViewById(R.id.edtFinanceContent);
        edtAmount = findViewById(R.id.edtAmount);
        edtDueDate = findViewById(R.id.edtDueDate);
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
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, month + 1, year);
                edtDueDate.setText(selectedDate);
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void setupResidentList() {
        recyclerResidents.setLayoutManager(new LinearLayoutManager(this));
        residentAdapter = new ResidentAdapter(new ArrayList<>(), ResidentAdapter.MODE_SELECT_FOR_NOTIFICATION, selected -> {
            this.selectedResidents = selected;
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
                            if (obj.optInt("role_id", 0) == 1 || obj.optInt("role_id", 0) == 2) { 
                                // Use the new, simpler constructor for ResidentItem
                                allResidents.add(new ResidentItem(
                                    obj.getInt("user_id"),
                                    obj.getString("full_name"),
                                    obj.getString("apartment_number")
                                ));
                            }
                        }
                        residentAdapter.updateList(allResidents);
                    } catch (Exception e) {
                        Log.e("CreateFinanceActivity", "Error parsing residents JSON", e);
                        Toast.makeText(this, "Lỗi xử lý danh sách cư dân", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    Log.e("CreateFinanceActivity", "Error fetching residents", error);
                    Toast.makeText(this, "Lỗi tải danh sách cư dân", Toast.LENGTH_SHORT).show();
                }
        );
        queue.add(request);
    }

    private void updateSelectedResidentsDisplay() {
        if (selectedResidents.isEmpty()) {
            txtSelectedResidents.setText("Chưa chọn ai (mặc định: Gửi đến tất cả)");
        } else {
            txtSelectedResidents.setText(selectedResidents.size() + " cư dân được chọn");
        }
    }

    private void createFee() {
        String title = edtFinanceTitle.getText().toString().trim();
        String content = edtFinanceContent.getText().toString().trim();
        String amountStr = edtAmount.getText().toString().trim();
        String dueDateRaw = edtDueDate.getText().toString().trim();
        String type = spinnerFinanceType.getSelectedItem().toString();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(amountStr)) {
            Toast.makeText(this, "Vui lòng nhập Tiêu đề và Số tiền", Toast.LENGTH_SHORT).show();
            return;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("title", title);
            body.put("content", content);
            body.put("amount", amount);
            body.put("type", type);
            body.put("due_date", dueDateRaw);

            JSONArray targetUserIds = new JSONArray();
            if (selectedResidents.isEmpty()) {
                 for (ResidentItem resident : allResidents) {
                    targetUserIds.put(resident.getUserId());
                }
            } else { 
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
                    error -> {
                        Log.e("CreateFinanceActivity", "Error creating fee", error);
                        Toast.makeText(this, "Lỗi khi tạo khoản thu", Toast.LENGTH_SHORT).show();
                    }
            );
            queue.add(request);

        } catch (Exception e) {
            Log.e("CreateFinanceActivity", "Error building JSON for fee creation", e);
            Toast.makeText(this, "Có lỗi xảy ra khi chuẩn bị dữ liệu", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
