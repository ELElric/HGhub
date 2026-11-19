package com.ridu.plan.data;

import android.content.Context;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 书单库：保存多份书单原始 JSON。
 * 每份书单可以只写页数，排期由 PlanScheduler 在选中时自动计算。
 */
public final class BooklistStore {
    public static class Record {
        public String id = "";
        public String name = "";
        public String file = "";
        public String sha256 = "";
        public String source = "";
        public String updatedAt = "";
        public int books;
        public int days;
        public int version;
        public boolean builtin;
        public boolean active;
        public boolean isNew;
    }

    private BooklistStore() {
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "booklists");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File indexFile(Context c) {
        return new File(dir(c), "index.json");
    }

    public static synchronized Record save(Context c, String json, String source) throws Exception {
        ensureSeeded(c);
        return saveInternal(c, json, source, false);
    }

    private static Record saveInternal(Context c, String json, String source,
                                       boolean builtin) throws Exception {
        String clean = json.replace("\uFEFF", "").trim();
        Plan plan = Plan.parse(clean);
        String id = PlanStore.planKey(plan);
        if (id.isEmpty()) throw new Exception("书单缺少 id");

        String fileName = id + ".json";
        writeText(new File(dir(c), fileName), clean);

        JSONArray index = readIndex(c);
        JSONObject existing = find(index, id);
        boolean isNew = existing == null;
        JSONObject rec = existing == null ? new JSONObject() : existing;
        rec.put("id", id);
        rec.put("name", plan.planName);
        rec.put("file", fileName);
        rec.put("sha256", sha256(clean));
        rec.put("source", source == null ? "" : source);
        rec.put("updatedAt", LocalDateTime.now().toString());
        rec.put("books", plan.books.size());
        rec.put("days", plan.totalDays);
        rec.put("version", 0);
        rec.put("builtin", builtin);
        if (isNew) index.put(rec);
        writeIndex(c, index);

        Record out = from(rec);
        out.books = plan.books.size();
        out.days = plan.totalDays;
        out.isNew = isNew;
        return out;
    }

    public static synchronized void ensureSeeded(Context c) {
        JSONArray index = readIndex(c);
        boolean hasBuiltin = false;
        for (int i = 0; i < index.length(); i++) {
            JSONObject o = index.optJSONObject(i);
            if (o != null && o.optBoolean("builtin", false)) {
                hasBuiltin = true;
                break;
            }
        }

        if (!hasBuiltin) {
            try (InputStream in = c.getAssets().open("plan.json")) {
                saveInternal(c, readStream(in), "内置", true);
            } catch (Exception ignored) {
            }
        }

        // 把旧版“计划库”里已经归档的计划迁移进书单库。
        File legacyIndex = new File(new File(c.getFilesDir(), "plans"), "index.json");
        if (legacyIndex.exists()) {
            try {
                JSONArray old = new JSONArray(readText(legacyIndex));
                for (int i = 0; i < old.length(); i++) {
                    JSONObject o = old.optJSONObject(i);
                    if (o == null) continue;
                    String file = o.optString("file", "");
                    if (file.isEmpty()) continue;
                    File src = new File(new File(c.getFilesDir(), "plans"), file);
                    if (!src.exists()) continue;
                    String id = o.optString("id", "");
                    JSONArray current = readIndex(c);
                    if (find(current, id) != null) continue;
                    saveInternal(c, readText(src), "旧计划", false);
                }
            } catch (Exception ignored) {
            }
        }

        // 当前正在读的计划也保证出现在书单库里。
        try {
            String current = PlanStore.exportCurrentJson(c);
            Plan plan = Plan.parse(current);
            JSONArray now = readIndex(c);
            if (find(now, PlanStore.planKey(plan)) == null) {
                saveInternal(c, current, "当前计划", false);
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized List<Record> list(Context c) {
        ensureSeeded(c);
        List<Record> out = new ArrayList<>();
        Plan active = null;
        try {
            active = PlanStore.getPlan(c);
        } catch (Exception ignored) {
        }
        String activeId = active == null ? "" : PlanStore.planKey(active);

        JSONArray index = readIndex(c);
        for (int i = 0; i < index.length(); i++) {
            JSONObject o = index.optJSONObject(i);
            if (o == null) continue;
            Record r = from(o);
            r.active = r.id.equals(activeId);
            try {
                Plan p = Plan.parse(readText(new File(dir(c), r.file)));
                r.books = p.books.size();
                r.days = p.totalDays;
            } catch (Exception ignored) {
            }
            out.add(r);
        }
        out.sort((a, b) -> {
            if (a.active != b.active) return a.active ? -1 : 1;
            return b.updatedAt.compareTo(a.updatedAt);
        });
        return out;
    }

    public static synchronized Record findById(Context c, String id) {
        ensureSeeded(c);
        JSONObject o = find(readIndex(c), id);
        return o == null ? null : from(o);
    }

    public static synchronized String getJson(Context c, String id) throws Exception {
        Record r = findById(c, id);
        if (r == null) throw new Exception("书单库里没有这份书单");
        if (r.file.isEmpty()) throw new Exception("书单文件缺失");
        File file = new File(dir(c), r.file);
        if (!file.exists()) throw new Exception("书单文件已丢失");
        return readText(file);
    }

    /**
     * 选中一份书单：先归档当前计划，再按书单里的算法重新排期并设为当前计划。
     */
    public static synchronized void activate(Context c, String id, LocalDate start) throws Exception {
        String json = getJson(c, id);
        Plan target = Plan.parse(json);
        target.startDate = start;
        PlanStore.archiveCurrent(c);
        Record record = findById(c, id);
        PlanStore.replacePlan(c, json, target, start, record == null ? 0 : record.version);
    }

    private static Record from(JSONObject o) {
        Record r = new Record();
        r.id = o.optString("id", "");
        r.name = o.optString("name", "未命名书单");
        r.file = o.optString("file", "");
        r.sha256 = o.optString("sha256", "");
        r.source = o.optString("source", "");
        r.updatedAt = o.optString("updatedAt", "");
        r.books = o.optInt("books", 0);
        r.days = o.optInt("days", 0);
        r.version = o.optInt("version", 0);
        r.builtin = o.optBoolean("builtin", false);
        return r;
    }

    private static JSONObject find(JSONArray index, String id) {
        for (int i = 0; i < index.length(); i++) {
            JSONObject o = index.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    private static JSONArray readIndex(Context c) {
        File file = indexFile(c);
        if (!file.exists()) return new JSONArray();
        try {
            return new JSONArray(readText(file));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void writeIndex(Context c, JSONArray index) throws IOException {
        writeText(indexFile(c), index.toString());
    }

    private static void writeText(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readText(File file) throws IOException {
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

    private static String sha256(String text) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
