package com.ridu.plan.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PlanStore {
    private static final String PREFS = "ridu_prefs";
    private static final String KEY_PLAN_JSON = "plan_json";
    private static final String KEY_START = "start_date";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";
    private static final String KEY_CHECKINS = "checkins";
    private static final String KEY_PLAN_URL = "plan_update_url";
    private static final String KEY_PLAN_VERSION = "plan_version";
    private static final String KEY_APP_URL = "app_update_url";
    private static final String KEY_AUTO_UPDATE = "auto_update";
    private static final String KEY_GH_TOKEN = "gh_token";

    private static Plan cached;

    private PlanStore() {
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized Plan getPlan(Context c) {
        if (cached != null) return cached;
        String json = readLocalPlan(c);
        try {
            cached = Plan.parse(json);
        } catch (Exception e) {
            try {
                cached = Plan.parse(readAsset(c));
            } catch (Exception fatal) {
                throw new IllegalStateException("无法读取内置书单", fatal);
            }
        }
        return cached;
    }

    public static synchronized void savePlan(Context c, String json) throws Exception {
        Plan parsed = Plan.parse(json);
        Plan current = null;
        try {
            current = getPlan(c);
        } catch (Exception ignored) {
        }
        if (current != null && !planKey(current).equals(planKey(parsed))) {
            archive(c, current, readLocalPlan(c), getStartDate(c), getPlanVersion(c));
        }
        File file = new File(c.getFilesDir(), "plan.json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        prefs(c).edit().putString(KEY_PLAN_JSON, json).apply();
        cached = parsed;
    }

    /** 直接替换当前计划，不再归档（调用方负责先归档）。 */
    public static synchronized void replacePlan(Context c, String json, Plan parsed,
                                                LocalDate start, int version) throws IOException {
        File file = new File(c.getFilesDir(), "plan.json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        prefs(c).edit().putString(KEY_PLAN_JSON, json).apply();
        cached = parsed;
        setStartDate(c, start);
        setPlanVersion(c, version);
    }

    public static String exportCurrentJson(Context c) {
        return readLocalPlan(c);
    }

    public static String planKey(Plan plan) {
        String id = plan.planId == null ? "" : plan.planId.trim();
        if (id.isEmpty()) {
            id = "p" + Integer.toHexString((plan.planName + "|" + plan.totalDays).hashCode());
        }
        return id.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    public static class PlanRecord {
        public String id = "";
        public String name = "";
        public String file = "";
        public String savedAt = "";
        public int version;
        public int books;
        public int days;
        public LocalDate startDate = LocalDate.now();
        public boolean active;
        public boolean builtin;
    }

    /** 当前计划先存入计划库，供之后切回。 */
    public static synchronized void archiveCurrent(Context c) {
        try {
            Plan plan = getPlan(c);
            archive(c, plan, readLocalPlan(c), getStartDate(c), getPlanVersion(c));
        } catch (Exception ignored) {
        }
    }

    private static void archive(Context c, Plan plan, String json, LocalDate start, int version) {
        try {
            String id = planKey(plan);
            String fileName = id + ".json";
            writeText(new File(plansDir(c), fileName), json);

            JSONArray index = readIndex(c);
            JSONObject record = null;
            for (int i = 0; i < index.length(); i++) {
                JSONObject o = index.optJSONObject(i);
                if (o != null && id.equals(o.optString("id"))) {
                    record = o;
                    break;
                }
            }
            if (record == null) {
                record = new JSONObject();
                index.put(record);
            }
            record.put("id", id);
            record.put("name", plan.planName);
            record.put("file", fileName);
            record.put("version", version);
            record.put("books", plan.books.size());
            record.put("days", plan.totalDays);
            record.put("startDate", start.toString());
            record.put("savedAt", LocalDate.now().toString());
            writeText(indexFile(c), index.toString());
        } catch (Exception ignored) {
        }
    }

    public static synchronized List<PlanRecord> listArchived(Context c) {
        List<PlanRecord> out = new ArrayList<>();
        Plan current = null;
        try {
            current = getPlan(c);
        } catch (Exception ignored) {
        }
        String activeId = current == null ? "" : planKey(current);

        JSONArray index = readIndex(c);
        for (int i = 0; i < index.length(); i++) {
            JSONObject o = index.optJSONObject(i);
            if (o == null) continue;
            PlanRecord r = new PlanRecord();
            r.id = o.optString("id", "");
            r.name = o.optString("name", "未命名计划");
            r.file = o.optString("file", "");
            r.savedAt = o.optString("savedAt", "");
            r.version = o.optInt("version", 0);
            r.books = o.optInt("books", 0);
            r.days = o.optInt("days", 0);
            try {
                r.startDate = LocalDate.parse(o.optString("startDate", ""));
            } catch (Exception ignored) {
            }
            r.active = r.id.equals(activeId);
            out.add(r);
        }

        // 内置的原始书单始终保留在计划库里，即使从未切换过。
        try {
            Plan builtin = Plan.parse(readAsset(c));
            String builtinId = planKey(builtin);
            boolean found = false;
            for (PlanRecord r : out) {
                if (r.id.equals(builtinId)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                PlanRecord r = new PlanRecord();
                r.id = builtinId;
                r.name = builtin.planName;
                r.books = builtin.books.size();
                r.days = builtin.totalDays;
                r.version = 0;
                r.builtin = true;
                r.active = builtinId.equals(activeId);
                out.add(r);
            }
        } catch (Exception ignored) {
        }

        out.sort((a, b) -> Boolean.compare(b.active, a.active));
        return out;
    }

    public static synchronized void activateArchived(Context c, String id) throws Exception {
        JSONObject record = null;
        JSONArray index = readIndex(c);
        for (int i = 0; i < index.length(); i++) {
            JSONObject o = index.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) {
                record = o;
                break;
            }
        }

        String json;
        String fileName = record == null ? "" : record.optString("file", "");
        if (!fileName.isEmpty()) {
            json = readFile(new File(plansDir(c), fileName));
        } else {
            String asset = readAsset(c);
            if (!planKey(Plan.parse(asset)).equals(id)) {
                throw new Exception("没有找到该计划");
            }
            json = asset;
        }

        Plan target = Plan.parse(json);
        Plan current = null;
        try {
            current = getPlan(c);
        } catch (Exception ignored) {
        }
        if (current != null && !planKey(current).equals(planKey(target))) {
            archive(c, current, readLocalPlan(c), getStartDate(c), getPlanVersion(c));
        }

        writeText(new File(c.getFilesDir(), "plan.json"), json);
        prefs(c).edit().putString(KEY_PLAN_JSON, json).apply();
        String date = record == null ? "" : record.optString("startDate", "");
        if (!date.isEmpty()) {
            try {
                setStartDate(c, LocalDate.parse(date));
            } catch (Exception ignored) {
                setStartDate(c, LocalDate.now());
            }
        } else {
            setStartDate(c, LocalDate.now());
        }
        setPlanVersion(c, record == null ? 0 : record.optInt("version", 0));
        cached = target;
    }

    private static File plansDir(Context c) {
        File dir = new File(c.getFilesDir(), "plans");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File indexFile(Context c) {
        return new File(plansDir(c), "index.json");
    }

    private static JSONArray readIndex(Context c) {
        File file = indexFile(c);
        if (!file.exists()) return new JSONArray();
        try {
            return new JSONArray(readFile(file));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void writeText(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static synchronized void resetCache() {
        cached = null;
    }

    private static String readLocalPlan(Context c) {
        File file = new File(c.getFilesDir(), "plan.json");
        if (file.exists()) {
            try {
                return readFile(file);
            } catch (IOException ignored) {
                // fall through to asset
            }
        }
        String saved = prefs(c).getString(KEY_PLAN_JSON, null);
        if (saved != null && !saved.isEmpty()) return saved;
        try {
            return readAsset(c);
        } catch (IOException e) {
            return "{\"books\":[]}";
        }
    }

    private static String readAsset(Context c) throws IOException {
        try (InputStream in = c.getAssets().open("plan.json")) {
            return readStream(in);
        }
    }

    private static String readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readStream(in);
        }
    }

    private static String readStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    public static LocalDate getStartDate(Context c) {
        String s = prefs(c).getString(KEY_START, null);
        if (s != null) {
            try {
                return LocalDate.parse(s);
            } catch (Exception ignored) {
            }
        }
        LocalDate today = LocalDate.now();
        prefs(c).edit().putString(KEY_START, today.toString()).apply();
        return today;
    }

    public static void setStartDate(Context c, LocalDate date) {
        prefs(c).edit().putString(KEY_START, date.toString()).apply();
    }

    public static int getReminderHour(Context c) {
        return prefs(c).getInt(KEY_REMINDER_HOUR, 8);
    }

    public static int getReminderMinute(Context c) {
        return prefs(c).getInt(KEY_REMINDER_MINUTE, 0);
    }

    public static void setReminderTime(Context c, int hour, int minute) {
        prefs(c).edit()
                .putInt(KEY_REMINDER_HOUR, hour)
                .putInt(KEY_REMINDER_MINUTE, minute)
                .apply();
    }

    public static boolean isReminderEnabled(Context c) {
        return prefs(c).getBoolean(KEY_REMINDER_ENABLED, true);
    }

    public static void setReminderEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply();
    }

    public static boolean isCheckedIn(Context c, LocalDate date) {
        return prefs(c).getStringSet(KEY_CHECKINS, new HashSet<>()).contains(date.toString());
    }

    public static boolean setCheckedIn(Context c, LocalDate date, boolean checked) {
        Set<String> set = new HashSet<>(prefs(c).getStringSet(KEY_CHECKINS, new HashSet<>()));
        if (checked) set.add(date.toString());
        else set.remove(date.toString());
        prefs(c).edit().putStringSet(KEY_CHECKINS, set).apply();
        return checked;
    }

    public static boolean toggleCheckIn(Context c, LocalDate date) {
        return setCheckedIn(c, date, !isCheckedIn(c, date));
    }

    public static String getPlanUpdateUrl(Context c) {
        return prefs(c).getString(KEY_PLAN_URL, "");
    }

    public static void setPlanUpdateUrl(Context c, String url) {
        prefs(c).edit().putString(KEY_PLAN_URL, url == null ? "" : url.trim()).apply();
    }

    public static int getPlanVersion(Context c) {
        return prefs(c).getInt(KEY_PLAN_VERSION, 0);
    }

    public static void setPlanVersion(Context c, int version) {
        prefs(c).edit().putInt(KEY_PLAN_VERSION, version).apply();
    }

    public static String getAppUpdateUrl(Context c) {
        return prefs(c).getString(KEY_APP_URL, "");
    }

    public static void setAppUpdateUrl(Context c, String url) {
        prefs(c).edit().putString(KEY_APP_URL, url == null ? "" : url.trim()).apply();
    }

    public static boolean isAutoUpdate(Context c) {
        return prefs(c).getBoolean(KEY_AUTO_UPDATE, true);
    }

    public static void setAutoUpdate(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply();
    }

    public static String getGithubToken(Context c) {
        String saved = prefs(c).getString(KEY_GH_TOKEN, "");
        if (saved != null && !saved.trim().isEmpty()) return saved.trim();
        return TokenVault.get();
    }

    public static boolean hasCustomGithubToken(Context c) {
        String saved = prefs(c).getString(KEY_GH_TOKEN, "");
        return saved != null && !saved.trim().isEmpty();
    }

    public static void setGithubToken(Context c, String token) {
        prefs(c).edit().putString(KEY_GH_TOKEN, token == null ? "" : token.trim()).apply();
    }
}
