package com.perl.planningenfants;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FamilyMessagingService extends FirebaseMessagingService {

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        SupabaseSync.updateFcmToken(getApplicationContext(), token);
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        RemoteMessage.Notification n = message.getNotification();
        String title = n != null && n.getTitle() != null ? n.getTitle() : "Planning familial";
        String body = n != null && n.getBody() != null ? n.getBody() : "Mise à jour";
        showNotification(title, body);
    }

    private void showNotification(String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        String ch = "family_push";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(ch, "Notifications famille", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 8113, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, ch) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(pi);
        nm.notify(8113, b.build());
    }
}
