package com.ridu.plan.imports;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ridu.plan.data.Plan;
import com.ridu.plan.data.PlanStore;
import com.ridu.plan.notify.ReminderScheduler;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlanImporter {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * 仓库里的书单清单。私有库需要“设置 → GitHub 只读 Token”。
     */
    public static final String DEFAULT_PLAN_URL =
            "https://api.github.com/repos/ELElric/APK-/contents/apps/ridu-plan/plans/manifest.json";

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

    /**
     * 一键检查并应用书单更新：读取 manifest.json，下载 plan 文件并校验 SHA-256，
     * 校验通过后直接替换本地计划，保留当前开始日期，无需手动导入文件。
     */
    public static void checkForPlanUpdate(Activity activity, String url, boolean silent, Runnable onDone) {
        final String source = (url == null || url.trim().isEmpty()) ? DEFAULT_PLAN_URL : url.trim();
        if (!silent) Toast.makeText(activity, "正在检查书单…", Toast.LENGTH_SHORT).show();
        EXECUTOR.execute(() -> {
            try {
                String token = PlanStore.getGithubToken(activity);
                String manifestBody = httpGet(source, token).replace("\uFEFF", "").trim();
                JSONObject manifest = new JSONObject(manifestBody);

                String planJson;
                int version;
                if (manifest.has("books")) {
                    // 地址直接指向 plan.json
                    planJson = manifestBody;
                    version = manifest.optInt("planVersion", 0);
                } else {
                    version = manifest.optInt("planVersion", 0);
                    String file = manifest.optString("file", "").trim();
                    if (file.isEmpty()) throw new Exception("书单清单缺少 file 字段");
                    planJson = httpGet(sibling(source, file), token);
                    String expected = manifest.optString("sha256", "").trim();
                    if (!expected.isEmpty() && !expected.equalsIgnoreCase(sha256(planJson))) {
                        throw new Exception("书单文件校验失败（SHA-256 不匹配）");
                    }
                }

                final Plan parsed = Plan.parse(planJson);
                final int newVersion = version;
                final int current = PlanStore.getPlanVersion(activity);
                if (newVersion > 0 && current >= newVersion) {
                    if (!silent) {
                        activity.runOnUiThread(() -> Toast.makeText(activity,
                                "书单已是最新（v" + current + "）", Toast.LENGTH_SHORT).show());
                    }
                    if (onDone != null) activity.runOnUiThread(onDone);
                    return;
                }

                activity.runOnUiThread(() -> {
                    try {
                        PlanStore.savePlan(activity, planJson);
                        PlanStore.setPlanVersion(activity, newVersion);
                        ReminderScheduler.schedule(activity);
                        Toast.makeText(activity, "书单已更新：共 " + parsed.books.size()
                                + " 本，保留当前开始日期", Toast.LENGTH_LONG).show();
                        if (onDone != null) onDone.run();
                    } catch (Exception e) {
                        toastError(activity, e);
                    }
                });
            } catch (Exception e) {
                if (silent) return;
                String message = String.valueOf(e.getMessage());
                if (message.contains("404")) {
                    message += "\n\n私有库需要在“设置 → GitHub 只读 Token”里填入 Token。";
                }
                final String shown = message;
                activity.runOnUiThread(() -> new MaterialAlertDialogBuilder(activity)
                        .setTitle("书单更新失败")
                        .setMessage(shown)
                        .setPositiveButton("知道了", null)
                        .show());
            }
        });
    }

    private static String sibling(String url, String file) {
        int q = url.indexOf('?');
        String base = q >= 0 ? url.substring(0, q) : url;
        int slash = base.lastIndexOf('/');
        return base.substring(0, slash + 1) + Uri.encode(file);
    }

    private static String sha256(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static String httpGet(String url) throws Exception {
        return httpGet(url, "");
    }

    public static String httpGet(String url, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "RiduPlan/1.0");
        conn.setRequestProperty("Accept", url.contains("api.github.com")
                ? "application/vnd.github.raw"
                : "application/json,text/plain,*/*");
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
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
