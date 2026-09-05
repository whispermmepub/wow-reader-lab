package com.whisper.wowreader;

import android.os.Build;
import android.text.Html;
import android.net.Uri;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

final class ReaderSearchIndex {
    static final class Hit {
        final int spineIndex;
        final int occurrence;
        final String chapter;
        final String snippet;
        Hit(int spineIndex, int occurrence, String chapter, String snippet) {
            this.spineIndex = spineIndex;
            this.occurrence = occurrence;
            this.chapter = chapter;
            this.snippet = snippet;
        }
    }

    static final class Footnote {
        final int spineIndex;
        final String fragment;
        final String text;
        Footnote(int spineIndex, String fragment, String text) {
            this.spineIndex = spineIndex;
            this.fragment = fragment == null ? "" : fragment;
            this.text = text == null ? "" : text;
        }
    }

    private ReaderSearchIndex() {}

    static List<Hit> search(List<File> spine, List<String> titles, String query, int limit) {
        ArrayList<Hit> out = new ArrayList<>();
        if (spine == null || query == null) return out;
        String q = clean(query);
        if (q.isEmpty()) return out;
        String qLower = q.toLowerCase(Locale.ROOT);
        int max = Math.max(1, limit);
        for (int s = 0; s < spine.size() && out.size() < max; s++) {
            String plain = plainText(readUtf8(spine.get(s)));
            if (plain.isEmpty()) continue;
            String lower = plain.toLowerCase(Locale.ROOT);
            int from = 0;
            int occurrence = 0;
            while (from <= lower.length() - qLower.length() && out.size() < max) {
                int at = lower.indexOf(qLower, from);
                if (at < 0) break;
                int left = Math.max(0, at - 58);
                int right = Math.min(plain.length(), at + q.length() + 92);
                String snippet = plain.substring(left, right).trim();
                if (left > 0) snippet = "…" + snippet;
                if (right < plain.length()) snippet += "…";
                String chapter = null;
                if (titles != null && s < titles.size()) chapter = titles.get(s);
                if (chapter == null || chapter.trim().isEmpty() || isGeneric(chapter)) chapter = "Chapter " + (s + 1);
                out.add(new Hit(s, occurrence, chapter.trim(), snippet));
                occurrence++;
                from = at + Math.max(1, qLower.length());
            }
        }
        return out;
    }

    static int resolveTargetSpine(List<File> spine, int sourceSpine, String href) {
        if (spine == null || spine.isEmpty() || sourceSpine < 0 || sourceSpine >= spine.size()) return -1;
        String raw = href == null ? "" : href.trim();
        int hash = raw.indexOf('#');
        String filePart = hash >= 0 ? raw.substring(0, hash) : raw;
        if (filePart.isEmpty()) return sourceSpine;
        try {
            String decoded = Uri.decode(filePart);
            String lower = decoded.toLowerCase(Locale.ROOT);
            if (lower.startsWith("http://") || lower.startsWith("https://") ||
                    lower.startsWith("mailto:") || lower.startsWith("tel:")) return -1;
            File source = spine.get(sourceSpine);
            File target;
            if (decoded.startsWith("file://")) target = new File(Uri.parse(decoded).getPath());
            else target = new File(source.getParentFile(), decoded);
            String wanted = target.getCanonicalPath();
            for (int i = 0; i < spine.size(); i++) {
                if (wanted.equals(spine.get(i).getCanonicalPath())) return i;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    static Footnote resolveFootnote(List<File> spine, int sourceSpine, String href, String sourceId) {
        if (spine == null || spine.isEmpty() || sourceSpine < 0 || sourceSpine >= spine.size())
            return new Footnote(sourceSpine, "", "");
        String raw = href == null ? "" : href.trim();
        int hash = raw.indexOf('#');
        String filePart = hash >= 0 ? raw.substring(0, hash) : raw;
        String fragment = hash >= 0 && hash + 1 < raw.length() ? Uri.decode(raw.substring(hash + 1)) : "";
        int targetSpine = sourceSpine;
        try {
            if (!filePart.isEmpty()) {
                String decoded = Uri.decode(filePart);
                File source = spine.get(sourceSpine);
                File target;
                if (decoded.startsWith("file://")) target = new File(Uri.parse(decoded).getPath());
                else target = new File(source.getParentFile(), decoded);
                String wanted = target.getCanonicalPath();
                for (int i = 0; i < spine.size(); i++) {
                    if (wanted.equals(spine.get(i).getCanonicalPath())) { targetSpine = i; break; }
                }
            }
        } catch (Exception ignored) {}

        String html = readUtf8(spine.get(Math.max(0, Math.min(spine.size() - 1, targetSpine))));
        String note = extractByFragment(html, fragment, sourceId);
        return new Footnote(targetSpine, fragment, note);
    }

    private static String extractByFragment(String html, String fragment, String sourceId) {
        if (html == null || html.isEmpty() || fragment == null || fragment.isEmpty()) return "";
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setExpandEntityReferences(false);
            try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
            try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
            try { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}
            Document d = f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
            Element target = findElement(d.getDocumentElement(), fragment);
            if (target != null) {
                Element container = chooseContainer(target);
                String text = collectText(container, sourceId);
                if (!text.isEmpty()) return text;
            }
        } catch (Exception ignored) {}

        try {
            Pattern p = Pattern.compile("(?is)(id|name)\\s*=\\s*(['\"])" + Pattern.quote(fragment) + "\\2");
            Matcher m = p.matcher(html);
            if (!m.find()) return "";
            int pos = m.start();
            int start = pos;
            String low = html.toLowerCase(Locale.ROOT);
            String[] starts = {"<aside", "<li", "<p", "<div", "<dd", "<section"};
            for (String tag : starts) {
                int x = low.lastIndexOf(tag, pos);
                if (x >= 0 && pos - x < 1800) start = Math.min(start, x);
            }
            int end = Math.min(html.length(), pos + 5000);
            String[] ends = {"</aside>", "</li>", "</p>", "</dd>", "</section>", "</div>"};
            for (String tag : ends) {
                int x = low.indexOf(tag, pos);
                if (x >= 0) end = Math.min(end, x + tag.length());
            }
            return plainText(html.substring(Math.max(0, start), Math.max(start, end)));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static Element findElement(Element root, String id) {
        if (root == null) return null;
        if (id.equals(root.getAttribute("id")) || id.equals(root.getAttribute("name"))) return root;
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) {
                Element found = findElement((Element) n, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Element chooseContainer(Element target) {
        Element current = target;
        for (int i = 0; i < 4; i++) {
            String tag = current.getTagName() == null ? "" : current.getTagName().toLowerCase(Locale.ROOT);
            if (tag.equals("aside") || tag.equals("li") || tag.equals("p") || tag.equals("dd") ||
                    tag.equals("section") || tag.equals("td") || tag.equals("div")) return current;
            Node p = current.getParentNode();
            if (!(p instanceof Element)) break;
            current = (Element) p;
        }
        return current;
    }

    private static String collectText(Element root, String sourceId) {
        StringBuilder b = new StringBuilder();
        collect(root, sourceId == null ? "" : sourceId, b);
        return clean(b.toString());
    }

    private static void collect(Node node, String sourceId, StringBuilder out) {
        if (node == null) return;
        if (node.getNodeType() == Node.TEXT_NODE) {
            out.append(node.getNodeValue()).append(' ');
            return;
        }
        if (node instanceof Element) {
            Element e = (Element) node;
            String tag = e.getTagName() == null ? "" : e.getTagName().toLowerCase(Locale.ROOT);
            if (tag.equals("script") || tag.equals("style")) return;
            if (tag.equals("a")) {
                String meta = (e.getAttribute("role") + " " + e.getAttribute("rel") + " " + e.getAttribute("epub:type")).toLowerCase(Locale.ROOT);
                String h = e.getAttribute("href");
                if (meta.contains("backlink") || (!sourceId.isEmpty() && h != null && h.endsWith("#" + sourceId))) return;
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) collect(children.item(i), sourceId, out);
    }

    private static String readUtf8(File f) {
        if (f == null || !f.isFile()) return "";
        try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception ignored) { return ""; }
    }

    private static String plainText(String html) {
        if (html == null || html.isEmpty()) return "";
        try {
            CharSequence s = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
                    : Html.fromHtml(html);
            return clean(s == null ? "" : s.toString());
        } catch (Exception ignored) {
            return clean(html.replaceAll("(?is)<script.*?</script>|<style.*?</style>", " ").replaceAll("(?s)<[^>]+>", " "));
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean isGeneric(String value) {
        String low = clean(value).toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        return low.isEmpty() || low.equals("unknown") || low.equals("untitled") || low.equals("null") ||
                low.matches("^(chapter|section|part|page|text|content|item|file)$");
    }
}
