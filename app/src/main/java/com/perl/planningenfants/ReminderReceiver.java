package com.perl.planningenfants;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){String title=i.getStringExtra("title"),body=i.getStringExtra("body"),type=i.getStringExtra("type");int code=i.getIntExtra("requestCode",1);long old=i.getLongExtra("trigger",System.currentTimeMillis());boolean weekly=i.getBooleanExtra("weekly",false);show(c,code,title,body,type);if(weekly)ReminderScheduler.scheduleSingle(c,code,old+7L*24L*60L*60L*1000L,title,body,type,true);}
    private void show(Context c,int id,String title,String body,String type){NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;String channel="ACTIVITY".equals(type)?"activities":"planning";if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){NotificationChannel nc=new NotificationChannel(channel,"ACTIVITY".equals(type)?"Activités":"Planning et sorties",NotificationManager.IMPORTANCE_HIGH);nc.setDescription("Rappels du planning des enfants");nm.createNotificationChannel(nc);}Intent open=new Intent(c,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);android.app.Notification.Builder b=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?new android.app.Notification.Builder(c,channel):new android.app.Notification.Builder(c);b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title==null?"Planning enfants":title).setContentText(body==null?"Rappel":body).setStyle(new android.app.Notification.BigTextStyle().bigText(body)).setAutoCancel(true).setContentIntent(pi).setPriority(android.app.Notification.PRIORITY_HIGH);nm.notify(id,b.build());}
}
