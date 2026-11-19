package com.ridu.plan.imports;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ridu.plan.data.Plan;
import com.ridu.plan.data.PlanStore;
import com.ridu.plan.notify.ReminderScheduler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlanImporter {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private PlanImporter() {
    }

    public static String readUri(Context c, Uri uri) throws Exception {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("无法打开文件");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static void importJson(Activity activity, String json, Runnable onDone) {
        final Plan parsed;
        try {
            parsed = Plan.parse(json);
        } catch (Exception e) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("书单格式不对")
                    .setMessage("没有读到有效的 books 数组。\n\n" + e.getMessage())
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("导入《" + parsed.planName + "》")
                .setMessage("共 " + parsed.books.size() + " 本书。要从今天重新开始，还是沿用原来的开始日期？")
                .setPositiveButton("从今天开始", (d, w) -> {
                    try {
                        PlanStore.savePlan(activity, json);
                        PlanStore.setStartDate(activity, LocalDate.now());
                        ReminderScheduler.schedule(activity);
                        Toast.makeText(activity, "已导入，从今天开始", Toast.LENGTH_SHORT).show();
                        if (onDone != null) onDone.run();
                    } catch (Exception e) {
                        toastError(activity, e);
                    }
                })
                .setNegativeButton("沿用原开始日期", (d, w) -> {
                    try {
                        PlanStore.savePlan(activity, json);
                        ReminderScheduler.schedule(activity);
                        Toast.makeText(activity, "已导入，保留当前进度", Toast.LENGTH_SHORT).show();
                        if (onDone != null) onDone.run();
                    } catch (Exception e) {
                        toastError(activity, e);
                    }
                })
                .setNeutralButton("取消", null)
                .show();
    }

    public static void fetchAndImport(Activity activity, String url, Runnable onDone) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(activity, "请先设置书单地址", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(activity, "正在获取书单…", Toast.LENGTH_SHORT).show();
        EXECUTOR.execute(() -> {
            try {
                String json = httpGet(url.trim());
                activity.runOnUiThread(() -> importJson(activity, json, onDone));
            } catch (Exception e) {
                activity.runOnUiThread(() -> new MaterialAlertDialogBuilder(activity)
                        .setTitle("获取失败")
                        .setMessage(String.valueOf(e.getMessage()))
                        .setPositiveButton("知道了", null)
                        .show());
            }
        });
    }

    public static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "RiduPlan/1.0");
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code);
        }
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            conn.disconnect();
        }
    }

    private static void toastError(Activity activity, Exception e) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("导入失败")
                .setMessage(String.valueOf(e.getMessage()))
                .setPositiveButton("知道了", null)
                .show();
    }
}
