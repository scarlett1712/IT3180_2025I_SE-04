package com.se_04.enoti.finance;

import android.content.Context;
import android.util.Log;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class FinanceRepository {
    private static FinanceRepository instance;
    private static final String API_GET_FINANCES_URL = "http://10.0.2.2:5000/api/finance/user/";

    private FinanceRepository() {}

    public static synchronized FinanceRepository getInstance() {
        if (instance == null) {
            instance = new FinanceRepository();
        }
        return instance;
    }

    public void fetchFinances(Context context, String userId, final FinanceCallback callback) {
        String url = API_GET_FINANCES_URL + userId;
        RequestQueue queue = Volley.newRequestQueue(context);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        List<FinanceItem> finances = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);

                            // Use the single, official constructor for FinanceItem
                            FinanceItem item = new FinanceItem(
                                    obj.optString("title", "Không có tiêu đề"),
                                    obj.optString("content", ""),
                                    obj.optString("due_date", "N/A"),
                                    obj.optString("type", "Khác"),
                                    "Ban Quản lý", // Default sender for user view
                                    (long) obj.optDouble("amount", 0.0)
                            );
                            item.setId(obj.getInt("id"));
                            
                            // You can set the paid status from API if available
                            // item.setPaid("da_thanh_toan".equalsIgnoreCase(obj.optString("status")));

                            finances.add(item);
                        }
                        callback.onSuccess(finances);
                    } catch (Exception e) {
                        Log.e("FinanceRepository", "Error parsing finance JSON", e);
                        callback.onError("Lỗi xử lý dữ liệu.");
                    }
                },
                error -> {
                    Log.e("FinanceRepository", "Volley network error", error);
                    callback.onError("Không thể kết nối đến server.");
                });
        queue.add(request);
    }

    public interface FinanceCallback {
        void onSuccess(List<FinanceItem> finances);
        void onError(String message);
    }
}
