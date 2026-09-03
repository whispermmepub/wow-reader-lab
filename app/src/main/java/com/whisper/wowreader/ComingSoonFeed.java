package com.whisper.wowreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
import android.text.format.Time;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ComingSoonFeed {
    static final class Post {
        final String title;
        final String source;
        final String url;
        final String imageUrl;
        final String contentHtml;
        final String excerpt;
        final String published;
        final long publishedMs;

        Post(String title, String source, String url, String imageUrl,
             String contentHtml, String excerpt, String published, long publishedMs) {
            this.title = title;
            this.source = source;
            this.url = url;
            this.imageUrl = imageUrl;
            this.contentHtml = contentHtml;
            this.excerpt = excerpt;
            this.published = published;
            this.publishedMs = publishedMs;
        }
    }

    private static final class Source {
        final String host;
        final String label;
        Source(String host, String label) { this.host = host; this.label = label; }
    }

    private static final Source[] SOURCES = {
            new Source("whisper1of.blogspot.com", "Whisper of Words"),
            new Source("thetpaingwrites.blogspot.com", "Thet Paing Writes"),
            new Source("youthsbookreflections.blogspot.com", "Youths Book Reflections")
    };

    private static final String PREFS = "wow_coming_soon_cache";
    private static final Pattern IMG_PATTERN = Pattern.compile(
            "<img[^>]+src\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']",
            Pattern.CASE_INSENSITIVE);

    private ComingSoonFeed() {}

    static List<Post> fetchLatest(Context context, int maxPerSource, int totalLimit) {
        List<Post> merged = new ArrayList<>();
        SharedPreferences cache = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (Source source : SOURCES) {
            String json = null;
            try {
                json = download(feedUrl(source.host, maxPerSource));
                if (json != null && !json.trim().isEmpty())
                    cache.edit().putString(cacheKey(source.host), json).apply();
            } catch (Exception ignored) {}
            if (json == null || json.trim().isEmpty())
                json = cache.getString(cacheKey(source.host), null);
            if (json == null || json.trim().isEmpty()) continue;
            try { merged.addAll(parse(json, source)); } catch (Exception ignored) {}
        }
        Collections.sort(merged, (a, b) -> Long.compare(b.publishedMs, a.publishedMs));
        if (totalLimit > 0 && merged.size() > totalLimit)
            return new ArrayList<>(merged.subList(0, totalLimit));
        return merged;
    }

    static Post fetchPost(Context context, String postUrl) {
        if (postUrl == null || postUrl.trim().isEmpty()) return null;
        Source source = sourceForUrl(postUrl);
        if (source == null) return null;
        SharedPreferences cache = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = null;
        try {
            json = download(feedUrl(source.host, 50));
            if (json != null && !json.trim().isEmpty())
                cache.edit().putString(cacheKey(source.host), json).apply();
        } catch (Exception ignored) {}
        if (json == null || json.trim().isEmpty()) json = cache.getString(cacheKey(source.host), null);
        if (json == null) return null;
        try {
            for (Post post : parse(json, source)) {
                if (sameUrl(post.url, postUrl)) return post;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String feedUrl(String host, int max) {
        int safeMax = Math.max(1, Math.min(50, max));
        return "https://" + host + "/feeds/posts/default?alt=json&max-results=" + safeMax;
    }

    private static String cacheKey(String host) { return "feed_" + host; }

    private static Source sourceForUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        for (Source s : SOURCES) if (lower.contains(s.host)) return s;
        return null;
    }

    private static boolean sameUrl(String a, String b) {
        if (a == null || b == null) return false;
        String aa = a.endsWith("/") ? a.substring(0, a.length() - 1) : a;
        String bb = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
        return aa.equalsIgnoreCase(bb);
    }

    private static String download(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(12000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
        connection.setRequestProperty("User-Agent", "WoWReader/2.16 Android");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + code);
        }
        StringBuilder out = new StringBuilder();
        try (InputStream in = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int n;
            while ((n = reader.read(buffer)) >= 0) out.append(buffer, 0, n);
        } finally {
            connection.disconnect();
        }
        return out.toString();
    }

    private static List<Post> parse(String json, Source source) throws Exception {
        List<Post> posts = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONObject feed = root.optJSONObject("feed");
        if (feed == null) return posts;
        JSONArray entries = feed.optJSONArray("entry");
        if (entries == null) return posts;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject e = entries.optJSONObject(i);
            if (e == null) continue;
            String title = nestedText(e, "title");
            String published = nestedText(e, "published");
            String content = nestedText(e, "content");
            if (content.isEmpty()) content = nestedText(e, "summary");
            String url = alternateLink(e.optJSONArray("link"));
            if (url.isEmpty()) continue;
            String image = thumbnail(e);
            if (image.isEmpty()) image = firstImage(content);
            image = improveBloggerImage(image);
            String excerpt = plainExcerpt(content, 190);
            posts.add(new Post(
                    title.isEmpty() ? "Untitled" : title,
                    source.label,
                    url,
                    image,
                    content,
                    excerpt,
                    displayDate(published),
                    parseDateMs(published)));
        }
        return posts;
    }

    private static String nestedText(JSONObject object, String key) {
        JSONObject node = object.optJSONObject(key);
        return node == null ? "" : node.optString("$t", "").trim();
    }

    private static String alternateLink(JSONArray links) {
        if (links == null) return "";
        String fallback = "";
        for (int i = 0; i < links.length(); i++) {
            JSONObject link = links.optJSONObject(i);
            if (link == null) continue;
            String href = link.optString("href", "");
            String rel = link.optString("rel", "");
            if (fallback.isEmpty() && href.startsWith("http")) fallback = href;
            if ("alternate".equals(rel) && href.startsWith("http")) return href;
        }
        return fallback;
    }

    private static String thumbnail(JSONObject entry) {
        JSONObject media = entry.optJSONObject("media$thumbnail");
        return media == null ? "" : media.optString("url", "").trim();
    }

    private static String firstImage(String html) {
        if (html == null || html.isEmpty()) return "";
        Matcher matcher = IMG_PATTERN.matcher(html);
        return matcher.find() ? matcher.group(1).replace("&amp;", "&") : "";
    }

    private static String improveBloggerImage(String url) {
        if (url == null) return "";
        String out = url.trim();
        out = out.replace("/s72-c/", "/s600/")
                 .replace("/s72-c-k-c0x00ffffff-no-rj/", "/s600/")
                 .replace("=s72-c", "=s600")
                 .replace("=s72", "=s600");
        if (out.startsWith("//")) out = "https:" + out;
        return out;
    }

    static CharSequence richText(String html) {
        if (html == null) return "";
        if (android.os.Build.VERSION.SDK_INT >= 24)
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        return Html.fromHtml(html);
    }

    private static String plainExcerpt(String html, int limit) {
        String plain = richText(html).toString().replace('\u00A0', ' ')
                .replaceAll("\\s+", " ").trim();
        if (plain.length() <= limit) return plain;
        return plain.substring(0, Math.max(1, limit - 1)).trim() + "…";
    }

    private static long parseDateMs(String value) {
        if (value == null || value.isEmpty()) return 0L;
        try {
            Time time = new Time();
            time.parse3339(value);
            return time.toMillis(false);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String displayDate(String value) {
        if (value == null || value.length() < 10) return "";
        String d = value.substring(0, 10);
        String[] p = d.split("-");
        if (p.length != 3) return d;
        return p[2] + "." + p[1] + "." + p[0];
    }
}
