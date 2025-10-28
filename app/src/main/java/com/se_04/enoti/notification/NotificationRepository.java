package com.se_04.enoti.notification;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationRepository {
    private static final String TAG = "NotificationRepo";
    private static NotificationRepository instance;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private static final String BASE_URL = ApiConfig.BASE_URL;

    private NotificationRepository() {
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static NotificationRepository getInstance() {
        if (instance == null) {
            instance = new NotificationRepository();
        }
        return instance;
    }

    // ===================== CALLBACK INTERFACES =====================
    public interface NotificationsCallback {
        void onSuccess(List<NotificationItem> items);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface TitleCallback {
        void onSuccess(String title);
        void onError(String message);
    }

    // ===================== API FUNCTIONS =====================

    public void fetchNotifications(long userId, NotificationsCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/api/notification/" + userId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                String resp = sb.toString();

                if (code >= 200 && code < 300) {
                    List<NotificationItem> items = parseNotificationArray(resp);
                    mainHandler.post(() -> callback.onSuccess(items));
                } else {
                    Log.e(TAG, "fetchNotifications HTTP " + code + ": " + resp);
                    mainHandler.post(() -> callback.onError("Lỗi máy chủ (" + code + ")"));
                }

            } catch (Exception e) {
                Log.e(TAG, "fetchNotifications exception", e);
                mainHandler.post(() -> callback.onError("Không thể kết nối đến server"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void fetchAdminNotifications(NotificationsCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/api/notification/sent/");
                Log.d(TAG, "fetchAdminNotifications -> " + url);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                Log.d(TAG, "HTTP code = " + code);

                InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                String resp = sb.toString();

                Log.d(TAG, "Response: " + resp);

                if (code >= 200 && code < 300) {
                    List<NotificationItem> items = parseNotificationArray(resp);
                    mainHandler.post(() -> callback.onSuccess(items));
                } else {
                    Log.e(TAG, "fetchAdminNotifications HTTP " + code + ": " + resp);
                    mainHandler.post(() -> callback.onError("Lỗi máy chủ (" + code + ")"));
                }

            } catch (Exception e) {
                Log.e(TAG, "fetchAdminNotifications exception", e);
                mainHandler.post(() -> callback.onError("Không thể kết nối đến server"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void markAsRead(long notificationId, long userId, SimpleCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/api/notification/" + notificationId + "/read");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                JSONObject body = new JSONObject();
                body.put("user_id", userId);
                byte[] input = body.toString().getBytes("utf-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    Log.e(TAG, "markAsRead HTTP " + code);
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

    // ===================== 🆕 HÀM MỚI =====================
    public void fetchNotificationTitle(long notificationId, TitleCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/api/notification/detail/" + notificationId);
                Log.d(TAG, "fetchNotificationTitle -> " + url);

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                String resp = sb.toString().trim();

                if (code >= 200 && code < 300) {
                    if (resp.startsWith("[")) {
                        JSONArray arr = new JSONArray(resp);
                        if (arr.length() > 0) {
                            JSONObject obj = arr.getJSONObject(0);
                            String title = obj.optString("title", "Không xác định");
                            mainHandler.post(() -> callback.onSuccess(title));
                        } else {
                            mainHandler.post(() -> callback.onError("Không có thông báo nào"));
                        }
                    } else if (resp.startsWith("{")) {
                        JSONObject obj = new JSONObject(resp);
                        String title = obj.optString("title", "Không xác định");
                        mainHandler.post(() -> callback.onSuccess(title));
                    } else {
                        mainHandler.post(() -> callback.onError("Dữ liệu trả về không hợp lệ"));
                    }
                } else {
                    Log.e(TAG, "fetchNotificationTitle HTTP " + code + ": " + resp);
                    mainHandler.post(() -> callback.onError("Không thể lấy tiêu đề thông báo"));
                }

            } catch (Exception e) {
                Log.e(TAG, "fetchNotificationTitle exception", e);
                mainHandler.post(() -> callback.onError("Kết nối thất bại"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ===================== JSON PARSERS =====================
    private List<NotificationItem> parseNotificationArray(String json) {
        List<NotificationItem> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json.trim());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                NotificationItem item = parseNotificationFromJson(o);
                if (item != null) out.add(item);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseNotificationArray error", e);
        }
        return out;
    }

    private NotificationItem parseNotificationFromJson(JSONObject o) {
        try {
            long id = o.optLong("notification_id", -1);
            String title = o.optString("title", "Thông báo mới");
            String content = o.optString("content", "");
            String type = o.optString("type", "Thông báo");
            String createdAt = o.optString("created_at", "");
            String expiredDate = o.optString("expired_date", "");
            String sender = o.optString("sender", "Hệ thống");
            boolean isRead = o.optBoolean("is_read", false);

            return new NotificationItem(id, title, createdAt, expiredDate, type, sender, content, isRead);
        } catch (Exception e) {
            Log.e(TAG, "parseNotificationFromJson error", e);
            return null;
        }
    }
}
