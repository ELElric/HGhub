package com.ridu.plan.update;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ridu.plan.BuildConfig;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateChecker {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private UpdateChecker() {
    }

    public static class VersionInfo {
        public int versionCode;
        public String versionName = "";
        public String apkUrl = "";
        public String notes = "";
        public String sha256 = "";
    }

    public static void checkForUpdate(Activity activity, String url, boolean silent) {
        if (url == null || url.trim().isEmpty()) {
            if (!silent) Toast.makeText(activity, "请先设置应用更新地址", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!silent) Toast.makeText(activity, "正在检查更新…", Toast.LENGTH_SHORT).show();
        EXECUTOR.execute(() -> {
            try {
                VersionInfo info = fetchInfo(url.trim());
                String apkUrl = resolve(url.trim(), info.apkUrl);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (info.versionCode > BuildConfig.VERSION_CODE) {
                        showUpdateDialog(activity, info, apkUrl);
                    } else if (!silent) {
                        Toast.makeText(activity, "已是最新版本 " + BuildConfig.VERSION_NAME, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                if (!silent) {
                    activity.runOnUiThread(() -> new MaterialAlertDialogBuilder(activity)
                            .setTitle("检查更新失败")
                            .setMessage(String.valueOf(e.getMessage()))
                            .setPositiveButton("知道了", null)
                            .show());
                }
            }
        });
    }

    private static VersionInfo fetchInfo(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "RiduPlan/" + BuildConfig.VERSION_NAME);
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        String body;
        try (InputStream in = conn.getInputStream()) {
            body = readAll(in);
        } finally {
            conn.disconnect();
        }
        JSONObject o = new JSONObject(body);
        VersionInfo info = new VersionInfo();
        info.versionCode = o.optInt("versionCode", 0);
        info.versionName = o.optString("versionName", "");
        info.apkUrl = o.optString("apkUrl", "");
        info.notes = o.optString("notes", "");
        info.sha256 = o.optString("sha256", "");
        if (info.versionCode <= 0 || info.apkUrl.isEmpty()) {
            throw new Exception("version.json 缺少 versionCode 或 apkUrl");
        }
        return info;
    }

    private static String resolve(String base, String apkUrl) throws Exception {
        if (apkUrl.startsWith("http://") || apkUrl.startsWith("https://")) return apkUrl;
        return new URL(new URL(base), apkUrl).toString();
    }

    private static void showUpdateDialog(Activity activity, VersionInfo info, String apkUrl) {
        String message = "当前版本：" + BuildConfig.VERSION_NAME
                + "\n最新版本：" + info.versionName + " (" + info.versionCode + ")";
        if (!info.notes.isEmpty()) message += "\n\n" + info.notes;
        new MaterialAlertDialogBuilder(activity)
                .setTitle("发现新版本")
                .setMessage(message)
                .setPositiveButton("下载并安装", (d, w) -> downloadAndInstall(activity, apkUrl, info.sha256))
                .setNegativeButton("稍后", null)
                .show();
    }

    private static void downloadAndInstall(Activity activity, String apkUrl, String sha256) {
        File dir = activity.getExternalFilesDir("updates");
        if (dir == null) dir = new File(activity.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(activity, "无法创建下载目录", Toast.LENGTH_SHORT).show();
            return;
        }
        File apk = new File(dir, "ridu-update.apk");

        ProgressDialog pd = new ProgressDialog(activity);
        pd.setMessage("正在下载更新…");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();

        File finalDir = dir;
        File finalApk = apk;
        EXECUTOR.execute(() -> {
            try {
                download(apkUrl, finalApk, percent -> activity.runOnUiThread(() -> {
                    if (pd.isShowing()) pd.setProgress(percent);
                }));
                if (!sha256.isEmpty() && !sha256.equalsIgnoreCase(sha256(finalApk))) {
                    throw new Exception("SHA-256 校验失败，文件可能不完整");
                }
                activity.runOnUiThread(() -> {
                    if (pd.isShowing()) pd.dismiss();
                    installApk(activity, finalApk);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (pd.isShowing()) pd.dismiss();
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("下载失败")
                            .setMessage(String.valueOf(e.getMessage()))
                            .setPositiveButton("知道了", null)
                            .show();
                });
            }
        });
    }

    private interface Progress {
        void onPercent(int percent);
    }

    private static void download(String url, File outFile, Progress progress) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "RiduPlan");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        int total = conn.getContentLength();
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192];
            long read = 0;
            int n;
            int last = -1;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                read += n;
                if (total > 0) {
                    int p = (int) (read * 100 / total);
                    if (p != last) {
                        last = p;
                        progress.onPercent(p);
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
        if (outFile.length() <= 0) throw new Exception("下载文件为空");
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    public static void installLocalApk(Activity activity, Uri uri) {
        EXECUTOR.execute(() -> {
            try {
                File out = new File(activity.getCacheDir(), "local-update.apk");
                try (InputStream in = activity.getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    if (in == null) throw new Exception("无法读取 APK");
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                }
                activity.runOnUiThread(() -> installApk(activity, out));
            } catch (Exception e) {
                activity.runOnUiThread(() -> new MaterialAlertDialogBuilder(activity)
                        .setTitle("读取 APK 失败")
                        .setMessage(String.valueOf(e.getMessage()))
                        .setPositiveButton("知道了", null)
                        .show());
            }
        });
    }

    public static void installApk(Activity activity, File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("需要安装权限")
                    .setMessage("请允许“日读”安装未知应用，然后返回再点一次安装。")
                    .setPositiveButton("去设置", (d, w) -> {
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(i);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }
}
