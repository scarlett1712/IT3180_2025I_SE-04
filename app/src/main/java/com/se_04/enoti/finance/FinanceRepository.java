package com.se_04.enoti.finance;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.utils.ApiConfig;

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
                response -> callback.onSuccess(parseFinanceList(response)),
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
                response -> callback.onSuccess(parseFinanceList(response)),
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

    /**
     * 🔥 HÀM QUAN TRỌNG: Parse JSON response sang danh sách FinanceItem.
     * Hàm này giờ sẽ đọc các trường liên quan đến phòng và trạng thái.
     */
    private List<FinanceItem> parseFinanceList(JSONArray response) {
        List<FinanceItem> list = new ArrayList<>();

        try {
            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.getJSONObject(i);

                FinanceItem item = new FinanceItem();
                // Dữ liệu cơ bản
                item.setId(obj.optInt("id", -1));
                item.setTitle(obj.optString("title", "Không rõ"));
                item.setContent(obj.optString("content", ""));
                item.setType(obj.optString("type", "Khác"));
                item.setDate(obj.optString("due_date", ""));
                item.setSender(obj.optString("sender", "Ban quản lý"));

                // 💰 Giá trị khoản thu
                if (obj.has("price") && !obj.isNull("price")) {
                    item.setPrice(obj.optLong("price", 0L));
                }

                // 👇 THAY ĐỔI: Đọc dữ liệu trạng thái và phòng
                item.setStatus(obj.optString("status", "chua_thanh_toan"));
                item.setRoom(obj.optString("room", null));

                // 👇 THAY ĐỔI: Đọc dữ liệu thống kê theo phòng (nếu có)
                item.setPaidRooms(obj.optInt("paid_rooms", 0));
                item.setTotalRooms(obj.optInt("total_rooms", 0));

                list.add(item);
            }
        } catch (JSONException e) {
            Log.e("FinanceRepo", "JSON parse error", e);
        }

        return list;
    }
}
