package com.perl.planningenfants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PickupPerson {
    public String id = "";
    public String name = "";
    public String relation = "";
    public String phone = "";
    public List<String> children = new ArrayList<>();
    public long updatedAt = 0L;

    public PickupPerson() {}

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id); m.put("name", name); m.put("relation", relation);
        m.put("phone", phone); m.put("children", children); m.put("updatedAt", updatedAt);
        return m;
    }

    }
