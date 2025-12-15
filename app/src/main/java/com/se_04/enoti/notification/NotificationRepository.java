package com.se_04.enoti.notification;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

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
    private Context context; // 🔥 ADD CONTEXT

    private static final String BASE_URL = ApiConfig.BASE_URL;

    // 🔥 Tăng thời gian chờ lên 30s để server Render kịp khởi động
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 30000;

    private NotificationRepository() {
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized NotificationRepository getInstance(Context context) {
        if (instance == null) {
            instance = new NotificationRepository();
        }
        // Cập nhật context mỗi lần gọi để tránh leak hoặc null
        instance.context = context;
        return instance;
    }

    // 🔥 ADD METHOD TO SET CONTEXT
    public void setContext(Context context) {
        this.context = context.getApplicationContext();
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
                Log.e(TAG, "📡 Fetching from: " + url);

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setUseCaches(false);
                conn.setRequestProperty("Accept", "application/json");

                // 🔥🔥🔥 ADD AUTHORIZATION HEADER 🔥🔥🔥
                if (context != null) {
                    String token = UserManager.getInstance(context).getAuthToken();
                    if (token != null && !token.isEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer " + token);
                        Log.e(TAG, "✅ Sending token: " + token.substring(0, Math.min(10, token.length())) + "...");
                    } else {
                        Log.e(TAG, "⚠️ WARNING: No token available!");
                    }
                } else {
                    Log.e(TAG, "⚠️ WARNING: Context is null, cannot get token!");
                }

                int code = conn.getResponseCode();
                Log.e(TAG, "📊 Response code: " + code);

                InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                String resp = sb.toString();

                Log.e(TAG, "📄 Response body (first 200 chars): " +
                        resp.substring(0, Math.min(200, resp.length())));

                if (code >= 200 && code < 300) {
                    List<NotificationItem> items = parseNotificationArray(resp);
                    Log.e(TAG, "✅ Parsed " + items.size() + " notifications");
                    mainHandler.post(() -> callback.onSuccess(items));
                } else {
                    Log.e(TAG, "❌ HTTP " + code + ": " + resp);

                    // 🔥 Handle 401 Unauthorized
                    if (code == 401 && context != null) {
                        mainHandler.post(() -> {
                            // Force logout on main thread
                            UserManager.getInstance(context).forceLogout();
                        });
                    }

                    mainHandler.post(() -> callback.onError("Lỗi máy chủ (" + code + ")"));
                }

            } catch (java.net.SocketTimeoutException e) {
                Log.e(TAG, "❌ TIMEOUT", e);
                mainHandler.post(() -> callback.onError("Kết nối quá hạn (Server đang khởi động...)"));
            } catch (Exception e) {
                Log.e(TAG, "❌ Exception: " + e.getMessage(), e);
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
                URL url = new URL(BASE_URL + "/api/notification/sent");
                Log.d(TAG, "📡 fetchAdminNotifications -> " + url);

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setUseCaches(false);
                conn.setRequestProperty("Accept", "application/json");

                // 🔥 ADD AUTHORIZATION HEADER
                if (context != null) {
                    String token = UserManager.getInstance(context).getAuthToken();
                    if (token != null && !token.isEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer " + token);
                    }
                }

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

    public void markAsRead(long notificationId, SimpleCallback callback) {
        executor.execute(() -> {
            HttpURLConnection urlConnection = null;
            try {
                // 1. Tự động lấy User ID từ hệ thống
                UserItem user = UserManager.getInstance(context).getCurrentUser();
                if (user == null) {
                    String msg = "User chưa đăng nhập, không thể đánh dấu đã đọc";
                    Log.e(TAG, "❌ " + msg);
                    if (callback != null) mainHandler.post(() -> callback.onError(msg));
                    return;
                }

                // Parse ID sang số (đề phòng ID là chuỗi)
                long userId;
                try {
                    userId = Long.parseLong(user.getId());
                } catch (NumberFormatException e) {
                    if (callback != null) mainHandler.post(() -> callback.onError("ID người dùng không hợp lệ"));
                    return;
                }

                // 2. Cấu hình URL và Connection
                URL url = new URL(BASE_URL + "/api/notification/" + notificationId + "/read");
                Log.d(TAG, "⚡ Marking read: " + url);

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("PUT");
                urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setDoOutput(true);

                // Thêm Token
                String token = UserManager.getInstance(context).getAuthToken();
                if (token != null) {
                    urlConnection.setRequestProperty("Authorization", "Bearer " + token);
                }

                // 3. Gửi Body JSON
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("user_id", userId);

                OutputStream os = urlConnection.getOutputStream();
                os.write(jsonBody.toString().getBytes("UTF-8"));
                os.close();

                // 4. Xử lý phản hồi
                int responseCode = urlConnection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    Log.d(TAG, "✅ Marked notification " + notificationId + " as read success.");
                    if (callback != null) mainHandler.post(callback::onSuccess);
                } else {
                    // Đọc lỗi chi tiết từ Server
                    InputStream errorStream = urlConnection.getErrorStream();
                    String errorMsg = "Lỗi không xác định";
                    if (errorStream != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
                        errorMsg = reader.readLine();
                    }
                    Log.e(TAG, "❌ Failed to mark read. Code: " + responseCode + " - " + errorMsg);

                    String finalMsg = errorMsg;
                    if (callback != null) mainHandler.post(() -> callback.onError("Lỗi " + responseCode + ": " + finalMsg));
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Exception marking as read", e);
                if (callback != null) mainHandler.post(() -> callback.onError("Lỗi kết nối"));
            } finally {
                if (urlConnection != null) urlConnection.disconnect();
            }
        });
    }

    public void fetchNotificationTitle(long notificationId, TitleCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/api/notification/detail/" + notificationId);
                Log.d(TAG, "fetchNotificationTitle -> " + url);

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestProperty("Accept", "application/json");

                // 🔥 ADD AUTHORIZATION HEADER
                if (context != null) {
                    String token = UserManager.getInstance(context).getAuthToken();
                    if (token != null && !token.isEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer " + token);
                    }
                }

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

    public void deleteNotification(long notificationId, SimpleCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/api/notification/delete/" + notificationId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);

                // 🔥 ADD AUTHORIZATION HEADER
                if (context != null) {
                    String token = UserManager.getInstance(context).getAuthToken();
                    if (token != null && !token.isEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer " + token);
                    }
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    Log.e(TAG, "deleteNotification HTTP " + code);
                    mainHandler.post(() -> callback.onError("Lỗi khi xóa thông báo: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteNotification exception", e);
                mainHandler.post(() -> callback.onError("Kết nối thất bại"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void updateNotification(long id, String title, String content, String type, SimpleCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/api/notification/update/" + id);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setDoOutput(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                // 🔥 ADD AUTHORIZATION HEADER
                if (context != null) {
                    String token = UserManager.getInstance(context).getAuthToken();
                    if (token != null && !token.isEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer " + token);
                    }
                }

                JSONObject body = new JSONObject();
                body.put("title", title);
                body.put("content", content);
                body.put("type", type);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes("utf-8"));
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    Log.e(TAG, "updateNotification HTTP " + code);
                    mainHandler.post(() -> callback.onError("Lỗi khi cập nhật thông báo: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "updateNotification exception", e);
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
            Log.e(TAG, "📋 Parsing " + arr.length() + " notifications");

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                NotificationItem item = parseNotificationFromJson(o);
                if (item != null) {
                    out.add(item);
                } else {
                    Log.e(TAG, "⚠️ Failed to parse notification at index " + i);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ parseNotificationArray error", e);
        }
        return out;
    }

    private NotificationItem parseNotificationFromJson(JSONObject o) {
        try {
            long id = o.optLong("notification_id", -1);
            if (id == -1) id = o.optLong("id", -1);

            String title = o.optString("title", "Thông báo mới");
            String content = o.optString("content", "");
            String type = o.optString("type", "Thông báo");

            String createdAt = o.optString("created_at", "");
            if (createdAt.equals("null")) createdAt = "";

            // Xử lý ngày hết hạn
            String expiredDate = o.optString("expired_date");
            if (expiredDate == null || expiredDate.isEmpty() || expiredDate.equalsIgnoreCase("null")) {
                expiredDate = o.optString("scheduled_at");
            }
            if (expiredDate == null || expiredDate.equalsIgnoreCase("null")) {
                expiredDate = "";
            }

            String sender = o.optString("sender", "Hệ thống");
            boolean isRead = o.optBoolean("is_read", false);

            // 🔥 CẬP NHẬT: Lấy file_url và file_type (Fix lỗi null)
            String fileUrl = o.optString("file_url", null);
            String fileType = o.optString("file_type", null);

            // Kiểm tra kỹ nếu server trả về chuỗi "null"
            if ("null".equalsIgnoreCase(fileUrl)) fileUrl = null;
            if ("null".equalsIgnoreCase(fileType)) fileType = null;

            // Sử dụng Constructor MỚI (đã thêm fileUrl, fileType)
            return new NotificationItem(id, title, createdAt, expiredDate, type, sender, content, isRead, fileUrl, fileType);

        } catch (Exception e) {
            Log.e(TAG, "❌ JSON Parse Error", e);
            return null;
        }
    }

    public void markAsRead(long notificationId) {
        markAsRead(notificationId, null);
    }
}