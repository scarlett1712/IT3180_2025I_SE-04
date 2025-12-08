package com.se_04.enoti.finance.admin;

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
import java.util.List;

public class ConfigRatesActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ConfigRateAdapter adapter;
    private List<RateItem> rateList = new ArrayList<>();
    private RadioButton radElec, radWater;
    private Button btnSave;
    private String currentType = "electricity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_rates); // Tự tạo layout có Recycler, RadioGroup, Button

        recyclerView = findViewById(R.id.recyclerRates);
        radElec = findViewById(R.id.radElectricity);
        radWater = findViewById(R.id.radWater);
        btnSave = findViewById(R.id.btnSaveRates);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConfigRateAdapter(rateList);
        recyclerView.setAdapter(adapter);

        // Sự kiện chuyển đổi loại Điện/Nước
        radElec.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) { currentType = "electricity"; loadRates(); }
        });
        radWater.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) { currentType = "water"; loadRates(); }
        });

        btnSave.setOnClickListener(v -> saveRates());

        loadRates(); // Load mặc định
    }

    private void loadRates() {
        String url = ApiConfig.BASE_URL + "/api/finance/utility-rates?type=" + currentType;
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    rateList.clear();
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            // Xử lý max_usage có thể null
                            Integer max = obj.isNull("max_usage") ? 0 : obj.getInt("max_usage");
                            rateList.add(new RateItem(
                                    obj.getString("tier_name"),
                                    obj.getInt("min_usage"),
                                    max,
                                    obj.getDouble("price")
                            ));
                        }
                        adapter.notifyDataSetChanged();
                    } catch (JSONException e) { e.printStackTrace(); }
                },
                error -> Toast.makeText(this, "Lỗi tải bảng giá", Toast.LENGTH_SHORT).show()
        );
        Volley.newRequestQueue(this).add(request);
    }

    private void saveRates() {
        String url = ApiConfig.BASE_URL + "/api/finance/update-rates";
        JSONObject body = new JSONObject();
        try {
            body.put("type", currentType);
            JSONArray tiers = new JSONArray();
            for (RateItem item : adapter.getList()) {
                JSONObject tier = new JSONObject();
                tier.put("tier_name", item.getName());
                tier.put("min", item.getMin());
                tier.put("max", (item.getMax() == 0) ? null : item.getMax());
                tier.put("price", item.getPrice());
                tiers.put(tier);
            }
            body.put("tiers", tiers);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show(),
                error -> Toast.makeText(this, "Lỗi cập nhật", Toast.LENGTH_SHORT).show()
        );
        Volley.newRequestQueue(this).add(request);
    }
}