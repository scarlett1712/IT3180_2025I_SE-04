package com.se_04.enoti.notification;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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

    // Base URL của backend — sửa nếu cần (10.0.2.2 cho emulator)
    private static final String BASE_URL = "http://10.0.2.2:5000";

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

    public interface NotificationsCallback {
        void onSuccess(List<NotificationItem> items);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    /**
     * Fetch notifications for userId from backend.
     */
    public void fetchNotifications(long userId, NotificationsCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = BASE_URL + "/api/notification/" + userId;
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                String resp = sb.toString();

                if (code >= 200 && code < 300) {
                    List<NotificationItem> items = parseNotificationArray(resp);
                    mainHandler.post(() -> callback.onSuccess(items));
                } else {
                    String message = "Server error: HTTP " + code;
                    Log.e(TAG, "fetchNotifications error: " + resp);
                    mainHandler.post(() -> callback.onError(message));
                }
            } catch (Exception e) {
                Log.e(TAG, "fetchNotifications exception", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /**
     * Mark notification as read on backend.
     */
    public void markAsRead(long notificationId, SimpleCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = BASE_URL + "/api/notification/" + notificationId + "/read";
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                // no body required by our API; but some servers require {}
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = "{}".getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                String resp = sb.toString();

                if (code >= 200 && code < 300) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    Log.e(TAG, "markAsRead error: " + resp);
                    mainHandler.post(() -> callback.onError("Server returned " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "markAsRead exception", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /**
     * Parse JSON array string -> list of NotificationItem
     * Accepts flexible key names with fallbacks.
     */
    private List<NotificationItem> parseNotificationArray(String json) {
        List<NotificationItem> out = new ArrayList<>();
        try {
            // If server returns an object with data field, handle it
            String trimmed = json.trim();
            JSONArray arr;
            if (trimmed.startsWith("[")) {
                arr = new JSONArray(trimmed);
            } else {
                // try object wrapper
                JSONObject obj = new JSONObject(trimmed);
                if (obj.has("data") && obj.get("data") instanceof JSONArray) {
                    arr = obj.getJSONArray("data");
                } else if (obj.has("notifications") && obj.get("notifications") instanceof JSONArray) {
                    arr = obj.getJSONArray("notifications");
                } else {
                    // not an array -> empty
                    return out;
                }
            }

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
            long id = o.has("notification_id") ? o.optLong("notification_id", -1)
                    : o.has("id") ? o.optLong("id", -1) : -1;

            // title fallback
            String title = o.optString("title", null);
            if (title == null || title.isEmpty()) title = o.optString("heading", o.optString("title", ""));

            String content = o.optString("content", o.optString("body", ""));
            String type = o.optString("type", o.optString("category", "Thông báo"));
            String createdAt = o.optString("created_at", o.optString("createdAt", o.optString("date", "")));
            String expired_date = o.optString("expired_date", o.optString("expiredDate", ""));
            String sender = o.optString("sender", o.optString("created_by", "admin"));

            boolean isRead = false;
            // server might return is_read or status (e.g., "read"/"unread")
            if (o.has("is_read")) {
                isRead = o.optBoolean("is_read", false);
            } else if (o.has("isRead")) {
                isRead = o.optBoolean("isRead", false);
            } else if (o.has("status")) {
                String st = o.optString("status", "");
                isRead = st.equalsIgnoreCase("read") || st.equalsIgnoreCase("1") || st.equalsIgnoreCase("true");
            }

            return new NotificationItem(id, title, createdAt, expired_date, type, sender, content, isRead);
        } catch (Exception e) {
            Log.e(TAG, "parseNotificationFromJson error", e);
            return null;
        }
    }

}
