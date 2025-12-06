package com.se_04.enoti.maintenance.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.maintenance.MaintenanceItem;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MaintenanceActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MaintenanceAdapter adapter;
    private List<MaintenanceItem> list = new ArrayList<>();
    private TextView txtEmpty;
    private FloatingActionButton fabAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maintenance);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("Danh sách bảo trì");
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        recyclerView = findViewById(R.id.recyclerViewMaintenance);
        txtEmpty = findViewById(R.id.txtEmpty);
        fabAdd = findViewById(R.id.fabAddMaintenance);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Khi click vào item thì hiện dialog cập nhật
        adapter = new MaintenanceAdapter(list, this::showUpdateStatusDialog);
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> {
            // Chuyển sang màn hình tạo mới
            Intent intent = new Intent(this, CreateMaintenanceActivity.class);
            startActivity(intent);
        });

        fetchMaintenanceSchedule();
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle arrow click here
        if (item.getItemId() == android.R.id.home) {
            finish(); // Close this activity and return to previous one
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchMaintenanceSchedule(); // Reload khi quay lại
    }

    private void fetchMaintenanceSchedule() {
        String url = ApiConfig.BASE_URL + "/api/maintenance/schedule";

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    list.clear();
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            list.add(new MaintenanceItem(response.getJSONObject(i)));
                        }
                        adapter.notifyDataSetChanged();
                        txtEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show()
        );

        Volley.newRequestQueue(this).add(request);
    }

    // Hiển thị dialog cập nhật trạng thái
    private void showUpdateStatusDialog(MaintenanceItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_update_status, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        RadioGroup rgStatus = view.findViewById(R.id.radioGroupStatus);
        RadioButton rbCompleted = view.findViewById(R.id.rbCompleted);
        RadioButton rbInProgress = view.findViewById(R.id.rbInProgress);
        TextInputEditText edtNote = view.findViewById(R.id.edtResultNote);
        Button btnSave = view.findViewById(R.id.btnSaveStatus);

        // Set giá trị hiện tại
        if ("Completed".equals(item.getStatus())) rbCompleted.setChecked(true);
        else if ("In Progress".equals(item.getStatus())) rbInProgress.setChecked(true);

        edtNote.setText(item.getResultNote());

        btnSave.setOnClickListener(v -> {
            String newStatus = rbCompleted.isChecked() ? "Completed" : "In Progress";
            String note = edtNote.getText().toString();
            updateStatus(item.getScheduleId(), newStatus, note, dialog);
        });

        dialog.show();
    }

    private void updateStatus(int scheduleId, String status, String note, AlertDialog dialog) {
        String url = ApiConfig.BASE_URL + "/api/maintenance/schedule/update";
        JSONObject body = new JSONObject();
        try {
            body.put("schedule_id", scheduleId);
            body.put("status", status);
            body.put("result_note", note);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    fetchMaintenanceSchedule(); // Reload lại danh sách
                },
                error -> Toast.makeText(this, "Lỗi cập nhật", Toast.LENGTH_SHORT).show()
        );

        Volley.newRequestQueue(this).add(request);
    }
}