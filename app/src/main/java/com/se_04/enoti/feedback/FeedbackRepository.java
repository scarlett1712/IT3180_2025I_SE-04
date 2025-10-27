package com.se_04.enoti.feedback;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FeedbackRepository {
    private static final String TAG = "FeedbackRepo";
    private static FeedbackRepository instance;

    private final ExecutorService executor;
    private final Handler mainHandler;
    private static final String BASE_URL = ApiConfig.BASE_URL + "/api/feedback";

    private FeedbackRepository() {
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static FeedbackRepository getInstance() {
        if (instance == null) instance = new FeedbackRepository();
        return instance;
    }

    // ===== Callback interfaces =====
    public interface FeedbackListCallback {
        void onSuccess(List<FeedbackItem> items);
        void onError(String message);
    }

    public interface FeedbackDetailCallback {
        void onSuccess(FeedbackItem item);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    // ===== API: Lấy danh sách feedback của user =====
    public void fetchFeedbacks(String userId, FeedbackListCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/user/" + userId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                String response = readStream(is);
                Log.d(TAG, "📥 Response: " + response);

                if (code >= 200 && code < 300) {
                    List<FeedbackItem> items = parseFeedbackArray(response);
                    mainHandler.post(() -> callback.onSuccess(items));
                } else {
                    mainHandler.post(() -> callback.onError("Lỗi máy chủ (" + code + ")"));
                }

            } catch (Exception e) {
                Log.e(TAG, "fetchFeedbacks exception", e);
                mainHandler.post(() -> callback.onError("Không thể kết nối đến server"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ===== API: Lấy chi tiết feedback =====
    public void fetchFeedbackDetail(long feedbackId, FeedbackDetailCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/" + feedbackId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                String response = readStream(
                        (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream()
                );

                if (code >= 200 && code < 300) {
                    FeedbackItem item = parseFeedbackFromJson(new JSONObject(response));
                    mainHandler.post(() -> callback.onSuccess(item));
                } else {
                    mainHandler.post(() -> callback.onError("Không thể lấy chi tiết phản hồi"));
                }

            } catch (Exception e) {
                Log.e(TAG, "fetchFeedbackDetail exception", e);
                mainHandler.post(() -> callback.onError("Kết nối thất bại"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ===== API: Gửi feedback mới =====
    public void sendFeedback(long userId, long notificationId, String content, String fileUrl, SimpleCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");

                JSONObject body = new JSONObject();
                body.put("user_id", userId);
                body.put("notification_id", notificationId);
                body.put("content", content);
                if (fileUrl != null && !fileUrl.isEmpty()) {
                    body.put("file_url", fileUrl);
                }

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    mainHandler.post(() -> callback.onError("Không thể gửi phản hồi"));
                }

            } catch (Exception e) {
                Log.e(TAG, "sendFeedback exception", e);
                mainHandler.post(() -> callback.onError("Kết nối thất bại"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ===== API: Đánh dấu feedback đã đọc =====
    public void markAsRead(long feedbackId, SimpleCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/" + feedbackId + "/read");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    mainHandler.post(() -> callback.onError("Không thể đánh dấu là đã đọc"));
                }

            } catch (Exception e) {
                Log.e(TAG, "markAsRead exception", e);
                mainHandler.post(() -> callback.onError("Kết nối thất bại"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ===== Helper =====
    private String readStream(InputStream is) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private List<FeedbackItem> parseFeedbackArray(String json) {
        List<FeedbackItem> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                FeedbackItem item = parseFeedbackFromJson(arr.getJSONObject(i));
                if (item != null) list.add(item);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseFeedbackArray error", e);
        }
        return list;
    }

    private FeedbackItem parseFeedbackFromJson(JSONObject o) {
        try {
            return new FeedbackItem(
                    o.optLong("feedback_id", -1),
                    o.optLong("notification_id", -1),
                    o.optLong("user_id", -1),
                    o.optString("content", ""),
                    o.optString("file_url", ""),
                    o.optString("status", "pending"),
                    o.optString("created_at", "")
            );
        } catch (Exception e) {
            Log.e(TAG, "parseFeedbackFromJson error", e);
            return null;
        }
    }

    private final List<FeedbackItem> cachedFeedbacks = new ArrayList<>();

    public void setFeedbacks(List<FeedbackItem> list) {
        cachedFeedbacks.clear();
        if (list != null) cachedFeedbacks.addAll(list);
    }

    public List<FeedbackItem> getFeedbacks() {
        return new ArrayList<>(cachedFeedbacks); // Trả bản sao an toàn
    }
}
