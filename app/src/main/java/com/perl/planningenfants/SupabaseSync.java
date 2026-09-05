package com.perl.planningenfants;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Remplace FirebaseSync : synchronisation familiale via Supabase (Postgres + Auth anonyme + REST).
 * Même surface publique que l'ancienne FirebaseSync pour ne rien changer à MainActivity/SyncWorker
 * au-delà du renommage de classe.
 */
public final class SupabaseSync {

    private static final String SUPABASE_URL = "https://sgykmprbccygxuxfzasa.supabase.co";
    private static final String SUPABASE_ANON_KEY = "sb_publishable_Wdv0mV-Q0_bertXJGRcvfg_66au4cXg";

    private static final String PREFS = "family_sync";
    private static final String K_FAMILY = "family_id";
    private static final String K_CODE = "family_code";
    private static final String K_ROLE = "role";
    private static final String K_PROFILE = "child_profile";
    private static final String K_NAME = "family_name";
    private static final String K_ACCESS = "sb_access_token";
    private static final String K_REFRESH = "sb_refresh_token";
    private static final String K_UID = "sb_user_id";

    public interface UiListener { void onSyncChanged(); void onSyncMessage(String message); }
    public interface Result { void done(boolean ok, String message); }

    private static Context app;
    private static UiListener ui;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Handler POLL = new Handler(Looper.getMainLooper());
    private static Runnable pollTick;
    private static boolean polling = false;
    private static final long POLL_INTERVAL_MS = 20000;

    private SupabaseSync() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static boolean isSupabaseConfigured(Context c) { return true; }

    public static boolean hasFamily(Context c) { return !prefs(c).getString(K_FAMILY, "").isEmpty(); }
    public static String familyId(Context c) { return prefs(c).getString(K_FAMILY, ""); }
    public static String familyCode(Context c) { return prefs(c).getString(K_CODE, ""); }
    public static String familyName(Context c) { return prefs(c).getString(K_NAME, "Famille"); }
    public static String role(Context c) { return prefs(c).getString(K_ROLE, "admin"); }
    public static String childProfile(Context c) { return prefs(c).getString(K_PROFILE, ""); }
    public static boolean isAdmin(Context c) { return !hasFamily(c) || "admin".equals(role(c)); }

    private static String accessToken(Context c) { return prefs(c).getString(K_ACCESS, ""); }
    private static String userId(Context c) { return prefs(c).getString(K_UID, ""); }
    private static boolean canWrite(Context c) { return hasFamily(c) && isAdmin(c) && !accessToken(c).isEmpty(); }

    // ---------------- HTTP bas niveau ----------------

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String rawRequest(String method, String url, String jsonBody, String bearer, String prefer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("apikey", SUPABASE_ANON_KEY);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + bearer);
        if (prefer != null) c.setRequestProperty("Prefer", prefer);
        if (jsonBody != null) {
            c.setDoOutput(true);
            c.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
            c.getOutputStream().close();
        }
        int code = c.getResponseCode();
        String text = readAll(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
        c.disconnect();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + text);
        return text;
    }

    private static void ensureAuthBlocking(Context c) throws Exception {
        if (!accessToken(c).isEmpty() && !userId(c).isEmpty()) return;
        String resp = rawRequest("POST", SUPABASE_URL + "/auth/v1/signup", "{}", SUPABASE_ANON_KEY, null);
        JSONObject o = new JSONObject(resp);
        String at = o.optString("access_token", "");
        String rt = o.optString("refresh_token", "");
        JSONObject user = o.optJSONObject("user");
        String uid = user != null ? user.optString("id", "") : "";
        if (at.isEmpty() || uid.isEmpty()) throw new IOException("Réponse Supabase inattendue");
        prefs(c).edit().putString(K_ACCESS, at).putString(K_REFRESH, rt).putString(K_UID, uid).apply();
    }

    private static void refreshTokenBlocking(Context c) throws Exception {
        String rt = prefs(c).getString(K_REFRESH, "");
        if (rt.isEmpty()) throw new IOException("no_refresh_token");
        JSONObject body = new JSONObject(); body.put("refresh_token", rt);
        String resp = rawRequest("POST", SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token", body.toString(), SUPABASE_ANON_KEY, null);
        JSONObject o = new JSONObject(resp);
        String at = o.optString("access_token", "");
        String newRt = o.optString("refresh_token", rt);
        if (at.isEmpty()) throw new IOException("refresh_failed");
        prefs(c).edit().putString(K_ACCESS, at).putString(K_REFRESH, newRt).apply();
    }

    private static String authed(String method, Context c, String path, String jsonBody, String prefer) throws Exception {
        ensureAuthBlocking(c);
        try {
            return rawRequest(method, SUPABASE_URL + path, jsonBody, accessToken(c), prefer);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("HTTP 401")) {
                refreshTokenBlocking(c);
                return rawRequest(method, SUPABASE_URL + path, jsonBody, accessToken(c), prefer);
            }
            throw e;
        }
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static String friendlyError(Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("HTTP 401") || m.contains("HTTP 403") || m.contains("Anonymous")) {
            return "Active « Authentication > Sign In / Providers > Anonymous » dans le projet Supabase.";
        }
        return "Synchro indisponible : " + m;
    }

    // ---------------- cycle de vie ----------------

    public static void start(Context context, UiListener listener) {
        app = context.getApplicationContext(); ui = listener;
        IO.execute(() -> {
            try {
                ensureAuthBlocking(app);
                MAIN.post(() -> { if (hasFamily(app)) { startPolling(); refreshFcmToken(app); } message("Synchronisation familiale active."); });
            } catch (Exception e) {
                MAIN.post(() -> message(friendlyError(e)));
            }
        });
    }

    /** Demande le jeton FCM courant et l'enregistre côté Supabase (push multi-appareils). */
    public static void refreshFcmToken(Context c) {
        if (!hasFamily(c)) return;
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(t -> updateFcmToken(c, t));
        } catch (Exception ignored) { }
    }

    public static void updateFcmToken(Context c, String token) {
        if (!hasFamily(c) || token == null || token.isEmpty() || accessToken(c).isEmpty()) return;
        IO.execute(() -> {
            try {
                JSONObject m = new JSONObject();
                m.put("user_id", userId(c)); m.put("family_id", familyId(c)); m.put("role", role(c));
                m.put("display_name", isAdmin(c) ? "Maman" : childProfile(c)); m.put("child_name", childProfile(c));
                m.put("fcm_token", token);
                authed("POST", c, "/rest/v1/pe_family_members?on_conflict=user_id", m.toString(), "return=minimal,resolution=merge-duplicates");
            } catch (Exception ignored) { }
        });
    }

    /** Best-effort : demande à la fonction Supabase d'envoyer un push aux autres appareils de la famille. */
    private static void notifyFamily(Context c, String title, String body) {
        if (!hasFamily(c) || accessToken(c).isEmpty()) return;
        String fid = familyId(c), uid = userId(c);
        IO.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("family_id", fid); payload.put("title", title); payload.put("body", body); payload.put("exclude_user_id", uid);
                rawRequest("POST", SUPABASE_URL + "/functions/v1/notify-family", payload.toString(), accessToken(c), null);
            } catch (Exception ignored) { }
        });
    }

    private static void startPolling() {
        if (polling) return;
        polling = true;
        pollTick = new Runnable() {
            @Override public void run() {
                if (!polling) return;
                IO.execute(() -> {
                    try { pullAll(app); MAIN.post(SupabaseSync::changed); }
                    catch (Exception ignored) { }
                });
                POLL.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        POLL.postDelayed(pollTick, POLL_INTERVAL_MS);
    }

    private static void stopPolling() { polling = false; if (pollTick != null) POLL.removeCallbacks(pollTick); }

    private static void changed() { if (ui != null) ui.onSyncChanged(); }
    private static void message(String m) { if (ui != null) ui.onSyncMessage(m); }

    // ---------------- espace famille ----------------

    public static void createFamily(Context context, String name, Result result) {
        app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                ensureAuthBlocking(app);
                String uid = userId(app);
                JSONObject fam = new JSONObject(); fam.put("name", name); fam.put("owner_user_id", uid);
                String famResp = authed("POST", app, "/rest/v1/pe_families", fam.toString(), "return=representation");
                String familyId = new JSONArray(famResp).getJSONObject(0).getString("id");

                JSONObject member = new JSONObject();
                member.put("user_id", uid); member.put("family_id", familyId);
                member.put("role", "admin"); member.put("display_name", "Maman"); member.put("child_name", "");
                authed("POST", app, "/rest/v1/pe_family_members", member.toString(), "return=minimal");

                String code = newCode();
                JSONObject invite = new JSONObject();
                invite.put("code", code); invite.put("family_id", familyId);
                invite.put("family_name", name); invite.put("active", true);
                authed("POST", app, "/rest/v1/pe_invites", invite.toString(), "return=minimal");

                saveFamily(app, familyId, code, "admin", "", name);
                uploadAllLocal(app, familyId);
                MAIN.post(() -> { startPolling(); refreshFcmToken(app); result.done(true, code); });
            } catch (Exception e) {
                MAIN.post(() -> result.done(false, friendlyError(e)));
            }
        });
    }

    public static void joinFamily(Context context, String codeRaw, String childName, Result result) {
        app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                ensureAuthBlocking(app);
                String code = codeRaw.trim().toUpperCase(Locale.ROOT);
                String resp = authed("GET", app, "/rest/v1/pe_invites?code=eq." + enc(code) + "&active=eq.true&select=family_id,family_name", null, null);
                JSONArray arr = new JSONArray(resp);
                if (arr.length() == 0) { MAIN.post(() -> result.done(false, "Code famille introuvable ou désactivé.")); return; }
                String familyId = arr.getJSONObject(0).getString("family_id");
                String familyName = arr.getJSONObject(0).optString("family_name", "Famille");
                String uid = userId(app);

                JSONObject member = new JSONObject();
                member.put("user_id", uid); member.put("family_id", familyId);
                member.put("role", "child"); member.put("display_name", childName); member.put("child_name", childName);
                authed("POST", app, "/rest/v1/pe_family_members?on_conflict=user_id", member.toString(), "return=minimal,resolution=merge-duplicates");

                saveFamily(app, familyId, code, "child", childName, familyName);
                MAIN.post(() -> { startPolling(); refreshFcmToken(app); result.done(true, "Espace famille rejoint."); });
            } catch (Exception e) {
                MAIN.post(() -> result.done(false, friendlyError(e)));
            }
        });
    }

    private static void saveFamily(Context c, String id, String code, String role, String profile, String name) {
        prefs(c).edit().putString(K_FAMILY, id).putString(K_CODE, code).putString(K_ROLE, role)
                .putString(K_PROFILE, profile).putString(K_NAME, name == null ? "Famille" : name).apply();
    }

    public static void leaveFamily(Context c) {
        stopPolling();
        String uid = userId(c);
        IO.execute(() -> { try { authed("DELETE", c, "/rest/v1/pe_family_members?user_id=eq." + enc(uid), null, null); } catch (Exception ignored) {} });
        prefs(c).edit().remove(K_FAMILY).remove(K_CODE).remove(K_ROLE).remove(K_PROFILE).remove(K_NAME).apply();
        if (ui != null) ui.onSyncChanged();
    }

    private static void uploadAllLocal(Context c, String familyId) {
        try {
            for (PlannerEvent e : LocalStore.events(c)) authed("POST", c, "/rest/v1/pe_events?on_conflict=id", eventToJson(e, familyId).toString(), "return=minimal,resolution=merge-duplicates");
            for (PickupPerson p : LocalStore.people(c)) authed("POST", c, "/rest/v1/pe_people?on_conflict=id", personToJson(p, familyId).toString(), "return=minimal,resolution=merge-duplicates");
            for (PickupAssignment a : LocalStore.pickups(c)) authed("POST", c, "/rest/v1/pe_pickups?on_conflict=id", pickupToJson(a, familyId).toString(), "return=minimal,resolution=merge-duplicates");
        } catch (Exception ignored) { }
    }

    // ---------------- lecture (poll / worker) ----------------

    /** Tire tout depuis Supabase et remplace le cache local. Retourne un horodatage max (pour détecter un changement) ou -1 si pas de famille. */
    public static long pullAll(Context c) throws Exception {
        if (!hasFamily(c)) return -1;
        ensureAuthBlocking(c);
        String fid = familyId(c);
        long max = 0;

        String evResp = authed("GET", c, "/rest/v1/pe_events?family_id=eq." + enc(fid) + "&select=*", null, null);
        JSONArray evArr = new JSONArray(evResp);
        List<PlannerEvent> events = new ArrayList<>();
        for (int i = 0; i < evArr.length(); i++) { PlannerEvent e = eventFromJson(evArr.getJSONObject(i)); events.add(e); max = Math.max(max, e.updatedAt); }
        LocalStore.replaceEvents(c, events);

        String peResp = authed("GET", c, "/rest/v1/pe_people?family_id=eq." + enc(fid) + "&select=*", null, null);
        JSONArray peArr = new JSONArray(peResp);
        List<PickupPerson> people = new ArrayList<>();
        for (int i = 0; i < peArr.length(); i++) { PickupPerson p = personFromJson(peArr.getJSONObject(i)); people.add(p); max = Math.max(max, p.updatedAt); }
        LocalStore.replacePeople(c, people);

        String piResp = authed("GET", c, "/rest/v1/pe_pickups?family_id=eq." + enc(fid) + "&select=*", null, null);
        JSONArray piArr = new JSONArray(piResp);
        List<PickupAssignment> pickups = new ArrayList<>();
        for (int i = 0; i < piArr.length(); i++) { PickupAssignment a = pickupFromJson(piArr.getJSONObject(i)); pickups.add(a); max = Math.max(max, a.updatedAt); }
        LocalStore.replacePickups(c, pickups);

        ReminderScheduler.scheduleAll(c);
        return max;
    }

    // ---------------- écriture ----------------

    public static void saveEvent(Context c, PlannerEvent e) {
        LocalStore.upsertEvent(c, e); ReminderScheduler.scheduleAll(c);
        if (canWrite(c)) { String fid = familyId(c); IO.execute(() -> { try { authed("POST", c, "/rest/v1/pe_events?on_conflict=id", eventToJson(e, fid).toString(), "return=minimal,resolution=merge-duplicates"); } catch (Exception ignored) {} }); notifyFamily(c, "Planning mis à jour", e.child + " • " + e.title); }
        changed();
    }
    public static void deleteEvent(Context c, String id) {
        LocalStore.deleteEvent(c, id); ReminderScheduler.scheduleAll(c);
        if (canWrite(c)) { IO.execute(() -> { try { authed("DELETE", c, "/rest/v1/pe_events?id=eq." + enc(id), null, null); } catch (Exception ignored) {} }); notifyFamily(c, "Planning mis à jour", "Un événement a été supprimé"); }
        changed();
    }
    public static void savePerson(Context c, PickupPerson p) {
        LocalStore.upsertPerson(c, p);
        if (canWrite(c)) { String fid = familyId(c); IO.execute(() -> { try { authed("POST", c, "/rest/v1/pe_people?on_conflict=id", personToJson(p, fid).toString(), "return=minimal,resolution=merge-duplicates"); } catch (Exception ignored) {} }); notifyFamily(c, "Personnes autorisées", p.name + " mis à jour"); }
        changed();
    }
    public static void deletePerson(Context c, String id) {
        LocalStore.deletePerson(c, id);
        if (canWrite(c)) { IO.execute(() -> { try { authed("DELETE", c, "/rest/v1/pe_people?id=eq." + enc(id), null, null); } catch (Exception ignored) {} }); notifyFamily(c, "Personnes autorisées", "Une personne a été retirée"); }
        changed();
    }
    public static void savePickup(Context c, PickupAssignment a) {
        LocalStore.upsertPickup(c, a); ReminderScheduler.scheduleAll(c);
        if (canWrite(c)) { String fid = familyId(c); IO.execute(() -> { try { authed("POST", c, "/rest/v1/pe_pickups?on_conflict=id", pickupToJson(a, fid).toString(), "return=minimal,resolution=merge-duplicates"); } catch (Exception ignored) {} }); notifyFamily(c, "Récupération", "Qui récupère " + a.child + " : " + a.personName); }
        changed();
    }
    public static void deletePickup(Context c, String id) {
        LocalStore.deletePickup(c, id); ReminderScheduler.scheduleAll(c);
        if (canWrite(c)) { IO.execute(() -> { try { authed("DELETE", c, "/rest/v1/pe_pickups?id=eq." + enc(id), null, null); } catch (Exception ignored) {} }); notifyFamily(c, "Récupération", "Une récupération a été supprimée"); }
        changed();
    }

    // ---------------- mapping JSON ----------------

    private static JSONObject eventToJson(PlannerEvent e, String familyId) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", e.id); o.put("family_id", familyId); o.put("child", e.child); o.put("title", e.title);
        o.put("start_time", e.start); o.put("end_time", e.end); o.put("kind", e.kind);
        o.put("weekly", e.weekly); o.put("day", e.day); o.put("date_iso", e.dateIso); o.put("updated_at", e.updatedAt);
        return o;
    }
    private static PlannerEvent eventFromJson(JSONObject o) {
        PlannerEvent e = new PlannerEvent();
        e.id = o.optString("id", ""); e.child = o.optString("child", ""); e.title = o.optString("title", "");
        e.start = o.optString("start_time", ""); e.end = o.optString("end_time", ""); e.kind = o.optString("kind", "ACTIVITY");
        e.weekly = o.optBoolean("weekly", true); e.day = o.optInt("day", 0); e.dateIso = o.optString("date_iso", "");
        e.updatedAt = o.optLong("updated_at", 0L);
        return e;
    }

    private static JSONObject personToJson(PickupPerson p, String familyId) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", p.id); o.put("family_id", familyId); o.put("name", p.name); o.put("relation", p.relation);
        o.put("phone", p.phone); o.put("children", new JSONArray(p.children)); o.put("updated_at", p.updatedAt);
        return o;
    }
    private static PickupPerson personFromJson(JSONObject o) {
        PickupPerson p = new PickupPerson();
        p.id = o.optString("id", ""); p.name = o.optString("name", ""); p.relation = o.optString("relation", "");
        p.phone = o.optString("phone", ""); p.updatedAt = o.optLong("updated_at", 0L);
        JSONArray arr = o.optJSONArray("children");
        if (arr != null) for (int i = 0; i < arr.length(); i++) p.children.add(arr.optString(i, ""));
        return p;
    }

    private static JSONObject pickupToJson(PickupAssignment a, String familyId) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", a.id); o.put("family_id", familyId); o.put("date_iso", a.dateIso); o.put("child", a.child);
        o.put("time_slot", a.time); o.put("person_id", a.personId); o.put("person_name", a.personName);
        o.put("note", a.note); o.put("updated_at", a.updatedAt);
        return o;
    }
    private static PickupAssignment pickupFromJson(JSONObject o) {
        PickupAssignment a = new PickupAssignment();
        a.id = o.optString("id", ""); a.dateIso = o.optString("date_iso", ""); a.child = o.optString("child", "");
        a.time = o.optString("time_slot", ""); a.personId = o.optString("person_id", ""); a.personName = o.optString("person_name", "");
        a.note = o.optString("note", ""); a.updatedAt = o.optLong("updated_at", 0L);
        return a;
    }

    private static String newCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; SecureRandom r = new SecureRandom();
        StringBuilder s = new StringBuilder(); for (int i = 0; i < 10; i++) s.append(chars.charAt(r.nextInt(chars.length())));
        return s.toString();
    }
}
