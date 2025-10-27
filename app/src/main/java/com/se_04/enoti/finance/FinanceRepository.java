package com.se_04.enoti.finance;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FinanceRepository {

    private static FinanceRepository instance;
    private RequestQueue requestQueue;

    public interface FinanceCallback {
        void onSuccess(List<FinanceItem> finances);
        void onError(String message);
    }

    private FinanceRepository() {}

    public static synchronized FinanceRepository getInstance() {
        if (instance == null) {
            instance = new FinanceRepository();
        }
        return instance;
    }

    private RequestQueue getRequestQueue(Context context) {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(context.getApplicationContext());
        }
        return requestQueue;
    }

    // 🧾 Dành cho cư dân (user)
    public void fetchUserFinances(Context context, int userId, FinanceCallback callback) {
        String url = ApiConfig.BASE_URL + "/api/finance/user/" + userId;
        Log.d("FinanceRepo", "Fetching user finances: " + url);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> callback.onSuccess(parseFinanceList(response, false)),
                error -> {
                    Log.e("FinanceRepo", "Error fetching user finances", error);
                    callback.onError("Không thể tải dữ liệu khoản thu của cư dân");
                });

        getRequestQueue(context).add(request);
    }

    // 🧮 Dành cho admin
    public void fetchAdminFinances(Context context, int adminId, FinanceCallback callback) {
        String url = ApiConfig.BASE_URL + "/api/finance/admin/" + adminId;
        Log.d("FinanceRepo", "Fetching admin finances: " + url);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> callback.onSuccess(parseFinanceList(response, true)),
                error -> {
                    Log.e("FinanceRepo", "Error fetching admin finances", error);
                    callback.onError("Không thể tải dữ liệu khoản thu (admin)");
                });

        getRequestQueue(context).add(request);
    }

    // ✅ Hàm dùng chung — tự động nhận biết role (user/admin)
    public void fetchFinances(Context context, int id, boolean isAdmin, FinanceCallback callback) {
        if (isAdmin) {
            fetchAdminFinances(context, id, callback);
        } else {
            fetchUserFinances(context, id, callback);
        }
    }

    // 🧩 Hàm parse JSON chung cho cả admin & user
    private List<FinanceItem> parseFinanceList(JSONArray response, boolean isAdmin) {
        List<FinanceItem> list = new ArrayList<>();

        try {
            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.getJSONObject(i);

                FinanceItem item = new FinanceItem();
                item.setId(obj.optInt("id", -1));
                item.setTitle(obj.optString("title", "Không rõ"));
                item.setContent(obj.optString("content", ""));
                item.setType(obj.optString("type", "Khác"));
                item.setDate(obj.optString("date", ""));
                item.setSender(obj.optString("sender", "Ban quản lý"));

                // 💰 Giá trị khoản thu
                if (obj.has("price") && !obj.isNull("price")) {
                    try {
                        item.setPrice(obj.getLong("price"));
                    } catch (Exception e) {
                        item.setPrice(0L);
                    }
                }

                // 👇 Dữ liệu thống kê (chỉ admin)
                if (isAdmin) {
                    item.setPaidUsers(obj.optInt("paid_users", 0));
                    item.setTotalUsers(obj.optInt("total_users", 0));
                    item.calculateUnpaidUsers(); // ✅ Tự tính số chưa nộp
                }

                list.add(item);
            }
        } catch (JSONException e) {
            Log.e("FinanceRepo", "JSON parse error", e);
        }

        return list;
    }
}
