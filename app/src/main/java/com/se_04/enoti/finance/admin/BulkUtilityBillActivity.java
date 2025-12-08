package com.se_04.enoti.finance.admin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class BulkUtilityBillActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private BulkUtilityAdapter adapter;
    private List<UtilityInputItem> inputList = new ArrayList<>();
    private RadioButton radElec;
    private Button btnCheckAndConfigRates, btnSaveAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bulk_utility_bill); // Bạn tự tạo layout này nhé (chỉ cần RecyclerView + Button)

        recyclerView = findViewById(R.id.recyclerBulkInput);
        radElec = findViewById(R.id.radElectricity);
        btnCheckAndConfigRates = findViewById(R.id.btnCheckAndConfigRates);
        btnSaveAll = findViewById(R.id.btnSaveAll);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BulkUtilityAdapter(inputList);
        recyclerView.setAdapter(adapter);

        // Tải danh sách phòng (Bạn cần có API lấy tất cả phòng, hoặc hardcode test)
        loadAllRooms();

        btnCheckAndConfigRates.setOnClickListener(v -> {
            Intent intent = new Intent(this, ConfigRatesActivity.class);
            startActivity(intent);
        });

        btnSaveAll.setOnClickListener(v -> submitAll());
    }

    private void loadAllRooms() {
        // Gọi API lấy danh sách phòng (Ví dụ: /api/apartments/all)
        // Ở đây tôi giả lập danh sách phòng để demo
        for (int i = 1; i <= 10; i++) {
            inputList.add(new UtilityInputItem("10" + i)); // P101 -> P110
            inputList.add(new UtilityInputItem("20" + i)); // P201 -> P210
        }
        adapter.notifyDataSetChanged();
    }

    private void submitAll() {
        JSONArray jsonArray = new JSONArray();
        int count = 0;

        // Duyệt qua list trong Adapter để lấy dữ liệu User đã nhập
        for (UtilityInputItem item : inputList) {
            String oldStr = item.getOldIndex().trim();
            String newStr = item.getNewIndex().trim();

            // Chỉ lấy những dòng đã nhập đủ
            if (!oldStr.isEmpty() && !newStr.isEmpty()) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("room", item.getRoomNumber());
                    obj.put("old_index", Integer.parseInt(oldStr));
                    obj.put("new_index", Integer.parseInt(newStr));
                    jsonArray.put(obj);
                    count++;
                } catch (JSONException e) { e.printStackTrace(); }
            }
        }

        if (count == 0) {
            Toast.makeText(this, "Bạn chưa nhập dữ liệu nào!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Gửi lên Server
        sendBulkData(jsonArray);
    }

    private void sendBulkData(JSONArray data) {
        String url = ApiConfig.BASE_URL + "/api/finance/create-utility-bulk";
        String type = radElec.isChecked() ? "electricity" : "water";
        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        int year = Calendar.getInstance().get(Calendar.YEAR);

        JSONObject body = new JSONObject();
        try {
            body.put("type", type);
            body.put("month", month);
            body.put("year", year);
            body.put("data", data); // Mảng dữ liệu
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(this, "Đã lưu thành công!", Toast.LENGTH_SHORT).show();
                    setResult(Activity.RESULT_OK);
                    finish();
                },
                error -> Toast.makeText(this, "Lỗi khi lưu", Toast.LENGTH_SHORT).show()
        );

        Volley.newRequestQueue(this).add(request);
    }
}