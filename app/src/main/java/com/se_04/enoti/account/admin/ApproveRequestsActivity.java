package com.se_04.enoti.account.admin;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import androidx.appcompat.widget.Toolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ApproveRequestsActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private ApproveRequestsAdapter adapter;
    private List<ProfileRequestItem> requestList = new ArrayList<>();
    private TextView txtEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_approve_requests);

        Toolbar toolbar;

        recyclerView = findViewById(R.id.recyclerViewRequests);
        txtEmpty = findViewById(R.id.txtEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ApproveRequestsAdapter(requestList, this::showDetailDialog);
        recyclerView.setAdapter(adapter);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Yêu cầu chỉnh sửa"); // FIX: Correct title
        }

        fetchPendingRequests();
    }

    // FIX: Add this method to handle toolbar item clicks
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle arrow click here
        if (item.getItemId() == android.R.id.home) {
            finish(); // Close this activity and return to previous one
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void fetchPendingRequests() {
        String url = ApiConfig.BASE_URL + "/api/profile-requests/pending";
        requestList.clear();

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            requestList.add(new ProfileRequestItem(response.getJSONObject(i)));
                        }
                        adapter.notifyDataSetChanged();
                        txtEmpty.setVisibility(requestList.isEmpty() ? View.VISIBLE : View.GONE);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(this, "Lỗi tải danh sách: " + error.getMessage(), Toast.LENGTH_SHORT).show()
        );

        Volley.newRequestQueue(this).add(request);
    }

    private void showDetailDialog(ProfileRequestItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_request_details, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        LinearLayout container = view.findViewById(R.id.containerChanges);
        Button btnApprove = view.findViewById(R.id.btnApprove);
        Button btnReject = view.findViewById(R.id.btnReject);

        // 🔥 Hiển thị các trường thông tin để so sánh
        addComparisonRow(container, "Họ tên", item.getCurrentName(), item.getNewFullName());
        addComparisonRow(container, "SĐT", item.getCurrentPhone(), item.getNewPhone());
        addComparisonRow(container, "Email", item.getCurrentEmail(), item.getNewEmail());
        addComparisonRow(container, "Giới tính", item.getCurrentGender(), item.getNewGender());

        // Xử lý ngày tháng (Cắt bớt giờ phút nếu có)
        String oldDob = item.getCurrentDob() != null && item.getCurrentDob().length() >= 10 ? item.getCurrentDob().substring(0, 10) : "";
        String newDob = item.getNewDob() != null && item.getNewDob().length() >= 10 ? item.getNewDob().substring(0, 10) : "";
        addComparisonRow(container, "Ngày sinh", oldDob, newDob);
        addComparisonRow(container, "Nghề nghiệp", item.getCurrentJob(), item.getNewJob());

        addComparisonRow(container, "CCCD", item.getCurrentIdentityCard(), item.getNewIdentityCard());
        addComparisonRow(container, "Quê quán", item.getCurrentHomeTown(), item.getNewHomeTown());

        btnApprove.setOnClickListener(v -> {
            processRequest(item.getRequestId(), "approve");
            dialog.dismiss();
        });

        btnReject.setOnClickListener(v -> {
            processRequest(item.getRequestId(), "reject");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void addComparisonRow(LinearLayout container, String label, String oldVal, String newVal) {
        if (oldVal == null) oldVal = "";
        if (newVal == null) newVal = "";

        // Chỉ hiển thị nếu có sự thay đổi (giá trị mới khác giá trị cũ và không rỗng)
        if (newVal.isEmpty() || oldVal.equalsIgnoreCase(newVal)) return;

        TextView tv = new TextView(this);
        tv.setTextSize(16f);
        tv.setPadding(0, 12, 0, 12);

        // In đậm và tô màu cam để làm nổi bật sự thay đổi
        tv.setText(label + ":\n" + oldVal + "  ➜  " + newVal);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor("#E65100")); // Cam đậm

        // Thêm đường kẻ mờ bên dưới
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#EEEEEE"));

        container.addView(tv);
        container.addView(divider);
    }

    private void processRequest(int requestId, String action) {
        String url = ApiConfig.BASE_URL + "/api/profile-requests/resolve";
        JSONObject body = new JSONObject();
        try {
            body.put("request_id", requestId);
            body.put("action", action);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    String msg = action.equals("approve") ? "Đã duyệt yêu cầu!" : "Đã từ chối yêu cầu.";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    fetchPendingRequests(); // Reload list
                },
                error -> Toast.makeText(this, "Lỗi xử lý: " + error.getMessage(), Toast.LENGTH_SHORT).show()
        );
        Volley.newRequestQueue(this).add(request);
    }
}