package com.se_04.enoti.maintenance.user;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager; // 🔥 Import UserManager

import org.json.JSONException;
import org.json.JSONObject;

public class ReportIssueActivity extends AppCompatActivity {

    private TextView txtAssetName;
    private TextInputEditText edtDesc;
    private Button btnSubmit;
    private int assetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_issue);

        txtAssetName = findViewById(R.id.txtAssetName);
        edtDesc = findViewById(R.id.edtIssueDescription);
        btnSubmit = findViewById(R.id.btnSubmitReport);

        // Nhận dữ liệu từ Fragment
        assetId = getIntent().getIntExtra("ASSET_ID", -1);
        String assetName = getIntent().getStringExtra("ASSET_NAME");

        txtAssetName.setText("Báo cáo sự cố: " + assetName);

        btnSubmit.setOnClickListener(v -> submitReport());
    }

    private void submitReport() {
        String desc = edtDesc.getText().toString().trim();
        if (TextUtils.isEmpty(desc)) {
            Toast.makeText(this, "Vui lòng nhập mô tả sự cố", Toast.LENGTH_SHORT).show();
            return;
        }

        // Lấy User ID hiện tại (Bắt buộc phải có để lưu vào DB)
        UserItem currentUser = UserManager.getInstance(this).getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Lỗi xác thực người dùng", Toast.LENGTH_SHORT).show();
            return;
        }

        // Khóa nút
        btnSubmit.setEnabled(false);
        btnSubmit.setText("Đang gửi...");

        // 🔥 URL MỚI: Gọi vào API báo cáo riêng
        String url = ApiConfig.BASE_URL + "/api/reports/create";

        JSONObject body = new JSONObject();
        try {
            body.put("user_id", Integer.parseInt(currentUser.getId())); // 🔥 Lấy ID thật
            body.put("asset_id", assetId);
            body.put("description", desc);
            // Không cần gửi status hay date, Backend tự xử lý
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(this, "✅ Gửi báo cáo thành công!", Toast.LENGTH_LONG).show();
                    finish(); // Đóng màn hình
                },
                error -> {
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Gửi báo cáo");
                    Toast.makeText(this, "❌ Lỗi khi gửi báo cáo", Toast.LENGTH_SHORT).show();
                }
        );

        request.setRetryPolicy(new DefaultRetryPolicy(
                20000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Volley.newRequestQueue(this).add(request);
    }
}