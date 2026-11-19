package com.ridu.plan.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
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
        File file = new File(c.getFilesDir(), "plan.json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        prefs(c).edit().putString(KEY_PLAN_JSON, json).apply();
        cached = parsed;
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
        return prefs(c).getString(KEY_GH_TOKEN, "");
    }

    public static void setGithubToken(Context c, String token) {
        prefs(c).edit().putString(KEY_GH_TOKEN, token == null ? "" : token.trim()).apply();
    }
}
