package com.ridu.plan.update;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ridu.plan.BuildConfig;
import com.ridu.plan.data.PlanStore;

import org.json.JSONArray;
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
    private static final int MAX_ATTEMPTS = 4;

    private UpdateChecker() {
    }

    public static class VersionInfo {
        public int versionCode;
        public String versionName = "";
        public String apkUrl = "";
        public String notes = "";
        public String sha256 = "";
        public boolean githubAsset;
        public String token = "";
    }

    public static void checkForUpdate(Activity activity, String url, boolean silent) {
        if (url == null || url.trim().isEmpty()) {
            if (!silent) Toast.makeText(activity, "请先设置应用更新地址", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!silent) Toast.makeText(activity, "正在检查更新…", Toast.LENGTH_SHORT).show();
        final String source = url.trim();
        final boolean manifest = isGithubManifest(source);
        EXECUTOR.execute(() -> {
            try {
                String token = PlanStore.getGithubToken(activity);
                VersionInfo info = manifest
                        ? fetchGithubManifest(source, token, activity.getPackageName())
                        : fetchInfo(source);
                if (!info.githubAsset) info.apkUrl = resolve(source, info.apkUrl);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (info.versionCode > BuildConfig.VERSION_CODE) {
                        showUpdateDialog(activity, info);
                    } else if (!silent) {
                        Toast.makeText(activity, "已是最新版本 " + BuildConfig.VERSION_NAME, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                if (!silent) {
                    String message = String.valueOf(e.getMessage());
                    if (manifest && message.contains("404")) {
                        message += "\n\n私有库需要在“设置 → GitHub 只读 Token”里填入 Token。";
                    }
                    final String shown = message;
                    activity.runOnUiThread(() -> new MaterialAlertDialogBuilder(activity)
                            .setTitle("检查更新失败")
                            .setMessage(shown)
                            .setPositiveButton("知道了", null)
                            .show());
                }
            }
        });
    }

    private static boolean isGithubManifest(String url) {
        return url.contains("api.github.com") && url.contains("/contents/");
    }

    /**
     * 读取 APK 仓库的 manifest.json，按包名找到自己，再定位 Release 附件。
     * 私有库需要只读 Token；公开库可不填。
     */
    private static VersionInfo fetchGithubManifest(String url, String token, String packageName)
            throws Exception {
        String body = httpGet(url, token, "application/vnd.github+json");
        JSONObject root = new JSONObject(body);
        if (!root.has("apps") && root.has("content")) {
            // Contents API 默认返回 base64 包装
            String encoded = root.optString("content", "").replaceAll("\\s", "");
            byte[] raw = Base64.decode(encoded, Base64.DEFAULT);
            root = new JSONObject(new String(raw, StandardCharsets.UTF_8));
        }
        JSONArray apps = root.optJSONArray("apps");
        if (apps == null) throw new Exception("manifest.json 缺少 apps 数组");

        JSONObject app = null;
        for (int i = 0; i < apps.length(); i++) {
            JSONObject item = apps.optJSONObject(i);
            if (item != null && packageName.equals(item.optString("packageName"))) {
                app = item;
                break;
            }
        }
        if (app == null) throw new Exception("仓库 manifest 中没有包名 " + packageName);

        VersionInfo info = new VersionInfo();
        info.versionCode = app.optInt("versionCode", 0);
        info.versionName = app.optString("versionName", "");
        info.sha256 = app.optString("sha256", "");
        info.notes = app.optString("changelog", "");
        info.token = token == null ? "" : token;

        String tag = app.optString("releaseTag", "");
        String file = app.optString("file", "");
        if (info.versionCode <= 0 || tag.isEmpty() || file.isEmpty()) {
            throw new Exception("manifest 条目缺少 versionCode / releaseTag / file");
        }

        int cut = url.indexOf("/contents/");
        String repoApi = url.substring(0, cut);
        JSONObject release = new JSONObject(
                httpGet(repoApi + "/releases/tags/" + tag, token, "application/vnd.github+json"));
        JSONArray assets = release.optJSONArray("assets");
        long assetId = 0;
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset != null && file.equals(asset.optString("name"))) {
                    assetId = asset.optLong("id", 0);
                    break;
                }
            }
        }
        if (assetId <= 0) throw new Exception("Release " + tag + " 中找不到附件 " + file);

        info.apkUrl = repoApi + "/releases/assets/" + assetId;
        info.githubAsset = true;
        return info;
    }

    private static VersionInfo fetchInfo(String url) throws Exception {
        String body = httpGet(url, "", "application/json,text/plain,*/*");
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

    private static String httpGet(String url, String token, String accept) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "RiduPlan/" + BuildConfig.VERSION_NAME);
        if (accept != null) conn.setRequestProperty("Accept", accept);
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            try (InputStream in = conn.getInputStream()) {
                return readAll(in);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String resolve(String base, String apkUrl) throws Exception {
        if (apkUrl.startsWith("http://") || apkUrl.startsWith("https://")) return apkUrl;
        return new URL(new URL(base), apkUrl).toString();
    }

    private static void showUpdateDialog(Activity activity, VersionInfo info) {
        String message = "当前版本：" + BuildConfig.VERSION_NAME
                + "\n最新版本：" + info.versionName + " (" + info.versionCode + ")";
        if (!info.notes.isEmpty()) message += "\n\n" + info.notes;
        new MaterialAlertDialogBuilder(activity)
                .setTitle("发现新版本")
                .setMessage(message)
                .setPositiveButton("下载并安装", (d, w) -> downloadAndInstall(activity, info))
                .setNegativeButton("稍后", null)
                .show();
    }

    private static void downloadAndInstall(Activity activity, VersionInfo info) {
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

        EXECUTOR.execute(() -> {
            try {
                downloadWithRetry(info, apk, percent -> activity.runOnUiThread(() -> {
                    if (pd.isShowing()) pd.setProgress(percent);
                }));
                if (!info.sha256.isEmpty() && !info.sha256.equalsIgnoreCase(sha256(apk))) {
                    throw new Exception("SHA-256 校验失败，文件可能不完整");
                }
                activity.runOnUiThread(() -> {
                    if (pd.isShowing()) pd.dismiss();
                    installApk(activity, apk);
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

    /**
     * 失败自动重试，并利用 Range 从已下载的部分继续，避免网络中断后从头再来。
     */
    private static void downloadWithRetry(VersionInfo info, File out, Progress progress) throws Exception {
        if (out.exists() && !out.delete()) {
            throw new Exception("无法清理旧的下载文件");
        }
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                downloadOnce(info.apkUrl,
                        info.githubAsset ? info.token : "",
                        info.githubAsset ? "application/octet-stream" : null,
                        out, progress);
                return;
            } catch (Exception e) {
                last = e;
                if (out.exists() && out.length() == 0) out.delete();
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new Exception("已重试 " + MAX_ATTEMPTS + " 次："
                + (last == null ? "未知错误" : last.getMessage()), last);
    }

    private static void downloadOnce(String url, String token, String accept, File out, Progress progress)
            throws Exception {
        String currentUrl = url;
        String currentToken = token == null ? "" : token;
        String currentAccept = accept;
        int redirects = 0;
        while (true) {
            long existing = out.exists() ? out.length() : 0;
            HttpURLConnection conn = (HttpURLConnection) new URL(currentUrl).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "RiduPlan/" + BuildConfig.VERSION_NAME);
            if (!currentToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + currentToken);
            }
            if (currentAccept != null && !currentAccept.isEmpty()) {
                conn.setRequestProperty("Accept", currentAccept);
            }
            if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");

            int code;
            try {
                code = conn.getResponseCode();
            } catch (Exception e) {
                conn.disconnect();
                throw e;
            }

            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) throw new Exception("重定向缺少 Location");
                if (++redirects > 5) throw new Exception("重定向次数过多");
                currentUrl = location;
                // 跳转到对象存储后不能再带 Token，否则签名校验会失败
                currentToken = "";
                currentAccept = null;
                continue;
            }
            if (code == 416 && existing > 0) {
                conn.disconnect();
                return;
            }
            if (code != 200 && code != 206) {
                conn.disconnect();
                throw new Exception("HTTP " + code);
            }

            boolean append = code == 206 && existing > 0;
            long contentLength = conn.getContentLengthLong();
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream fos = new FileOutputStream(out, append)) {
                byte[] buf = new byte[8192];
                long read = 0;
                int lastPercent = -1;
                int n;
                while ((n = in.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                    read += n;
                    if (contentLength > 0) {
                        long done = (append ? existing : 0) + read;
                        long all = append ? existing + contentLength : contentLength;
                        int percent = (int) Math.min(100, done * 100 / all);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            progress.onPercent(percent);
                        }
                    }
                }
            } finally {
                conn.disconnect();
            }
            return;
        }
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
