package com.se_04.enoti.finance;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FinanceRepository {
    private static FinanceRepository instance;

    // API cho user và admin
    private static final String API_GET_FINANCES_URL = ApiConfig.BASE_URL + "/api/finance/user/";
    private static final String API_GET_ADMIN_FINANCES_URL = ApiConfig.BASE_URL + "/api/finance/admin/"; // ✅ admin route

    private FinanceRepository() {}

    public static synchronized FinanceRepository getInstance() {
        if (instance == null) {
            instance = new FinanceRepository();
        }
        return instance;
    }

    // 🟢 User: Lấy danh sách tài chính theo userId
    public void fetchFinances(Context context, String userId, final FinanceCallback callback) {
        String url = API_GET_FINANCES_URL + userId;
        RequestQueue queue = Volley.newRequestQueue(context);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        List<FinanceItem> finances = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);

                            FinanceItem item = new FinanceItem(
                                    obj.optString("title", "Không có tiêu đề"),
                                    obj.optString("content", ""),
                                    obj.optString("due_date", "N/A"),
                                    obj.optString("type", "Khác"),
                                    "Ban Quản lý", // Người gửi mặc định cho user
                                    (long) obj.optDouble("amount", 0.0)
                            );
                            item.setId(obj.getInt("id"));
                            finances.add(item);
                        }
                        callback.onSuccess(finances);
                    } catch (Exception e) {
                        Log.e("FinanceRepository", "Error parsing user finance JSON", e);
                        callback.onError("Lỗi xử lý dữ liệu.");
                    }
                },
                error -> {
                    Log.e("FinanceRepository", "Volley network error (user)", error);
                    callback.onError("Không thể kết nối đến server.");
                });
        queue.add(request);
    }

    // 🔵 Admin: Lấy các khoản thu do admin đang đăng nhập tạo
    public void fetchAdminFinances(Context context, int adminId, final FinanceCallback callback) {
        String url = API_GET_ADMIN_FINANCES_URL + adminId;
        RequestQueue queue = Volley.newRequestQueue(context);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        List<FinanceItem> finances = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);

                            FinanceItem item = new FinanceItem(
                                    obj.optString("title", "Không có tiêu đề"),
                                    obj.optString("content", ""),
                                    obj.optString("due_date", "N/A"),
                                    obj.optString("type", "Khác"),
                                    "Bạn", // ✅ admin là người tạo
                                    (long) obj.optDouble("amount", 0.0)
                            );
                            item.setId(obj.getInt("id"));
                            finances.add(item);
                        }
                        callback.onSuccess(finances);
                    } catch (Exception e) {
                        Log.e("FinanceRepository", "Error parsing admin finance JSON", e);
                        callback.onError("Lỗi xử lý dữ liệu.");
                    }
                },
                error -> {
                    Log.e("FinanceRepository", "Volley network error (admin)", error);
                    callback.onError("Không thể kết nối đến server.");
                });

        queue.add(request);
    }


    // Callback dùng chung
    public interface FinanceCallback {
        void onSuccess(List<FinanceItem> finances);
        void onError(String message);
    }
}
