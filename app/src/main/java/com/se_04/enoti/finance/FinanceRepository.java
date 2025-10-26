package com.se_04.enoti.finance;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FinanceRepository {
    private static FinanceRepository instance;
    private final List<FinanceItem> finances = new ArrayList<>();
    private static final String API_GET_FINANCES_URL = ApiConfig.BASE_URL + "/api/finance/user/";

    private FinanceRepository() {
    }

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
                        finances.clear();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            finances.add(new FinanceItem(
                                    obj.getString("title"),
                                    obj.getString("due_date"),
                                    obj.getString("type"),
                                    obj.getString("content"),
                                    obj.getLong("amount")));
                        }
                        callback.onSuccess(finances);
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                },
                error -> callback.onError(error.getMessage()));
        queue.add(request);
    }

    public interface FinanceCallback {
        void onSuccess(List<FinanceItem> finances);
        void onError(String message);
    }

     public List<FinanceItem> getReceipts(){ return finances; }
}
