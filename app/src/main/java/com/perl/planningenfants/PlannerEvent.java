package com.perl.planningenfants;

import java.util.HashMap;
import java.util.Map;

public class PlannerEvent {
    public String id = "";
    public String child = "";
    public String title = "";
    public String start = "";
    public String end = "";
    public String kind = "ACTIVITY";
    public boolean weekly = true;
    public int day = 0; // Calendar.DAY_OF_WEEK for weekly events
    public String dateIso = ""; // yyyy-MM-dd for one-time events
    public long updatedAt = 0L;

    public PlannerEvent() {}

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id); m.put("child", child); m.put("title", title);
        m.put("start", start); m.put("end", end); m.put("kind", kind);
        m.put("weekly", weekly); m.put("day", day); m.put("dateIso", dateIso);
        m.put("updatedAt", updatedAt);
        return m;
    }

    }
