package com.ridu.plan.reader;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.ridu.plan.data.Book;

import java.util.Locale;

/**
 * 把电子书交给外部阅读器（优先静读天下 Moon+ Reader）打开。
 * 只使用标准 ACTION_VIEW，不依赖任何私有 SDK。
 */
public final class ExternalReader {
    private static final String PREFS = "ridu_external_reader";
    private static final String MOON_FREE = "com.flyersoft.moonreader";
    private static final String MOON_PRO = "com.flyersoft.moonreaderp";

    private ExternalReader() {
    }

    public static String key(Book book) {
        return "book_" + book.title + "|" + book.author;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Uri boundUri(Context c, String key) {
        String saved = prefs(c).getString(key, null);
        return saved == null ? null : Uri.parse(saved);
    }

    public static void bind(Context c, String key, Uri uri) {
        try {
            c.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString(key, uri.toString()).apply();
    }

    public static void unbind(Context c, String key) {
        prefs(c).edit().remove(key).apply();
    }

    public static String fileName(Context c, Uri uri) {
        String name = null;
        try (Cursor cur = c.getContentResolver().query(uri, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cur.getString(idx);
            }
        } catch (Exception ignored) {
        }
        if (name == null) name = uri.getLastPathSegment();
        return name == null ? "book" : name;
    }

    public static String mime(Context c, Uri uri, String name) {
        String type = null;
        try {
            type = c.getContentResolver().getType(uri);
        } catch (Exception ignored) {
        }
        if (type != null && !"application/octet-stream".equals(type)) return type;

        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".epub")) return "application/epub+zip";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".mobi")) return "application/x-mobipocket-ebook";
        if (lower.endsWith(".azw3") || lower.endsWith(".azw")) return "application/vnd.amazon.ebook";
        if (lower.endsWith(".fb2")) return "application/fb2";
        return "application/octet-stream";
    }

    public static String moonPackage(Context c) {
        for (String pkg : new String[]{MOON_PRO, MOON_FREE}) {
            try {
                c.getPackageManager().getPackageInfo(pkg, 0);
                return pkg;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static boolean open(Context c, Uri uri) {
        String name = fileName(c, uri);
        String type = mime(c, uri, name);

        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, type);
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        String moon = moonPackage(c);
        if (moon != null) {
            Intent explicit = new Intent(view);
            explicit.setPackage(moon);
            try {
                c.startActivity(explicit);
                return true;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
        try {
            c.startActivity(Intent.createChooser(view, "选择阅读应用"));
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
