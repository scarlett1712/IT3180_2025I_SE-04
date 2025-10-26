package com.se_04.enoti.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.se_04.enoti.R;
import com.se_04.enoti.home.user.MainActivity_User;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";
    // Define a public action string for the broadcast
    public static final String ACTION_NEW_NOTIFICATION = "com.se_04.enoti.NEW_NOTIFICATION";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            Log.d(TAG, "Message data payload: " + data);

            String title = data.get("title");
            String body = data.get("body");
            String notificationId = data.get("notification_id");

            if (title == null || body == null) return;

            // Show the notification on the status bar
            showNotification(title, body, notificationId);

            // Send a local broadcast to notify active fragments
            sendNewNotificationBroadcast();

        } else if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            showNotification(
                remoteMessage.getNotification().getTitle(),
                remoteMessage.getNotification().getBody(),
                null
            );
            sendNewNotificationBroadcast();
        }
    }

    private void showNotification(String title, String message, String notificationId) {
        // ... (existing code for showing notification is fine)
        Intent intent;
        if (notificationId != null && !notificationId.isEmpty()) {
            intent = new Intent(this, NotificationDetailActivity.class);
            try {
                long id = Long.parseLong(notificationId);
                intent.putExtra("notification_id", id);
            } catch (NumberFormatException e) {
                intent = new Intent(this, MainActivity_User.class);
            }
        } else {
            intent = new Intent(this, MainActivity_User.class);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        String channelId = "enoti_channel";
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_notification_light)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "eNoti Notifications", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        int uniqueId = (notificationId != null) ? notificationId.hashCode() : (int) System.currentTimeMillis();
        notificationManager.notify(uniqueId, notificationBuilder.build());
    }

    private void sendNewNotificationBroadcast() {
        Intent intent = new Intent(ACTION_NEW_NOTIFICATION);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "New notification broadcast sent.");
    }
}
