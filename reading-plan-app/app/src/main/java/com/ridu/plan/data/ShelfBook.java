package com.ridu.plan.data;

import org.json.JSONException;
import org.json.JSONObject;

public class ShelfBook {
    public String id;
    public String title;
    public String author;
    public String path;
    public String format;
    public int progress;
    public int total;
    public long addedAt;

    public ShelfBook() {
    }

    public ShelfBook(String id, String title, String author, String path,
                     String format, int total, long addedAt) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.path = path;
        this.format = format;
        this.total = total;
        this.addedAt = addedAt;
    }

    public int percent() {
        if (total <= 0) return 0;
        return Math.min(100, Math.round(progress * 100f / total));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("author", author);
        o.put("path", path);
        o.put("format", format);
        o.put("progress", progress);
        o.put("total", total);
        o.put("addedAt", addedAt);
        return o;
    }

    public static ShelfBook fromJson(JSONObject o) {
        ShelfBook b = new ShelfBook();
        b.id = o.optString("id");
        b.title = o.optString("title");
        b.author = o.optString("author");
        b.path = o.optString("path");
        b.format = o.optString("format", "TXT");
        b.progress = o.optInt("progress", 0);
        b.total = o.optInt("total", 0);
        b.addedAt = o.optLong("addedAt", 0);
        return b;
    }
}
