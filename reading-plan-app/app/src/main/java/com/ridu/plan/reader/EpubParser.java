package com.ridu.plan.reader;

import android.net.Uri;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class EpubParser {

    public static class Info {
        public String title = "";
        public String author = "";
        public int chapters;
    }

    private static class Opf {
        String title = "";
        String author = "";
        String dir = "";
        List<String> chapterPaths = new ArrayList<>();
    }

    private EpubParser() {
    }

    public static Info inspect(File epub) {
        Info info = new Info();
        try (ZipFile zip = new ZipFile(epub)) {
            Opf opf = parseOpf(zip);
            if (opf != null) {
                info.title = opf.title;
                info.author = opf.author;
                info.chapters = opf.chapterPaths.size();
            }
        } catch (Exception ignored) {
        }
        return info;
    }

    public static List<File> unpack(File epub, File dir) throws Exception {
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("无法创建解压目录");
        String root = dir.getCanonicalPath() + File.separator;

        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(epub))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                File out = new File(dir, entry.getName());
                if (!out.getCanonicalPath().startsWith(root)) continue;
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) fos.write(buf, 0, n);
                }
            }
        }

        List<File> chapters = new ArrayList<>();
        try (ZipFile zip = new ZipFile(epub)) {
            Opf opf = parseOpf(zip);
            if (opf != null) {
                for (String path : opf.chapterPaths) {
                    File f = new File(dir, path);
                    if (f.isFile()) chapters.add(f);
                }
            }
        }
        return chapters;
    }

    private static Opf parseOpf(ZipFile zip) throws Exception {
        String container = readEntry(zip, "META-INF/container.xml");
        if (container == null) return null;
        String rootPath = findRootPath(container);
        if (rootPath == null) return null;

        String opfXml = readEntry(zip, rootPath);
        if (opfXml == null) return null;

        Opf opf = new Opf();
        int slash = rootPath.lastIndexOf('/');
        opf.dir = slash >= 0 ? rootPath.substring(0, slash + 1) : "";

        Map<String, String> manifest = new HashMap<>();
        List<String> spine = new ArrayList<>();

        XmlPullParser p = Xml.newPullParser();
        try {
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        } catch (Exception ignored) {
        }
        p.setInput(new StringReader(opfXml));

        int event;
        while ((event = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (event != XmlPullParser.START_TAG) continue;
            String name = p.getName();
            if ("title".equals(name) && opf.title.isEmpty()) {
                opf.title = safeText(p.nextText());
            } else if ("creator".equals(name) && opf.author.isEmpty()) {
                opf.author = safeText(p.nextText());
            } else if ("item".equals(name)) {
                String id = p.getAttributeValue(null, "id");
                String href = p.getAttributeValue(null, "href");
                if (id != null && href != null) manifest.put(id, href);
            } else if ("itemref".equals(name)) {
                String idref = p.getAttributeValue(null, "idref");
                if (idref != null) spine.add(idref);
            }
        }

        for (String idref : spine) {
            String href = manifest.get(idref);
            if (href == null) continue;
            String path = resolve(opf.dir, href);
            String lower = path.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".xhtml") || lower.endsWith(".htm")) {
                opf.chapterPaths.add(path);
            }
        }

        if (opf.chapterPaths.isEmpty()) {
            for (String href : manifest.values()) {
                String lower = href.toLowerCase();
                if (lower.endsWith(".html") || lower.endsWith(".xhtml") || lower.endsWith(".htm")) {
                    opf.chapterPaths.add(resolve(opf.dir, href));
                }
            }
        }
        return opf;
    }

    private static String findRootPath(String xml) {
        int i = xml.indexOf("rootfile");
        while (i >= 0) {
            int end = xml.indexOf('>', i);
            if (end < 0) break;
            String tag = xml.substring(i, end);
            int fp = tag.indexOf("full-path=");
            if (fp >= 0) {
                char quote = fp + 10 < tag.length() ? tag.charAt(fp + 10) : '"';
                int start = fp + 11;
                int close = tag.indexOf(quote, start);
                if (close > start) return tag.substring(start, close);
            }
            i = xml.indexOf("rootfile", end);
        }
        return null;
    }

    private static String resolve(String dir, String href) {
        String decoded = Uri.decode(href);
        int hash = decoded.indexOf('#');
        if (hash >= 0) decoded = decoded.substring(0, hash);
        return normalize(dir + decoded);
    }

    public static String normalize(String path) {
        String[] parts = path.replace('\\', '/').split("/");
        Deque<String> stack = new ArrayDeque<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!stack.isEmpty()) stack.removeLast();
            } else {
                stack.addLast(part);
            }
        }
        return String.join("/", stack);
    }

    private static String readEntry(ZipFile zip, String name) {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            entry = zip.getEntry(name.replace('\\', '/'));
        }
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeText(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}
