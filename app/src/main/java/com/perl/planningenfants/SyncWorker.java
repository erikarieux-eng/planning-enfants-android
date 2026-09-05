package com.perl.planningenfants;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {
    private static final String PREFS = "background_sync";
    private static final String K_LAST = "last_change";
    public SyncWorker(@NonNull Context appContext, @NonNull WorkerParameters params) { super(appContext, params); }

    public static void schedule(Context c) {
        Constraints con = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES).setConstraints(con).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("planning_family_sync", ExistingPeriodicWorkPolicy.KEEP, req);
    }

    @NonNull @Override public Result doWork() {
        Context c = getApplicationContext();
        try {
            if (!SupabaseSync.hasFamily(c)) return Result.success();
            long max = SupabaseSync.pullAll(c);
            if (max < 0) return Result.success();
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long old = p.getLong(K_LAST, 0);
            p.edit().putLong(K_LAST, max).apply();
            if (old > 0 && max > old) notifyChange(c);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void notifyChange(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String ch = "sync_changes";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) nm.createNotificationChannel(new NotificationChannel(ch, "Synchronisation planning", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 8112, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new android.app.Notification.Builder(c, ch) : new android.app.Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Planning familial mis à jour").setContentText("Un événement ou une récupération a changé.").setAutoCancel(true).setContentIntent(pi);
        nm.notify(8112, b.build());
    }
}
