package com.ridu.plan.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShelfStore {
    private static final String PREFS = "ridu_shelf";
    private static final String KEY_BOOKS = "books";

    private ShelfStore() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static File booksDir(Context c) {
        File dir = new File(c.getFilesDir(), "books");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File unpackDir(Context c, String id) {
        return new File(c.getFilesDir(), "unpacked/" + id);
    }

    public static List<ShelfBook> list(Context c) {
        List<ShelfBook> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_BOOKS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(ShelfBook.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (a, b) -> Long.compare(b.addedAt, a.addedAt));
        return out;
    }

    public static ShelfBook byId(Context c, String id) {
        for (ShelfBook b : list(c)) {
            if (id.equals(b.id)) return b;
        }
        return null;
    }

    public static void add(Context c, ShelfBook book) {
        List<ShelfBook> books = list(c);
        books.add(0, book);
        save(c, books);
    }

    public static void updateProgress(Context c, String id, int progress, int total) {
        List<ShelfBook> books = list(c);
        for (ShelfBook b : books) {
            if (id.equals(b.id)) {
                b.progress = progress;
                if (total > 0) b.total = total;
                break;
            }
        }
        save(c, books);
    }

    public static void remove(Context c, ShelfBook book) {
        List<ShelfBook> books = list(c);
        for (int i = books.size() - 1; i >= 0; i--) {
            if (book.id.equals(books.get(i).id)) books.remove(i);
        }
        save(c, books);
        if (book.path != null) new File(book.path).delete();
        deleteRecursive(unpackDir(c, book.id));
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File child : children) deleteRecursive(child);
        f.delete();
    }

    private static void save(Context c, List<ShelfBook> books) {
        JSONArray arr = new JSONArray();
        try {
            for (ShelfBook b : books) arr.put(b.toJson());
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString(KEY_BOOKS, arr.toString()).apply();
    }
}
