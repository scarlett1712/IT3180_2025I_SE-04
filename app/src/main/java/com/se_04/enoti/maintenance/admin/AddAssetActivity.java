package com.se_04.enoti.maintenance.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONException;
import org.json.JSONObject;

public class AddAssetActivity extends AppCompatActivity {

    private TextInputEditText edtName, edtLocation, edtDate;
    private Button btnAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_asset);

        edtName = findViewById(R.id.edtAssetName);
        edtLocation = findViewById(R.id.edtAssetLocation);
        edtDate = findViewById(R.id.edtPurchaseDate);
        btnAdd = findViewById(R.id.btnAddAsset);

        btnAdd.setOnClickListener(v -> submitAsset());
    }

    private void submitAsset() {
        String name = edtName.getText().toString().trim();
        String location = edtLocation.getText().toString().trim();
        String date = edtDate.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(location)) {
            Toast.makeText(this, "Vui lòng nhập tên và vị trí", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/maintenance/assets";
        JSONObject body = new JSONObject();
        try {
            body.put("asset_name", name);
            body.put("location", location);
            body.put("purchase_date", date);
            body.put("status", "Good"); // Mặc định là Tốt
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(this, "Thêm thiết bị thành công!", Toast.LENGTH_SHORT).show();
                    finish(); // Đóng màn hình
                },
                error -> Toast.makeText(this, "Lỗi khi thêm thiết bị", Toast.LENGTH_SHORT).show()
        );

        Volley.newRequestQueue(this).add(request);
    }
}