package com.se_04.enoti.apartment;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class ApartmentEditorActivity extends BaseActivity {

    private EditText edtNumber, edtFloor, edtArea;
    private Button btnSave;
    private Apartment currentApartment; // Nếu null là thêm mới, có dữ liệu là sửa

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apartment_editor);

        edtNumber = findViewById(R.id.edtRoomNumber);
        edtFloor = findViewById(R.id.edtFloor);
        edtArea = findViewById(R.id.edtArea);
        btnSave = findViewById(R.id.btnSave);

        // Nhận dữ liệu nếu là Sửa
        if (getIntent().hasExtra("apartment")) {
            currentApartment = (Apartment) getIntent().getSerializableExtra("apartment");
            edtNumber.setText(currentApartment.getApartmentNumber());
            edtFloor.setText(String.valueOf(currentApartment.getFloor()));
            edtArea.setText(String.valueOf(currentApartment.getArea()));
            btnSave.setText("Cập nhật");
        }

        btnSave.setOnClickListener(v -> saveApartment());
    }

    private void saveApartment() {
        String num = edtNumber.getText().toString().trim();
        String floor = edtFloor.getText().toString().trim();
        String area = edtArea.getText().toString().trim();

        if (num.isEmpty() || floor.isEmpty() || area.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        String CREATE_APARTMENT = ApiConfig.BASE_URL + "/api/apartments/create";
        String UPDATE_APARTMENT = ApiConfig.BASE_URL + "/api/apartments/update/";

        String url = (currentApartment == null) ? CREATE_APARTMENT : UPDATE_APARTMENT + currentApartment.getId();
        int method = (currentApartment == null) ? Request.Method.POST : Request.Method.PUT;

        JSONObject body = new JSONObject();
        try {
            body.put("apartment_number", num);
            body.put("floor", Integer.parseInt(floor));
            body.put("area", Double.parseDouble(area));
            body.put("building_id", 1); // Mặc định
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(method, url, body,
                response -> {
                    Toast.makeText(this, "Thành công!", Toast.LENGTH_SHORT).show();
                    finish(); // Đóng màn hình để quay lại danh sách
                },
                error -> Toast.makeText(this, "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getApplicationContext()).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }
}