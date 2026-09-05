package com.perl.planningenfants;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ReminderScheduler {
    public static final String PREFS="planning_prefs";
    public static final String KEY_ACTIVITY="rem_activity",KEY_PICKUP="rem_pickup",KEY_MORNING="rem_morning";
    public static final String KEY_ACTIVITY_MIN="activity_min",KEY_PICKUP_MIN="pickup_min",KEY_MORNING_MIN="morning_min";
    private static final String KEY_CODES="scheduled_codes_v2";
    private ReminderScheduler(){}

    public static void ensureDefaults(Context c){SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);if(!p.contains(KEY_ACTIVITY))p.edit().putBoolean(KEY_ACTIVITY,true).putBoolean(KEY_PICKUP,true).putBoolean(KEY_MORNING,false).putInt(KEY_ACTIVITY_MIN,30).putInt(KEY_PICKUP_MIN,20).putInt(KEY_MORNING_MIN,30).apply();}

    public static void scheduleAll(Context c){ensureDefaults(c);cancelAll(c);SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);Set<String> codes=new HashSet<>();
        boolean activity=p.getBoolean(KEY_ACTIVITY,true),pickup=p.getBoolean(KEY_PICKUP,true),morning=p.getBoolean(KEY_MORNING,false);int actMin=p.getInt(KEY_ACTIVITY_MIN,30),pickMin=p.getInt(KEY_PICKUP_MIN,20),morningMin=p.getInt(KEY_MORNING_MIN,30);
        for(PlannerEvent e:LocalStore.events(c)){
            if("ACTIVITY".equals(e.kind)&&activity)scheduleEvent(c,e,false,actMin,"ACTIVITY",codes);
            if(pickup&&("SCHOOL".equals(e.kind)||("GARDERIE".equals(e.kind)&&"Kelvyn".equals(e.child)&&"16:00".equals(e.start))))scheduleEvent(c,e,true,pickMin,"PICKUP",codes);
            if(morning&&isMorning(e))scheduleEvent(c,e,false,morningMin,"MORNING",codes);
        }
        if(pickup)for(PickupAssignment a:LocalStore.pickups(c))schedulePickup(c,a,pickMin,codes);
        p.edit().putStringSet(KEY_CODES,codes).apply();
    }

    private static boolean isMorning(PlannerEvent e){if("Kelvyn".equals(e.child))return "GARDERIE".equals(e.kind)&&"06:30".equals(e.start);return "SCHOOL".equals(e.kind);}

    private static void scheduleEvent(Context c,PlannerEvent e,boolean useEnd,int before,String type,Set<String>codes){String time=useEnd?e.end:e.start;long trigger=e.weekly?nextWeekly(e.day,time):oneTime(e.dateIso,time);trigger-=before*60_000L;if(trigger<=System.currentTimeMillis()){if(!e.weekly)return;trigger+=7L*24L*60L*60L*1000L;}int code=requestCode(e.id,type);String title,body;if("ACTIVITY".equals(type)){title=e.child+" • "+e.title;body="Début à "+e.start+" — fin à "+e.end;}else if("MORNING".equals(type)){title="Départ à prévoir • "+e.child;body=e.title+" commence à "+e.start;}else{title="Sortie à prévoir • "+e.child;body=e.title+" se termine à "+e.end;}scheduleSingle(c,code,trigger,title,body,type,e.weekly);codes.add(String.valueOf(code));}

    private static void schedulePickup(Context c,PickupAssignment a,int before,Set<String>codes){long trigger=oneTime(a.dateIso,a.time)-before*60_000L;if(trigger<=System.currentTimeMillis())return;int code=requestCode(a.id,"ASSIGNED_PICKUP");String body=a.personName+" est prévu(e) à "+a.time+(a.note==null||a.note.isEmpty()?"":" — "+a.note);scheduleSingle(c,code,trigger,"Récupération • "+a.child,body,"PICKUP",false);codes.add(String.valueOf(code));}

    public static void scheduleSingle(Context c,int code,long at,String title,String body,String type,boolean weekly){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;Intent i=new Intent(c,ReminderReceiver.class);i.putExtra("requestCode",code);i.putExtra("title",title);i.putExtra("body",body);i.putExtra("type",type);i.putExtra("trigger",at);i.putExtra("weekly",weekly);PendingIntent pi=PendingIntent.getBroadcast(c,code,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S&&!am.canScheduleExactAlarms())am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);}

    public static void cancelAll(Context c){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;Set<String>codes=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getStringSet(KEY_CODES,new HashSet<>());for(String s:codes){try{int code=Integer.parseInt(s);Intent i=new Intent(c,ReminderReceiver.class);PendingIntent pi=PendingIntent.getBroadcast(c,code,i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(pi!=null){am.cancel(pi);pi.cancel();}}catch(Exception ignored){}}}
    public static int requestCode(String id,String type){return Math.abs((id+"_"+type).hashCode());}

    private static long nextWeekly(int day,String hhmm){String[]p=hhmm.split(":");Calendar now=Calendar.getInstance(),t=Calendar.getInstance();t.set(Calendar.SECOND,0);t.set(Calendar.MILLISECOND,0);t.set(Calendar.HOUR_OF_DAY,Integer.parseInt(p[0]));t.set(Calendar.MINUTE,Integer.parseInt(p[1]));int delta=(day-now.get(Calendar.DAY_OF_WEEK)+7)%7;t.add(Calendar.DAY_OF_YEAR,delta);if(t.getTimeInMillis()<=now.getTimeInMillis())t.add(Calendar.DAY_OF_YEAR,7);return t.getTimeInMillis();}
    private static long oneTime(String iso,String hhmm){try{String[]d=iso.split("-");String[]t=hhmm.split(":");Calendar c=Calendar.getInstance();c.set(Calendar.YEAR,Integer.parseInt(d[0]));c.set(Calendar.MONTH,Integer.parseInt(d[1])-1);c.set(Calendar.DAY_OF_MONTH,Integer.parseInt(d[2]));c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(t[0]));c.set(Calendar.MINUTE,Integer.parseInt(t[1]));c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}catch(Exception e){return 0;}}
}
