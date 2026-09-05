package com.perl.planningenfants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ScheduleData {
    private ScheduleData() {}

    public static final int MON = 2; // Calendar.MONDAY
    public static final int TUE = 3;
    public static final int WED = 4;
    public static final int THU = 5;
    public static final int FRI = 6;
    public static final int SAT = 7;

    public enum Kind { SCHOOL, GARDERIE, ACTIVITY }

    public static final class Event {
        public final String id;
        public final String child;
        public final int day;
        public final String start;
        public final String end;
        public final String label;
        public final Kind kind;

        public Event(String id, String child, int day, String start, String end, String label, Kind kind) {
            this.id = id;
            this.child = child;
            this.day = day;
            this.start = start;
            this.end = end;
            this.label = label;
            this.kind = kind;
        }
    }

    public static List<Event> events() {
        List<Event> e = new ArrayList<>();

        // ANDREW — lycée + activités
        e.add(new Event("and_mon_school", "Andrew", MON, "07:30", "17:30", "Cours", Kind.SCHOOL));
        e.add(new Event("and_tue_school", "Andrew", TUE, "07:30", "17:30", "Cours", Kind.SCHOOL));
        e.add(new Event("and_wed_school", "Andrew", WED, "08:30", "11:30", "Cours", Kind.SCHOOL));
        e.add(new Event("and_thu_school", "Andrew", THU, "07:30", "11:30", "Cours", Kind.SCHOOL));
        e.add(new Event("and_fri_school", "Andrew", FRI, "07:30", "17:30", "Cours", Kind.SCHOOL));
        e.add(new Event("and_mon_basket", "Andrew", MON, "18:30", "20:00", "🏀 Basket", Kind.ACTIVITY));
        e.add(new Event("and_tue_basket", "Andrew", TUE, "18:30", "20:00", "🏀 Basket", Kind.ACTIVITY));
        e.add(new Event("and_fri_basket", "Andrew", FRI, "18:30", "20:00", "🏀 Basket", Kind.ACTIVITY));
        e.add(new Event("and_sat_piano", "Andrew", SAT, "08:00", "09:00", "🎹 Piano", Kind.ACTIVITY));

        // SHANAYSS — collège + gym
        e.add(new Event("sha_mon_school", "Shanayss", MON, "08:25", "16:00", "Cours", Kind.SCHOOL));
        e.add(new Event("sha_tue_school", "Shanayss", TUE, "07:25", "17:00", "Cours", Kind.SCHOOL));
        e.add(new Event("sha_wed_school", "Shanayss", WED, "07:25", "11:30", "Cours", Kind.SCHOOL));
        e.add(new Event("sha_thu_school", "Shanayss", THU, "07:25", "16:00", "Cours", Kind.SCHOOL));
        e.add(new Event("sha_fri_school", "Shanayss", FRI, "07:25", "12:30", "Cours", Kind.SCHOOL));
        e.add(new Event("sha_mon_gym", "Shanayss", MON, "17:00", "19:00", "🤸🏽‍♀️ Gym", Kind.ACTIVITY));
        e.add(new Event("sha_sat_gym", "Shanayss", SAT, "08:00", "10:00", "🤸🏽‍♀️ Gym", Kind.ACTIVITY));

        // KELVYN — garderie + école
        for (int d : Arrays.asList(MON, TUE, WED, THU, FRI)) {
            String dayCode = String.valueOf(d);
            e.add(new Event("kel_" + dayCode + "_gard_m", "Kelvyn", d, "06:30", "07:50", "Garderie matin", Kind.GARDERIE));
            e.add(new Event("kel_" + dayCode + "_school", "Kelvyn", d, "07:50", "16:00", "Cours", Kind.SCHOOL));
            e.add(new Event("kel_" + dayCode + "_gard_s", "Kelvyn", d, "16:00", "18:00", "Garderie soir (18h max)", Kind.GARDERIE));
        }
        e.add(new Event("kel_tue_basket", "Kelvyn", TUE, "17:30", "19:00", "🏀 Basket", Kind.ACTIVITY));
        e.add(new Event("kel_fri_basket", "Kelvyn", FRI, "17:30", "19:00", "🏀 Basket", Kind.ACTIVITY));

        return e;
    }
}
