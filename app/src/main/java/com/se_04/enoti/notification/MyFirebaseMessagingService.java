package com.se_04.enoti.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.account_related.SplashActivity;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONObject;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";
    private static final String CHANNEL_ID = "ENOTI_HIGH_PRIORITY"; // 🔥 Đổi ID mới để reset setting cũ
    private static final String CHANNEL_NAME = "Thông báo quan trọng ENoti";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Ưu tiên xử lý Notification Payload (từ Console)
        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            sendNotification(title, body);
        }
        // Xử lý Data Payload (từ Backend API)
        else if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            String title = data.get("title");
            String body = data.get("body");

            if (title != null && body != null) {
                sendNotification(title, body);
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        getSharedPreferences("FCM_PREF", MODE_PRIVATE).edit().putString("fcm_token", token).apply();
        UserItem currentUser = UserManager.getInstance(getApplicationContext()).getCurrentUser();
        if (currentUser != null) {
            sendRegistrationToServer(currentUser.getId(), token);
        }
    }

    private void sendRegistrationToServer(String userId, String token) {
        String url = ApiConfig.BASE_URL + "/api/users/update_fcm_token";
        JSONObject body = new JSONObject();
        try {
            body.put("user_id", userId);
            body.put("fcm_token", token);
        } catch (Exception e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> Log.d(TAG, "Token updated on server"),
                error -> Log.e(TAG, "Failed to update token: " + error.toString())
        );
        Volley.newRequestQueue(getApplicationContext()).add(request);
    }

    private void sendNotification(String title, String messageBody) {
        Intent intent = new Intent(this, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // 🔥 CẤU HÌNH HEADS-UP NOTIFICATION (POPUP)
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.enoti)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.enoti))
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setDefaults(NotificationCompat.DEFAULT_ALL) // Rung + Âm thanh + Đèn
                        .setPriority(NotificationCompat.PRIORITY_MAX) // 🔥 Mức cao nhất để hiện popup
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Hiện trên màn hình khóa
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // 🔥 TẠO CHANNEL VỚI IMPORTANCE_HIGH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH); // 🔥 Quan trọng: HIGH mới hiện popup

            channel.setDescription("Thông báo khẩn cấp từ ENoti");
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500});
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
    }
}