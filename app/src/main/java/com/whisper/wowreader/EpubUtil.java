package com.whisper.wowreader;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

final class EpubUtil {
    private EpubUtil() {}

    static final class Summary {
        String title;
        String author;
        File cover;
    }

    static final class BookInfo {
        String title;
        String author;
        final List<File> spine = new ArrayList<>();
        final List<String> chapterTitles = new ArrayList<>();
        final List<Integer> tocSpineIndices = new ArrayList<>();
        final List<String> tocTitles = new ArrayList<>();
        final List<String> tocFragments = new ArrayList<>();
    }

    private static final class ManifestItem {
        String id;
        String href;
        String mediaType;
        String properties;
    }

    static Summary extractSummary(File epub, File coverDir) throws Exception {
        Summary result = new Summary();
        if (!coverDir.exists()) coverDir.mkdirs();
        try (ZipFile zip = new ZipFile(epub)) {
            ZipEntry containerEntry = zip.getEntry("META-INF/container.xml");
            if (containerEntry == null) throw new Exception("Invalid EPUB: container.xml missing");
            Document container;
            try (InputStream in = zip.getInputStream(containerEntry)) {
                container = parseXml(in);
            }
            String opfPath = rootfilePath(container);
            ZipEntry opfEntry = zip.getEntry(opfPath);
            if (opfEntry == null) throw new Exception("Invalid EPUB package");
            Document opfDoc;
            try (InputStream in = zip.getInputStream(opfEntry)) {
                opfDoc = parseXml(in);
            }

            result.title = clean(firstText(opfDoc, "title"));
            if (isGenericTitle(result.title)) result.title = null;
            result.author = clean(firstText(opfDoc, "creator"));

            LinkedHashMap<String, ManifestItem> items = readManifest(opfDoc);
            String coverHref = findCoverHref(opfDoc, items);
            String opfDir = parentPath(opfPath);

            if (coverHref == null) coverHref = findGuideCover(zip, opfDoc, opfDir);
            if (coverHref != null) {
                String coverPath = resolveZipPath(opfDir, coverHref);
                ZipEntry coverEntry = zip.getEntry(coverPath);
                if (coverEntry != null && !coverEntry.isDirectory()) {
                    String ext = imageExtension(coverPath);
                    String cacheKey = Integer.toHexString((epub.getAbsolutePath() + ":" + epub.length() + ":" + epub.lastModified()).hashCode());
                    File out = new File(coverDir, cacheKey + ext);
                    if (!out.isFile() || out.length() == 0) {
                        try (InputStream in = zip.getInputStream(coverEntry); FileOutputStream fos = new FileOutputStream(out)) {
                            byte[] buffer = new byte[32 * 1024];
                            int n;
                            while ((n = in.read(buffer)) > 0) fos.write(buffer, 0, n);
                        }
                    }
                    result.cover = out;
                }
            }
        }
        return result;
    }

    static BookInfo parseExtracted(File root) throws Exception {
        File containerFile = new File(root, "META-INF/container.xml");
        Document container = parseXml(containerFile);
        String opfPath = rootfilePath(container);
        File opf = new File(root, opfPath);
        Document opfDoc = parseXml(opf);
        File opfDir = opf.getParentFile();

        BookInfo info = new BookInfo();
        info.title = clean(firstText(opfDoc, "title"));
        if (isGenericTitle(info.title)) info.title = null;
        info.author = clean(firstText(opfDoc, "creator"));

        LinkedHashMap<String, ManifestItem> items = readManifest(opfDoc);
        Map<String, Integer> spineIndex = new HashMap<>();
        NodeList refs = elements(opfDoc, "itemref");
        for (int i = 0; i < refs.getLength(); i++) {
            Element ref = (Element) refs.item(i);
            if ("no".equalsIgnoreCase(ref.getAttribute("linear"))) continue;
            ManifestItem item = items.get(ref.getAttribute("idref"));
            if (item == null || item.href == null || item.href.isEmpty()) continue;
            if (hasWord(item.properties, "nav")) continue;
            File chapter = resolveFile(opfDir, item.href);
            if (!chapter.isFile() || !hasReadableContent(chapter)) continue;
            int index = info.spine.size();
            info.spine.add(chapter);
            info.chapterTitles.add(guessChapterTitle(chapter, index + 1));
            spineIndex.put(canonical(chapter), index);
        }

        ManifestItem navItem = null;
        ManifestItem ncxItem = null;
        for (ManifestItem item : items.values()) {
            if (hasWord(item.properties, "nav")) navItem = item;
            if (item.mediaType != null && item.mediaType.toLowerCase(Locale.ROOT).contains("ncx")) ncxItem = item;
        }
        Element spineElement = firstElement(opfDoc, "spine");
        if (spineElement != null) {
            String tocId = spineElement.getAttribute("toc");
            if (!tocId.isEmpty() && items.containsKey(tocId)) ncxItem = items.get(tocId);
        }
        boolean tocFound = false;
        if (navItem != null) tocFound = applyNavTitles(resolveFile(opfDir, navItem.href), spineIndex, info.chapterTitles);
        if (!tocFound && ncxItem != null) applyNcxTitles(resolveFile(opfDir, ncxItem.href), spineIndex, info.chapterTitles);

        boolean exactToc = false;
        if (navItem != null) exactToc = collectNavEntries(resolveFile(opfDir, navItem.href), spineIndex, info);
        if (!exactToc && ncxItem != null) exactToc = collectNcxEntries(resolveFile(opfDir, ncxItem.href), spineIndex, info);
        if (!exactToc) collectStrictHeadingFallback(info);

        return info;
    }

    private static LinkedHashMap<String, ManifestItem> readManifest(Document doc) {
        LinkedHashMap<String, ManifestItem> items = new LinkedHashMap<>();
        NodeList nodes = elements(doc, "item");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            ManifestItem item = new ManifestItem();
            item.id = e.getAttribute("id");
            item.href = e.getAttribute("href");
            item.mediaType = e.getAttribute("media-type");
            item.properties = e.getAttribute("properties");
            if (!item.id.isEmpty()) items.put(item.id, item);
        }
        return items;
    }

    private static String findCoverHref(Document doc, Map<String, ManifestItem> items) {
        for (ManifestItem item : items.values()) if (hasWord(item.properties, "cover-image") && isImage(item)) return item.href;
        String coverId = null;
        NodeList metas = elements(doc, "meta");
        for (int i = 0; i < metas.getLength(); i++) {
            Element e = (Element) metas.item(i);
            if ("cover".equalsIgnoreCase(e.getAttribute("name"))) { coverId = e.getAttribute("content"); break; }
        }
        if (coverId != null) {
            ManifestItem cover = items.get(coverId);
            if (cover != null && isImage(cover)) return cover.href;
        }
        for (ManifestItem item : items.values()) {
            String id = item.id == null ? "" : item.id.toLowerCase(Locale.ROOT);
            String href = item.href == null ? "" : item.href.toLowerCase(Locale.ROOT);
            if (isImage(item) && (id.contains("cover") || href.contains("cover"))) return item.href;
        }
        return null;
    }

    private static String findGuideCover(ZipFile zip, Document doc, String opfDir) {
        try {
            NodeList refs = elements(doc, "reference");
            for (int i = 0; i < refs.getLength(); i++) {
                Element e = (Element) refs.item(i);
                if (!e.getAttribute("type").toLowerCase(Locale.ROOT).contains("cover")) continue;
                String pageHref = e.getAttribute("href");
                String pagePath = resolveZipPath(opfDir, pageHref);
                ZipEntry pageEntry = zip.getEntry(pagePath);
                if (pageEntry == null) continue;
                Document page;
                try (InputStream in = zip.getInputStream(pageEntry)) { page = parseXml(in); }
                NodeList imgs = elements(page, "img");
                if (imgs.getLength() > 0) {
                    String src = ((Element) imgs.item(0)).getAttribute("src");
                    String absolute = resolveZipPath(parentPath(pagePath), src);
                    return opfDir.isEmpty() ? absolute : absolute.substring(Math.min(absolute.length(), opfDir.length() + 1));
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean applyNavTitles(File navFile, Map<String, Integer> spineIndex, List<String> titles) {
        if (navFile == null || !navFile.isFile()) return false;
        boolean applied = false;
        try {
            Document doc = parseXml(navFile);
            Element targetNav = null;
            NodeList navs = elements(doc, "nav");
            for (int i = 0; i < navs.getLength(); i++) {
                Element nav = (Element) navs.item(i);
                String type = nav.getAttribute("epub:type");
                if (type.isEmpty()) type = nav.getAttributeNS("http://www.idpf.org/2007/ops", "type");
                if (type.toLowerCase(Locale.ROOT).contains("toc")) { targetNav = nav; break; }
            }
            if (targetNav == null && navs.getLength() > 0) targetNav = (Element) navs.item(0);
            if (targetNav == null) return false;
            NodeList anchors = targetNav.getElementsByTagNameNS("*", "a");
            if (anchors.getLength() == 0) anchors = targetNav.getElementsByTagName("a");
            for (int i = 0; i < anchors.getLength(); i++) {
                Element a = (Element) anchors.item(i);
                String href = a.getAttribute("href");
                String label = clean(a.getTextContent());
                if (href.isEmpty() || label == null || label.isEmpty()) continue;
                Integer idx = spineIndex.get(canonical(resolveFile(navFile.getParentFile(), href)));
                if (idx != null && idx >= 0 && idx < titles.size() && !isGenericTitle(label)) { titles.set(idx, label); applied = true; }
            }
        } catch (Exception ignored) {}
        return applied;
    }

    private static boolean applyNcxTitles(File ncxFile, Map<String, Integer> spineIndex, List<String> titles) {
        if (ncxFile == null || !ncxFile.isFile()) return false;
        boolean applied = false;
        try {
            Document doc = parseXml(ncxFile);
            NodeList points = elements(doc, "navPoint");
            for (int i = 0; i < points.getLength(); i++) {
                Element point = (Element) points.item(i);
                NodeList contents = point.getElementsByTagNameNS("*", "content");
                if (contents.getLength() == 0) contents = point.getElementsByTagName("content");
                NodeList texts = point.getElementsByTagNameNS("*", "text");
                if (texts.getLength() == 0) texts = point.getElementsByTagName("text");
                if (contents.getLength() == 0 || texts.getLength() == 0) continue;
                String href = ((Element) contents.item(0)).getAttribute("src");
                String label = clean(texts.item(0).getTextContent());
                Integer idx = spineIndex.get(canonical(resolveFile(ncxFile.getParentFile(), href)));
                if (idx != null && label != null && !label.isEmpty() && !isGenericTitle(label)) { titles.set(idx, label); applied = true; }
            }
        } catch (Exception ignored) {}
        return applied;
    }

    private static String guessChapterTitle(File chapter, int number) {
        try {
            Document doc = parseXml(chapter);

            String title = clean(firstText(doc, "title"));
            if (isMeaningfulChapterTitle(title)) return title;

            for (String tag : new String[]{"h1", "h2", "h3", "h4"}) {
                NodeList nodes = elements(doc, tag);
                for (int i = 0; i < nodes.getLength(); i++) {
                    String h = clean(nodes.item(i).getTextContent());
                    if (isMeaningfulChapterTitle(h) && h.length() <= 160) return h;
                }
            }

            // Some EPUBs put a chapter heading in an ordinary element whose
            // class/id contains title/chapter/heading rather than using h1-h4.
            NodeList all = doc.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                if (!(all.item(i) instanceof Element)) continue;
                Element e = (Element) all.item(i);
                String hint = (e.getAttribute("class") + " " + e.getAttribute("id")).toLowerCase(Locale.ROOT);
                if (!(hint.contains("title") || hint.contains("chapter") || hint.contains("heading"))) continue;
                String value = clean(e.getTextContent());
                if (isMeaningfulChapterTitle(value) && value.length() <= 160) return value;
            }

            // Last body-text fallback: only use short text, never a full
            // paragraph. This removes generic labels while keeping the TOC readable.
            NodeList paragraphs = elements(doc, "p");
            for (int i = 0; i < paragraphs.getLength(); i++) {
                String p = clean(paragraphs.item(i).getTextContent());
                if (isMeaningfulChapterTitle(p) && p.length() >= 2 && p.length() <= 84) return p;
            }
        } catch (Exception ignored) {}

        String fileTitle = titleFromFilename(chapter == null ? null : chapter.getName());
        if (fileTitle != null) return fileTitle;
        return "Chapter " + number;
    }

    private static boolean isMeaningfulChapterTitle(String value) {
        if (value == null) return false;
        String v = clean(value);
        if (v == null || v.length() < 1 || isGenericTitle(v)) return false;
        String compact = v.replaceAll("[\\p{Punct}\\s]+", "");
        return !compact.isEmpty();
    }

    private static boolean isGenericTitle(String value) {
        if (value == null) return true;
        String v = clean(value);
        if (v == null || v.isEmpty()) return true;
        String low = v.toLowerCase(Locale.ROOT).replaceAll("[._-]+", " ").replaceAll("\\s+", " ").trim();
        if (low.equals("unknown") || low.equals("untitled") || low.equals("undefined") ||
                low.equals("null") || low.equals("none") || low.equals("n a") ||
                low.equals("no title") || low.equals("no name") || low.equals("title")) return true;
        return low.matches("^(chapter|section|part|page|text|content|item|file)\\s*\\d*$");
    }

    private static String titleFromFilename(String name) {
        if (name == null || name.isEmpty()) return null;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String cleaned = clean(base.replace('_', ' ').replace('-', ' '));
        if (!isMeaningfulChapterTitle(cleaned)) return null;
        String low = cleaned.toLowerCase(Locale.ROOT);
        if (low.matches("^(chapter|section|part|page|text|content|item|file)\\s*\\d+$")) return null;
        if (cleaned.matches("^\\d+$")) return null;
        return cleaned;
    }

    private static boolean collectNavEntries(File navFile, Map<String, Integer> spineIndex, BookInfo info) {
        if (navFile == null || !navFile.isFile()) return false;
        boolean added = false;
        try {
            Document doc = parseXml(navFile);
            Element targetNav = null;
            NodeList navs = elements(doc, "nav");
            for (int i = 0; i < navs.getLength(); i++) {
                Element nav = (Element) navs.item(i);
                String type = nav.getAttribute("epub:type");
                if (type.isEmpty()) type = nav.getAttributeNS("http://www.idpf.org/2007/ops", "type");
                if (type.toLowerCase(Locale.ROOT).contains("toc")) { targetNav = nav; break; }
            }
            if (targetNav == null) return false;

            NodeList anchors = targetNav.getElementsByTagNameNS("*", "a");
            if (anchors.getLength() == 0) anchors = targetNav.getElementsByTagName("a");
            for (int i = 0; i < anchors.getLength(); i++) {
                Element a = (Element) anchors.item(i);
                String href = a.getAttribute("href");
                String label = clean(a.getTextContent());
                if (href.isEmpty() || !isMeaningfulChapterTitle(label) || isAuxiliaryTocLabel(label)) continue;
                Integer idx = spineIndex.get(canonical(resolveFile(navFile.getParentFile(), href)));
                if (idx == null || idx < 0 || idx >= info.spine.size()) continue;
                String fragment = fragmentFromHref(href);
                if (addTocEntry(info, idx, label, fragment)) added = true;
            }
        } catch (Exception ignored) {}
        return added;
    }

    private static boolean collectNcxEntries(File ncxFile, Map<String, Integer> spineIndex, BookInfo info) {
        if (ncxFile == null || !ncxFile.isFile()) return false;
        boolean added = false;
        try {
            Document doc = parseXml(ncxFile);
            NodeList points = elements(doc, "navPoint");
            for (int i = 0; i < points.getLength(); i++) {
                Element point = (Element) points.item(i);
                NodeList contents = point.getElementsByTagNameNS("*", "content");
                if (contents.getLength() == 0) contents = point.getElementsByTagName("content");
                NodeList texts = point.getElementsByTagNameNS("*", "text");
                if (texts.getLength() == 0) texts = point.getElementsByTagName("text");
                if (contents.getLength() == 0 || texts.getLength() == 0) continue;
                String href = ((Element) contents.item(0)).getAttribute("src");
                String label = clean(texts.item(0).getTextContent());
                if (!isMeaningfulChapterTitle(label) || isAuxiliaryTocLabel(label)) continue;
                Integer idx = spineIndex.get(canonical(resolveFile(ncxFile.getParentFile(), href)));
                if (idx == null || idx < 0 || idx >= info.spine.size()) continue;
                if (addTocEntry(info, idx, label, fragmentFromHref(href))) added = true;
            }
        } catch (Exception ignored) {}
        return added;
    }

    private static boolean addTocEntry(BookInfo info, int spineIndex, String title, String fragment) {
        String cleanTitle = clean(title);
        if (!isMeaningfulChapterTitle(cleanTitle) || isAuxiliaryTocLabel(cleanTitle)) return false;
        String cleanFragment = fragment == null ? "" : fragment.trim();
        for (int i = 0; i < info.tocTitles.size(); i++) {
            if (info.tocSpineIndices.get(i) == spineIndex &&
                    info.tocTitles.get(i).equals(cleanTitle) &&
                    info.tocFragments.get(i).equals(cleanFragment)) return false;
        }
        info.tocSpineIndices.add(spineIndex);
        info.tocTitles.add(cleanTitle);
        info.tocFragments.add(cleanFragment);
        return true;
    }

    private static void collectStrictHeadingFallback(BookInfo info) {
        for (int i = 0; i < info.spine.size(); i++) {
            String title = strictChapterHeading(info.spine.get(i));
            if (title != null && !isAuxiliaryTocLabel(title)) addTocEntry(info, i, title, "");
        }
        if (info.tocTitles.isEmpty() && !info.spine.isEmpty()) {
            addTocEntry(info, 0, "Start", "");
        }
    }

    private static String strictChapterHeading(File chapter) {
        try {
            Document doc = parseXml(chapter);
            for (String tag : new String[]{"h1", "h2", "h3", "h4"}) {
                NodeList nodes = elements(doc, tag);
                for (int i = 0; i < nodes.getLength(); i++) {
                    String value = clean(nodes.item(i).getTextContent());
                    if (isMeaningfulChapterTitle(value) && value.length() <= 180) return value;
                }
            }
            NodeList all = doc.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                if (!(all.item(i) instanceof Element)) continue;
                Element e = (Element) all.item(i);
                String hint = (e.getAttribute("class") + " " + e.getAttribute("id")).toLowerCase(Locale.ROOT);
                if (!(hint.contains("chapter") || hint.contains("heading") || hint.contains("chapter-title"))) continue;
                String value = clean(e.getTextContent());
                if (isMeaningfulChapterTitle(value) && value.length() <= 180) return value;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean hasReadableContent(File chapter) {
        try {
            Document doc = parseXml(chapter);
            Element body = firstElement(doc, "body");
            if (body != null) {
                String text = clean(body.getTextContent());
                if (text != null && !text.replaceAll("[\\p{Punct}\\s]+", "").isEmpty()) return true;
            }
            for (String tag : new String[]{"img", "image", "svg", "math", "table", "video", "audio", "object", "embed", "canvas"}) {
                if (elements(doc, tag).getLength() > 0) return true;
            }
            return false;
        } catch (Exception ignored) {
            // Broken-but-renderable HTML should be kept rather than silently lost.
            return true;
        }
    }

    private static boolean isAuxiliaryTocLabel(String value) {
        if (value == null) return true;
        String low = clean(value).toLowerCase(Locale.ROOT)
                .replaceAll("[._-]+", " ").replaceAll("\\s+", " ").trim();
        return low.equals("cover") || low.equals("cover page") || low.equals("title page") ||
                low.equals("copyright") || low.equals("copyright page") || low.equals("imprint") ||
                low.equals("about us") || low.equals("publisher") || low.equals("publishers") ||
                low.equals("contents") || low.equals("table of contents") || low.equals("toc");
    }

    private static String fragmentFromHref(String href) {
        if (href == null) return "";
        int hash = href.indexOf('#');
        if (hash < 0 || hash + 1 >= href.length()) return "";
        String fragment = href.substring(hash + 1);
        try { fragment = URLDecoder.decode(fragment, StandardCharsets.UTF_8.name()); } catch (Exception ignored) {}
        return fragment.trim();
    }

    private static File resolveFile(File base, String href) { return new File(base, decodedPath(href)); }

    private static String resolveZipPath(String base, String href) {
        String clean = decodedPath(href);
        String joined = base == null || base.isEmpty() ? clean : base + "/" + clean;
        String[] parts = joined.replace('\\', '/').split("/");
        ArrayList<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) { if (!out.isEmpty()) out.remove(out.size() - 1); }
            else out.add(part);
        }
        return String.join("/", out);
    }

    private static String decodedPath(String href) {
        if (href == null) return "";
        String clean = href.split("#", 2)[0].split("\\?", 2)[0];
        try { clean = URLDecoder.decode(clean, StandardCharsets.UTF_8.name()); } catch (Exception ignored) {}
        return clean;
    }

    private static String parentPath(String path) { int slash = path == null ? -1 : path.lastIndexOf('/'); return slash < 0 ? "" : path.substring(0, slash); }

    private static String rootfilePath(Document container) throws Exception {
        NodeList roots = elements(container, "rootfile");
        if (roots.getLength() == 0) throw new Exception("EPUB package path missing");
        return ((Element) roots.item(0)).getAttribute("full-path");
    }

    private static boolean isImage(ManifestItem item) {
        if (item == null) return false;
        String media = item.mediaType == null ? "" : item.mediaType.toLowerCase(Locale.ROOT);
        String href = item.href == null ? "" : item.href.toLowerCase(Locale.ROOT);
        return media.startsWith("image/") || href.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|svg)$");
    }

    private static String imageExtension(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        for (String ext : new String[]{".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"}) if (lower.endsWith(ext)) return ext;
        return ".img";
    }

    private static boolean hasWord(String words, String target) {
        if (words == null) return false;
        for (String word : words.trim().split("\\s+")) if (target.equalsIgnoreCase(word)) return true;
        return false;
    }

    private static String canonical(File f) { try { return f.getCanonicalPath(); } catch (Exception e) { return f.getAbsolutePath(); } }

    private static Document parseXml(File file) throws Exception { try (InputStream in = new FileInputStream(file)) { return parseXml(in); } }

    private static Document parseXml(InputStream in) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
        try { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(in);
    }

    private static NodeList elements(Document doc, String localName) {
        NodeList list = doc.getElementsByTagNameNS("*", localName);
        if (list.getLength() == 0) list = doc.getElementsByTagName(localName);
        return list;
    }

    private static Element firstElement(Document doc, String localName) { NodeList list = elements(doc, localName); return list.getLength() == 0 ? null : (Element) list.item(0); }
    private static String firstText(Document doc, String localName) { NodeList list = elements(doc, localName); return list.getLength() == 0 ? null : list.item(0).getTextContent(); }
    private static String clean(String s) { if (s == null) return null; return s.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim(); }
}
