package com.perl.planningenfants;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class LocalStore {
    private static final String PREFS = "planning_data_v2";
    private static final String KEY_EVENTS = "events";
    private static final String KEY_PEOPLE = "people";
    private static final String KEY_PICKUPS = "pickups";
    private static final String KEY_SEEDED = "seeded";

    private LocalStore() {}

    private static SharedPreferences p(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static synchronized void seedIfNeeded(Context c) {
        if (p(c).getBoolean(KEY_SEEDED, false)) return;
        List<PlannerEvent> out = new ArrayList<>();
        for (ScheduleData.Event s : ScheduleData.events()) {
            PlannerEvent e = new PlannerEvent();
            e.id = s.id; e.child = s.child; e.title = s.label; e.start = s.start; e.end = s.end;
            e.kind = s.kind.name(); e.weekly = true; e.day = s.day; e.dateIso = ""; e.updatedAt = System.currentTimeMillis();
            out.add(e);
        }
        replaceEvents(c, out);
        p(c).edit().putBoolean(KEY_SEEDED, true).apply();
    }

    public static synchronized List<PlannerEvent> events(Context c) {
        seedIfNeeded(c);
        List<PlannerEvent> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(c).getString(KEY_EVENTS, "[]"));
            for (int i=0;i<a.length();i++) {
                JSONObject o = a.getJSONObject(i);
                PlannerEvent e = new PlannerEvent();
                e.id=o.optString("id"); e.child=o.optString("child"); e.title=o.optString("title");
                e.start=o.optString("start"); e.end=o.optString("end"); e.kind=o.optString("kind","ACTIVITY");
                e.weekly=o.optBoolean("weekly",true); e.day=o.optInt("day",0); e.dateIso=o.optString("dateIso","");
                e.updatedAt=o.optLong("updatedAt",0); out.add(e);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static synchronized void replaceEvents(Context c, List<PlannerEvent> list) {
        JSONArray a = new JSONArray();
        try { for (PlannerEvent e:list) { JSONObject o=new JSONObject();
            o.put("id",e.id);o.put("child",e.child);o.put("title",e.title);o.put("start",e.start);o.put("end",e.end);
            o.put("kind",e.kind);o.put("weekly",e.weekly);o.put("day",e.day);o.put("dateIso",e.dateIso);o.put("updatedAt",e.updatedAt);a.put(o);} } catch(Exception ignored){}
        p(c).edit().putString(KEY_EVENTS,a.toString()).putBoolean(KEY_SEEDED,true).apply();
    }

    public static synchronized void upsertEvent(Context c, PlannerEvent e) {
        List<PlannerEvent> l=events(c); boolean found=false;
        for(int i=0;i<l.size();i++) if(l.get(i).id.equals(e.id)){l.set(i,e);found=true;break;}
        if(!found)l.add(e); replaceEvents(c,l);
    }
    public static synchronized void deleteEvent(Context c,String id){List<PlannerEvent> l=events(c);l.removeIf(x->x.id.equals(id));replaceEvents(c,l);}

    public static synchronized List<PickupPerson> people(Context c){List<PickupPerson> out=new ArrayList<>();try{JSONArray a=new JSONArray(p(c).getString(KEY_PEOPLE,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);PickupPerson x=new PickupPerson();x.id=o.optString("id");x.name=o.optString("name");x.relation=o.optString("relation");x.phone=o.optString("phone");x.updatedAt=o.optLong("updatedAt",0);JSONArray ca=o.optJSONArray("children");if(ca!=null)for(int j=0;j<ca.length();j++)x.children.add(ca.optString(j));out.add(x);}}catch(Exception ignored){}return out;}
    public static synchronized void replacePeople(Context c,List<PickupPerson> l){JSONArray a=new JSONArray();try{for(PickupPerson x:l){JSONObject o=new JSONObject();o.put("id",x.id);o.put("name",x.name);o.put("relation",x.relation);o.put("phone",x.phone);o.put("updatedAt",x.updatedAt);o.put("children",new JSONArray(x.children));a.put(o);}}catch(Exception ignored){}p(c).edit().putString(KEY_PEOPLE,a.toString()).apply();}
    public static synchronized void upsertPerson(Context c,PickupPerson x){List<PickupPerson>l=people(c);boolean f=false;for(int i=0;i<l.size();i++)if(l.get(i).id.equals(x.id)){l.set(i,x);f=true;break;}if(!f)l.add(x);replacePeople(c,l);}
    public static synchronized void deletePerson(Context c,String id){List<PickupPerson>l=people(c);l.removeIf(x->x.id.equals(id));replacePeople(c,l);}

    public static synchronized List<PickupAssignment> pickups(Context c){List<PickupAssignment>out=new ArrayList<>();try{JSONArray a=new JSONArray(p(c).getString(KEY_PICKUPS,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);PickupAssignment x=new PickupAssignment();x.id=o.optString("id");x.dateIso=o.optString("dateIso");x.child=o.optString("child");x.time=o.optString("time");x.personId=o.optString("personId");x.personName=o.optString("personName");x.note=o.optString("note");x.updatedAt=o.optLong("updatedAt",0);out.add(x);}}catch(Exception ignored){}return out;}
    public static synchronized void replacePickups(Context c,List<PickupAssignment>l){JSONArray a=new JSONArray();try{for(PickupAssignment x:l){JSONObject o=new JSONObject();o.put("id",x.id);o.put("dateIso",x.dateIso);o.put("child",x.child);o.put("time",x.time);o.put("personId",x.personId);o.put("personName",x.personName);o.put("note",x.note);o.put("updatedAt",x.updatedAt);a.put(o);}}catch(Exception ignored){}p(c).edit().putString(KEY_PICKUPS,a.toString()).apply();}
    public static synchronized void upsertPickup(Context c,PickupAssignment x){List<PickupAssignment>l=pickups(c);boolean f=false;for(int i=0;i<l.size();i++)if(l.get(i).id.equals(x.id)){l.set(i,x);f=true;break;}if(!f)l.add(x);replacePickups(c,l);}
    public static synchronized void deletePickup(Context c,String id){List<PickupAssignment>l=pickups(c);l.removeIf(x->x.id.equals(id));replacePickups(c,l);}
}
