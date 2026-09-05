package com.perl.planningenfants;

import java.util.HashMap;
import java.util.Map;

public class PickupAssignment {
    public String id = "";
    public String dateIso = "";
    public String child = "";
    public String time = "";
    public String personId = "";
    public String personName = "";
    public String note = "";
    public long updatedAt = 0L;

    public PickupAssignment() {}

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id); m.put("dateIso", dateIso); m.put("child", child);
        m.put("time", time); m.put("personId", personId); m.put("personName", personName);
        m.put("note", note); m.put("updatedAt", updatedAt);
        return m;
    }

    }
