from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def write(path, content):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise SystemExit(f"Expected snippet not found in {path}: {old[:120]!r}")
    if text.count(old) != 1:
        raise SystemExit(f"Expected one match in {path}, found {text.count(old)}")
    write(path, text.replace(old, new, 1))


# ---------- Version + dependencies ----------
build = read("app/build.gradle")
build = build.replace("versionCode 41", "versionCode 42")
build = build.replace("versionName '2.17.1'", "versionName '2.17.2'")
build = build.replace(
    "compileOptions {\n        sourceCompatibility JavaVersion.VERSION_17\n        targetCompatibility JavaVersion.VERSION_17\n    }",
    "compileOptions {\n        coreLibraryDesugaringEnabled true\n        sourceCompatibility JavaVersion.VERSION_17\n        targetCompatibility JavaVersion.VERSION_17\n    }",
)
build = build.replace(
    "dependencies {\n    implementation 'androidx.recyclerview:recyclerview:1.3.2'",
    "dependencies {\n    coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.1.5'\n    implementation 'com.github.chanmratekoko:myanmar-calendar:1.1.1.RELEASE'\n    implementation 'androidx.recyclerview:recyclerview:1.3.2'",
)
write("app/build.gradle", build)

# ---------- Manifest ----------
replace_once(
    "app/src/main/AndroidManifest.xml",
    "        <activity\n            android:name=\".ComingSoonDetailActivity\"\n            android:exported=\"false\" />",
    "        <activity\n            android:name=\".ReadingMemoryActivity\"\n            android:exported=\"false\" />\n\n"
    "        <activity\n            android:name=\".ReadingDayActivity\"\n            android:exported=\"false\" />\n\n"
    "        <activity\n            android:name=\".ReadingCalendarActivity\"\n            android:exported=\"false\" />\n\n"
    "        <activity\n            android:name=\".ComingSoonDetailActivity\"\n            android:exported=\"false\" />",
)

# ---------- Reading statistics: keep old data, add per-day books + memories ----------
write("app/src/main/java/com/whisper/wowreader/ReadingStatsStore.java", r'''package com.whisper.wowreader;

import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local-first reading statistics plus calendar/memory data. */
public final class ReadingStatsStore {
    private ReadingStatsStore() {}

    private static final String KEY_TOTAL_MS = "reading_stats_total_ms";
    private static final String KEY_DAYS = "reading_stats_days_json";
    private static final String KEY_BOOKS = "reading_stats_books_json";
    private static final String KEY_DAY_BOOKS = "reading_stats_day_books_json";
    private static final String KEY_DAY_NOTES = "reading_stats_day_notes_json";
    private static final String KEY_BOOK_DAY_NOTES = "reading_stats_book_day_notes_json";
    private static final String KEY_CURRENT_STREAK = "reading_stats_current_streak";
    private static final String KEY_LONGEST_STREAK = "reading_stats_longest_streak";
    private static final String KEY_LAST_DAY = "reading_stats_last_day";

    private static final long MIN_SESSION_MS = 5_000L;
    private static final long MAX_SESSION_MS = 6L * 60L * 60L * 1000L;

    public static final class Snapshot {
        public long todayMs;
        public long totalMs;
        public long bookMs;
        public int currentStreak;
        public int longestStreak;
        public int activeDays;
    }

    public static final class DayBook {
        public final String fileName;
        public final long durationMs;
        DayBook(String fileName, long durationMs) {
            this.fileName = fileName;
            this.durationMs = durationMs;
        }
    }

    public static long beginSession() {
        return SystemClock.elapsedRealtime();
    }

    public static void finishSession(SharedPreferences prefs, String bookName, long startedElapsedMs) {
        if (prefs == null || startedElapsedMs <= 0L) return;
        long duration = SystemClock.elapsedRealtime() - startedElapsedMs;
        if (duration < MIN_SESSION_MS) return;
        duration = Math.min(duration, MAX_SESSION_MS);
        record(prefs, bookName, duration, System.currentTimeMillis());
    }

    static void record(SharedPreferences prefs, String bookName, long durationMs, long wallClockMs) {
        if (prefs == null || durationMs <= 0L) return;
        try {
            String today = dayKey(wallClockMs);
            JSONObject days = object(prefs.getString(KEY_DAYS, "{}"));
            JSONObject books = object(prefs.getString(KEY_BOOKS, "{}"));
            JSONObject dayBooks = object(prefs.getString(KEY_DAY_BOOKS, "{}"));

            days.put(today, safeAdd(days.optLong(today, 0L), durationMs));
            String cleanBook = bookName == null ? "" : bookName.trim();
            if (!cleanBook.isEmpty()) {
                String hashedBook = bookKey(cleanBook);
                books.put(hashedBook, safeAdd(books.optLong(hashedBook, 0L), durationMs));
                JSONObject todayBooks = dayBooks.optJSONObject(today);
                if (todayBooks == null) todayBooks = new JSONObject();
                todayBooks.put(cleanBook, safeAdd(todayBooks.optLong(cleanBook, 0L), durationMs));
                dayBooks.put(today, todayBooks);
            }

            int current = prefs.getInt(KEY_CURRENT_STREAK, 0);
            int longest = prefs.getInt(KEY_LONGEST_STREAK, 0);
            String lastDay = prefs.getString(KEY_LAST_DAY, "");
            if (!today.equals(lastDay)) {
                if (lastDay != null && lastDay.equals(previousDayKey(wallClockMs))) current = Math.max(1, current + 1);
                else current = 1;
                longest = Math.max(longest, current);
                lastDay = today;
            }

            prefs.edit()
                    .putLong(KEY_TOTAL_MS, safeAdd(prefs.getLong(KEY_TOTAL_MS, 0L), durationMs))
                    .putString(KEY_DAYS, days.toString())
                    .putString(KEY_BOOKS, books.toString())
                    .putString(KEY_DAY_BOOKS, dayBooks.toString())
                    .putInt(KEY_CURRENT_STREAK, current)
                    .putInt(KEY_LONGEST_STREAK, longest)
                    .putString(KEY_LAST_DAY, lastDay == null ? today : lastDay)
                    .putLong("sync_updated_ms", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {}
    }

    public static Snapshot snapshot(SharedPreferences prefs, String bookName) {
        Snapshot s = new Snapshot();
        if (prefs == null) return s;
        try {
            JSONObject days = object(prefs.getString(KEY_DAYS, "{}"));
            JSONObject books = object(prefs.getString(KEY_BOOKS, "{}"));
            s.todayMs = days.optLong(dayKey(System.currentTimeMillis()), 0L);
            s.totalMs = prefs.getLong(KEY_TOTAL_MS, 0L);
            s.bookMs = bookName == null ? 0L : books.optLong(bookKey(bookName), 0L);
            s.currentStreak = calculateCurrentStreak(days);
            s.longestStreak = Math.max(prefs.getInt(KEY_LONGEST_STREAK, 0), calculateLongestStreak(days));
            s.activeDays = countPositiveDays(days);
        } catch (Exception ignored) {}
        return s;
    }

    public static Snapshot snapshot(SharedPreferences prefs) { return snapshot(prefs, null); }

    public static String dayKey(int year, int monthOneBased, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, Math.max(0, monthOneBased - 1), day, 12, 0, 0);
        return dayKey(c.getTimeInMillis());
    }

    public static long dayTime(SharedPreferences prefs, String key) {
        if (prefs == null || key == null) return 0L;
        return object(prefs.getString(KEY_DAYS, "{}")).optLong(key, 0L);
    }

    public static long bookTimeForDay(SharedPreferences prefs, String key, String fileName) {
        if (prefs == null || key == null || fileName == null) return 0L;
        JSONObject date = object(prefs.getString(KEY_DAY_BOOKS, "{}")).optJSONObject(key);
        return date == null ? 0L : date.optLong(fileName, 0L);
    }

    public static long totalBookTime(SharedPreferences prefs, String fileName) {
        if (prefs == null || fileName == null) return 0L;
        return object(prefs.getString(KEY_BOOKS, "{}")).optLong(bookKey(fileName), 0L);
    }

    public static List<DayBook> booksForDay(SharedPreferences prefs, String key) {
        List<DayBook> out = new ArrayList<>();
        if (prefs == null || key == null) return out;
        JSONObject date = object(prefs.getString(KEY_DAY_BOOKS, "{}")).optJSONObject(key);
        if (date == null) return out;
        Iterator<String> names = date.keys();
        while (names.hasNext()) {
            String name = names.next();
            long ms = date.optLong(name, 0L);
            if (name != null && !name.trim().isEmpty() && ms > 0L) out.add(new DayBook(name, ms));
        }
        Collections.sort(out, new Comparator<DayBook>() {
            @Override public int compare(DayBook a, DayBook b) { return Long.compare(b.durationMs, a.durationMs); }
        });
        return out;
    }

    public static long readingTimeForMonth(SharedPreferences prefs, int year, int monthOneBased) {
        if (prefs == null) return 0L;
        String prefix = monthPrefix(year, monthOneBased);
        JSONObject days = object(prefs.getString(KEY_DAYS, "{}"));
        long total = 0L;
        Iterator<String> keys = days.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith(prefix)) total = safeAdd(total, days.optLong(key, 0L));
        }
        return total;
    }

    public static int activeDaysForMonth(SharedPreferences prefs, int year, int monthOneBased) {
        if (prefs == null) return 0;
        String prefix = monthPrefix(year, monthOneBased);
        JSONObject days = object(prefs.getString(KEY_DAYS, "{}"));
        int count = 0;
        Iterator<String> keys = days.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith(prefix) && days.optLong(key, 0L) > 0L) count++;
        }
        return count;
    }

    public static int uniqueBooksForMonth(SharedPreferences prefs, int year, int monthOneBased) {
        if (prefs == null) return 0;
        String prefix = monthPrefix(year, monthOneBased);
        JSONObject root = object(prefs.getString(KEY_DAY_BOOKS, "{}"));
        Set<String> books = new HashSet<>();
        Iterator<String> days = root.keys();
        while (days.hasNext()) {
            String key = days.next();
            if (!key.startsWith(prefix)) continue;
            JSONObject item = root.optJSONObject(key);
            if (item == null) continue;
            Iterator<String> names = item.keys();
            while (names.hasNext()) {
                String name = names.next();
                if (item.optLong(name, 0L) > 0L) books.add(name);
            }
        }
        return books.size();
    }

    public static String dailyNote(SharedPreferences prefs, String key) {
        if (prefs == null || key == null) return "";
        return object(prefs.getString(KEY_DAY_NOTES, "{}")).optString(key, "");
    }

    public static void setDailyNote(SharedPreferences prefs, String key, String note) {
        if (prefs == null || key == null) return;
        try {
            JSONObject root = object(prefs.getString(KEY_DAY_NOTES, "{}"));
            String clean = note == null ? "" : note.trim();
            if (clean.isEmpty()) root.remove(key); else root.put(key, clean);
            saveJson(prefs, KEY_DAY_NOTES, root);
        } catch (Exception ignored) {}
    }

    public static String bookDayNote(SharedPreferences prefs, String key, String fileName) {
        if (prefs == null || key == null || fileName == null) return "";
        JSONObject date = object(prefs.getString(KEY_BOOK_DAY_NOTES, "{}")).optJSONObject(key);
        return date == null ? "" : date.optString(fileName, "");
    }

    public static void setBookDayNote(SharedPreferences prefs, String key, String fileName, String note) {
        if (prefs == null || key == null || fileName == null) return;
        try {
            JSONObject root = object(prefs.getString(KEY_BOOK_DAY_NOTES, "{}"));
            JSONObject date = root.optJSONObject(key);
            if (date == null) date = new JSONObject();
            String clean = note == null ? "" : note.trim();
            if (clean.isEmpty()) date.remove(fileName); else date.put(fileName, clean);
            if (date.length() == 0) root.remove(key); else root.put(key, date);
            saveJson(prefs, KEY_BOOK_DAY_NOTES, root);
        } catch (Exception ignored) {}
    }

    private static void saveJson(SharedPreferences prefs, String key, JSONObject value) {
        prefs.edit().putString(key, value.toString())
                .putLong("sync_updated_ms", System.currentTimeMillis()).apply();
    }

    private static String monthPrefix(int year, int monthOneBased) {
        return String.format(Locale.US, "%04d-%02d-", year, monthOneBased);
    }

    private static String bookKey(String bookName) {
        return Integer.toHexString((bookName == null ? "book" : bookName).hashCode());
    }

    private static JSONObject object(String raw) {
        try { return new JSONObject(raw == null ? "{}" : raw); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private static int countPositiveDays(JSONObject days) {
        int count = 0;
        Iterator<String> keys = days.keys();
        while (keys.hasNext()) if (days.optLong(keys.next(), 0L) > 0L) count++;
        return count;
    }

    private static int calculateCurrentStreak(JSONObject days) {
        Calendar c = Calendar.getInstance();
        String today = dayKey(c.getTimeInMillis());
        if (days.optLong(today, 0L) <= 0L) {
            c.add(Calendar.DAY_OF_YEAR, -1);
            if (days.optLong(dayKey(c.getTimeInMillis()), 0L) <= 0L) return 0;
        }
        int count = 0;
        while (count < 3660 && days.optLong(dayKey(c.getTimeInMillis()), 0L) > 0L) {
            count++;
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return count;
    }

    private static int calculateLongestStreak(JSONObject days) {
        if (days.length() == 0) return 0;
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -3650);
        int longest = 0;
        int running = 0;
        for (int i = 0; i <= 3650; i++) {
            if (days.optLong(dayKey(c.getTimeInMillis()), 0L) > 0L) {
                running++;
                longest = Math.max(longest, running);
            } else running = 0;
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return longest;
    }

    private static String previousDayKey(long wallClockMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(wallClockMs);
        c.add(Calendar.DAY_OF_YEAR, -1);
        return dayKey(c.getTimeInMillis());
    }

    private static String dayKey(long wallClockMs) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(wallClockMs));
    }

    private static long safeAdd(long a, long b) {
        if (b > 0L && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return Math.max(0L, a + b);
    }
}
''')

# ---------- Shelf store: add safe rename/delete ----------
write("app/src/main/java/com/whisper/wowreader/LibraryShelfStore.java", r'''package com.whisper.wowreader;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Local-first custom shelf storage. Deleting a shelf never deletes its books. */
public final class LibraryShelfStore {
    private LibraryShelfStore() {}
    private static final String KEY = "library_shelves_json";

    public static List<String> shelves(SharedPreferences prefs) {
        List<String> result = new ArrayList<>();
        if (prefs == null) return result;
        JSONObject root = object(prefs.getString(KEY, "{}"));
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String name = cleanName(keys.next());
            if (!name.isEmpty()) result.add(name);
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public static boolean createShelf(SharedPreferences prefs, String shelfName) {
        if (prefs == null) return false;
        String name = cleanName(shelfName);
        if (name.isEmpty()) return false;
        try {
            JSONObject root = object(prefs.getString(KEY, "{}"));
            if (!root.has(name)) root.put(name, new JSONArray());
            save(prefs, root);
            return true;
        } catch (Exception ignored) { return false; }
    }

    public static boolean renameShelf(SharedPreferences prefs, String oldShelfName, String newShelfName) {
        if (prefs == null) return false;
        String oldName = cleanName(oldShelfName);
        String newName = cleanName(newShelfName);
        if (oldName.isEmpty() || newName.isEmpty()) return false;
        if (oldName.equals(newName)) return true;
        try {
            JSONObject root = object(prefs.getString(KEY, "{}"));
            if (!root.has(oldName) || root.has(newName)) return false;
            JSONArray books = root.optJSONArray(oldName);
            root.remove(oldName);
            root.put(newName, books == null ? new JSONArray() : books);
            save(prefs, root);
            return true;
        } catch (Exception ignored) { return false; }
    }

    public static boolean deleteShelf(SharedPreferences prefs, String shelfName) {
        if (prefs == null) return false;
        String name = cleanName(shelfName);
        if (name.isEmpty()) return false;
        try {
            JSONObject root = object(prefs.getString(KEY, "{}"));
            if (!root.has(name)) return false;
            root.remove(name);
            save(prefs, root);
            return true;
        } catch (Exception ignored) { return false; }
    }

    public static boolean contains(SharedPreferences prefs, String shelfName, String bookName) {
        if (prefs == null || bookName == null) return false;
        String name = cleanName(shelfName);
        if (name.isEmpty()) return false;
        JSONObject root = object(prefs.getString(KEY, "{}"));
        JSONArray books = root.optJSONArray(name);
        if (books == null) return false;
        for (int i = 0; i < books.length(); i++) if (bookName.equals(books.optString(i, ""))) return true;
        return false;
    }

    public static void setMembership(SharedPreferences prefs, String shelfName, String bookName, boolean included) {
        if (prefs == null || bookName == null || bookName.trim().isEmpty()) return;
        String name = cleanName(shelfName);
        if (name.isEmpty()) return;
        try {
            JSONObject root = object(prefs.getString(KEY, "{}"));
            JSONArray old = root.optJSONArray(name);
            if (old == null) old = new JSONArray();
            JSONArray next = new JSONArray();
            boolean found = false;
            for (int i = 0; i < old.length(); i++) {
                String value = old.optString(i, "");
                if (bookName.equals(value)) {
                    found = true;
                    if (!included) continue;
                }
                if (!value.isEmpty()) next.put(value);
            }
            if (included && !found) next.put(bookName);
            root.put(name, next);
            save(prefs, root);
        } catch (Exception ignored) {}
    }

    public static int count(SharedPreferences prefs, String shelfName) {
        if (prefs == null) return 0;
        JSONObject root = object(prefs.getString(KEY, "{}"));
        JSONArray books = root.optJSONArray(cleanName(shelfName));
        return books == null ? 0 : books.length();
    }

    public static void removeBookFromAll(SharedPreferences prefs, String bookName) {
        if (prefs == null || bookName == null) return;
        try {
            JSONObject root = object(prefs.getString(KEY, "{}"));
            Iterator<String> keys = root.keys();
            List<String> names = new ArrayList<>();
            while (keys.hasNext()) names.add(keys.next());
            boolean changed = false;
            for (String shelf : names) {
                JSONArray old = root.optJSONArray(shelf);
                if (old == null) continue;
                JSONArray next = new JSONArray();
                for (int i = 0; i < old.length(); i++) {
                    String value = old.optString(i, "");
                    if (bookName.equals(value)) { changed = true; continue; }
                    if (!value.isEmpty()) next.put(value);
                }
                root.put(shelf, next);
            }
            if (changed) save(prefs, root);
        } catch (Exception ignored) {}
    }

    private static void save(SharedPreferences prefs, JSONObject root) {
        prefs.edit().putString(KEY, root.toString())
                .putLong("sync_updated_ms", System.currentTimeMillis()).apply();
    }

    private static JSONObject object(String raw) {
        try { return new JSONObject(raw == null ? "{}" : raw); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private static String cleanName(String value) {
        if (value == null) return "";
        String clean = value.trim().replaceAll("\\s+", " ");
        return clean.length() > 40 ? clean.substring(0, 40).trim() : clean;
    }
}
''')

# ---------- Shared calendar UI palette ----------
write("app/src/main/java/com/whisper/wowreader/ReadingCalendarUi.java", r'''package com.whisper.wowreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

final class ReadingCalendarUi {
    final Context context;
    final SharedPreferences prefs;
    final String mode;
    final int background;
    final int card;
    final int control;
    final int primary;
    final int secondary;
    final int accent;
    final int stroke;
    final boolean darkSystemIcons;
    final Typeface myanmarTypeface;

    ReadingCalendarUi(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences("wow_reader", Context.MODE_PRIVATE);
        String stored = prefs.getString("app_theme", "white");
        mode = AppThemePalette.isSupportedTheme(stored) ? stored : "white";
        if ("custom".equals(mode)) {
            AppThemePalette p = AppThemePalette.custom(prefs);
            background = p.background; card = p.card; control = p.control;
            primary = p.primary; secondary = p.secondary; accent = p.accent; stroke = p.stroke;
            darkSystemIcons = p.darkSystemIcons;
        } else if ("black".equals(mode)) {
            background = Color.rgb(12, 13, 16); card = Color.rgb(27, 29, 34); control = Color.rgb(35, 37, 43);
            primary = Color.rgb(244, 247, 250); secondary = Color.rgb(178, 183, 192);
            accent = Color.rgb(151, 166, 255); stroke = Color.rgb(55, 59, 68); darkSystemIcons = false;
        } else if ("navy".equals(mode)) {
            background = Color.rgb(3, 28, 48); card = Color.rgb(7, 44, 70); control = Color.rgb(10, 51, 79);
            primary = Color.rgb(244, 247, 250); secondary = Color.rgb(165, 196, 213);
            accent = Color.rgb(239, 194, 91); stroke = Color.rgb(26, 91, 120); darkSystemIcons = false;
        } else {
            background = Color.rgb(247, 248, 251); card = Color.WHITE; control = Color.rgb(251, 251, 253);
            primary = Color.rgb(31, 34, 40); secondary = Color.rgb(105, 110, 122);
            accent = Color.rgb(82, 82, 214); stroke = Color.rgb(224, 227, 234); darkSystemIcons = true;
        }
        Typeface tf;
        try { tf = Typeface.createFromAsset(context.getAssets(), "fonts/pyidaungsu_native.ttf"); }
        catch (Exception ignored) { tf = Typeface.DEFAULT; }
        myanmarTypeface = tf;
    }

    int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    GradientDrawable rounded(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    void text(TextView v, float size, int color, boolean bold) {
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(myanmarTypeface, bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setIncludeFontPadding(true);
    }

    String formatDuration(long ms) {
        long minutes = Math.max(0L, ms) / 60_000L;
        if (minutes < 1L) return "<1m";
        if (minutes < 60L) return minutes + "m";
        long h = minutes / 60L, m = minutes % 60L;
        return m == 0L ? h + "h" : h + "h " + m + "m";
    }
}
''')

# ---------- Myanmar calendar bridge ----------
write("app/src/main/java/com/whisper/wowreader/MyanmarCalendarBridge.java", r'''package com.whisper.wowreader;

import java.util.ArrayList;
import java.util.List;

import mmcalendar.Astro;
import mmcalendar.HolidayCalculator;
import mmcalendar.MyanmarDate;

final class MyanmarCalendarBridge {
    private MyanmarCalendarBridge() {}

    static final class Info {
        final String year;
        final String monthName;
        final String moonPhase;
        final String fortnightDay;
        final String weekDay;
        final String sabbath;
        final List<String> holidays;

        Info(String year, String monthName, String moonPhase, String fortnightDay,
             String weekDay, String sabbath, List<String> holidays) {
            this.year = clean(year); this.monthName = clean(monthName); this.moonPhase = clean(moonPhase);
            this.fortnightDay = clean(fortnightDay); this.weekDay = clean(weekDay); this.sabbath = clean(sabbath);
            this.holidays = holidays == null ? new ArrayList<>() : holidays;
        }
    }

    static Info info(int year, int monthOneBased, int day) {
        try {
            MyanmarDate md = MyanmarDate.of(year, monthOneBased, day);
            Astro astro = Astro.of(md);
            List<String> holidays;
            try { holidays = new ArrayList<>(HolidayCalculator.getHoliday(md)); }
            catch (Exception ignored) { holidays = new ArrayList<>(); }
            return new Info(String.valueOf(md.getYear()), md.getMonthName(), md.getMoonPhase(),
                    md.getFortnightDay(), md.getWeekDay(), astro == null ? "" : astro.getSabbath(), holidays);
        } catch (Exception ignored) {
            return new Info("", "", "", "", "", "", new ArrayList<>());
        }
    }

    private static String clean(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        return "null".equalsIgnoreCase(s) ? "" : s.trim();
    }
}
''')

# ---------- Book cover helper ----------
write("app/src/main/java/com/whisper/wowreader/BookVisualUtil.java", r'''package com.whisper.wowreader;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class BookVisualUtil {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private BookVisualUtil() {}

    static String title(SharedPreferences prefs, String fileName) {
        if (fileName == null) return "Book";
        String fallback = stripExtension(fileName);
        String value = prefs == null ? fallback : prefs.getString("library_title_" + fileName, fallback);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    static String author(SharedPreferences prefs, String fileName) {
        if (prefs == null || fileName == null) return "";
        String value = prefs.getString("library_author_" + fileName, "");
        return value == null ? "" : value.trim();
    }

    static void loadCover(Activity activity, File file, ImageView target, int widthPx, int heightPx) {
        if (activity == null || target == null) return;
        String name = file == null ? "Book" : file.getName();
        target.setImageBitmap(placeholder(activity, name, widthPx, heightPx));
        if (file == null || !file.isFile()) return;
        final int tag = System.identityHashCode(file) ^ file.getName().hashCode();
        target.setTag(tag);
        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            try {
                if (file.getName().toLowerCase(Locale.ROOT).endsWith(".epub")) {
                    File cache = new File(activity.getFilesDir(), "cover_cache");
                    if (!cache.exists()) cache.mkdirs();
                    EpubUtil.Summary summary = EpubUtil.extractSummary(file, cache);
                    if (summary.cover != null && summary.cover.isFile()) bitmap = BitmapFactory.decodeFile(summary.cover.getAbsolutePath());
                } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    bitmap = pdfCover(file, Math.max(120, widthPx));
                }
            } catch (Exception ignored) {}
            if (bitmap == null) return;
            final Bitmap ready = bitmap;
            activity.runOnUiThread(() -> {
                Object current = target.getTag();
                if (!activity.isFinishing() && current instanceof Integer && ((Integer) current) == tag)
                    target.setImageBitmap(ready);
            });
        });
    }

    private static Bitmap pdfCover(File file, int width) {
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;
        PdfRenderer.Page page = null;
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            if (renderer.getPageCount() == 0) return null;
            page = renderer.openPage(0);
            int height = Math.max(1, Math.round(width * page.getHeight() / (float) page.getWidth()));
            Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);
            page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return b;
        } catch (Exception ignored) { return null; }
        finally {
            try { if (page != null) page.close(); } catch (Exception ignored) {}
            try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
            try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        }
    }

    private static Bitmap placeholder(Activity activity, String title, int width, int height) {
        int w = Math.max(40, width), h = Math.max(60, height);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] colors = {Color.rgb(92, 76, 150), Color.rgb(52, 100, 138), Color.rgb(146, 78, 75), Color.rgb(69, 111, 83), Color.rgb(137, 91, 57)};
        p.setColor(colors[Math.abs(title == null ? 0 : title.hashCode()) % colors.length]);
        c.drawRect(0, 0, w, h, p);
        p.setColor(Color.WHITE);
        Typeface tf;
        try { tf = Typeface.createFromAsset(activity.getAssets(), "fonts/pyidaungsu_native.ttf"); }
        catch (Exception ignored) { tf = Typeface.DEFAULT; }
        p.setTypeface(Typeface.create(tf, Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Math.min(w, h) * .28f);
        String letter = title == null || title.trim().isEmpty() ? "W" : title.trim().substring(0, 1);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(letter, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p);
        return b;
    }

    private static String stripExtension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : (name == null ? "Book" : name);
    }
}
''')

# ---------- Full Myanmar Reading Calendar ----------
write("app/src/main/java/com/whisper/wowreader/ReadingCalendarActivity.java", r'''package com.whisper.wowreader;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ReadingCalendarActivity extends Activity {
    private SharedPreferences prefs;
    private ReadingCalendarUi ui;
    private File libraryDir;
    private int year;
    private int month;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);
        ui = new ReadingCalendarUi(this);
        libraryDir = new File(getFilesDir(), "library");
        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH) + 1;
        render();
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(10));
        root.setBackgroundColor(ui.background);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = icon("‹", 29);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Reading Calendar", 21, ui.primary, true);
        TextView subtitle = label("Myanmar calendar · your books by day", 10.5f, ui.secondary, false);
        heading.addView(title);
        heading.addView(subtitle);
        top.addView(heading, new LinearLayout.LayoutParams(0, ui.dp(50), 1f));
        TextView today = label("Today", 12, ui.accent, true);
        today.setGravity(Gravity.CENTER);
        today.setBackground(ui.rounded(ui.control, 18, 1, ui.stroke));
        today.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance(); year = now.get(Calendar.YEAR); month = now.get(Calendar.MONTH) + 1; render();
        });
        top.addView(today, new LinearLayout.LayoutParams(ui.dp(68), ui.dp(38)));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(54)));

        LinearLayout monthCard = new LinearLayout(this);
        monthCard.setGravity(Gravity.CENTER_VERTICAL);
        monthCard.setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        monthCard.setBackground(ui.rounded(ui.card, 18, 1, ui.stroke));
        TextView prev = icon("‹", 27);
        prev.setOnClickListener(v -> moveMonth(-1));
        monthCard.addView(prev, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(54)));

        Calendar mid = Calendar.getInstance();
        mid.clear(); mid.set(year, month - 1, 15, 12, 0, 0);
        MyanmarCalendarBridge.Info mi = MyanmarCalendarBridge.info(year, month, 15);
        LinearLayout monthCopy = new LinearLayout(this);
        monthCopy.setOrientation(LinearLayout.VERTICAL);
        monthCopy.setGravity(Gravity.CENTER);
        String mmTitle = mi.monthName.isEmpty() ? "မြန်မာ ပြက္ခဒိန်" : mi.monthName + (mi.year.isEmpty() ? "" : " · " + mi.year);
        TextView mm = label(mmTitle, 17.5f, ui.primary, true);
        mm.setGravity(Gravity.CENTER);
        TextView western = label(new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(mid.getTime()), 10.5f, ui.secondary, false);
        western.setGravity(Gravity.CENTER);
        monthCopy.addView(mm);
        monthCopy.addView(western);
        monthCard.addView(monthCopy, new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        TextView next = icon("›", 27);
        next.setOnClickListener(v -> moveMonth(1));
        monthCard.addView(next, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(54)));
        LinearLayout.LayoutParams monthLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(60));
        monthLp.topMargin = ui.dp(6); root.addView(monthCard, monthLp);

        GridLayout dow = new GridLayout(this);
        dow.setColumnCount(7);
        String[] names = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
        for (int i = 0; i < names.length; i++) {
            TextView dayName = label(names[i], 9.2f, (i == 0 || i == 6) ? ui.accent : ui.secondary, true);
            dayName.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams lp = weightedCell(ui.dp(26));
            dow.addView(dayName, lp);
        }
        root.addView(dow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(30)));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        Calendar first = Calendar.getInstance();
        first.clear(); first.set(year, month - 1, 1, 12, 0, 0);
        int offset = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = 0; i < offset; i++) grid.addView(new View(this), weightedCell(ui.dp(78)));
        Calendar now = Calendar.getInstance();
        for (int day = 1; day <= days; day++) grid.addView(dayCell(day, now), weightedCell(ui.dp(78)));
        int used = offset + days;
        int remaining = (7 - (used % 7)) % 7;
        for (int i = 0; i < remaining; i++) grid.addView(new View(this), weightedCell(ui.dp(78)));
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7));
        summary.setBackground(ui.rounded(ui.card, 18, 1, ui.stroke));
        int active = ReadingStatsStore.activeDaysForMonth(prefs, year, month);
        int books = ReadingStatsStore.uniqueBooksForMonth(prefs, year, month);
        long time = ReadingStatsStore.readingTimeForMonth(prefs, year, month);
        summary.addView(metric(String.valueOf(active), "Days read"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        summary.addView(divider(), new LinearLayout.LayoutParams(ui.dp(1), ui.dp(34)));
        summary.addView(metric(String.valueOf(books), "Books"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        summary.addView(divider(), new LinearLayout.LayoutParams(ui.dp(1), ui.dp(34)));
        summary.addView(metric(ui.formatDuration(time), "Reading time"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(68));
        sumLp.topMargin = ui.dp(7); root.addView(summary, sumLp);

        setContentView(root);
        AppWindowInsets.apply(this, root, ui.background, ui.darkSystemIcons);
    }

    private View dayCell(int day, Calendar now) {
        final String key = ReadingStatsStore.dayKey(year, month, day);
        final long activityMs = ReadingStatsStore.dayTime(prefs, key);
        final List<ReadingStatsStore.DayBook> books = ReadingStatsStore.booksForDay(prefs, key);
        boolean isToday = year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH) + 1 && day == now.get(Calendar.DAY_OF_MONTH);

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cell.setPadding(ui.dp(2), ui.dp(3), ui.dp(2), ui.dp(2));
        int fill = isToday ? blend(ui.accent, ui.card, 0.91f) : ui.card;
        cell.setBackground(ui.rounded(fill, 8, 1, isToday ? ui.accent : ui.stroke));
        cell.setClickable(true);
        cell.setOnClickListener(v -> openDay(day, key));

        TextView number = label(String.valueOf(day), 10.8f, isToday ? ui.accent : ui.primary, true);
        number.setGravity(Gravity.CENTER);
        cell.addView(number, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(18)));
        MyanmarCalendarBridge.Info info = MyanmarCalendarBridge.info(year, month, day);
        String lunar = info.moonPhase + (info.fortnightDay.isEmpty() ? "" : " " + info.fortnightDay);
        TextView lunarText = label(lunar.trim(), 7.4f, ui.secondary, false);
        lunarText.setGravity(Gravity.CENTER);
        lunarText.setSingleLine(true);
        cell.addView(lunarText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(15)));

        if (!books.isEmpty()) {
            LinearLayout covers = new LinearLayout(this);
            covers.setGravity(Gravity.CENTER);
            int max = Math.min(2, books.size());
            for (int i = 0; i < max; i++) {
                ReadingStatsStore.DayBook db = books.get(i);
                ImageView cover = new ImageView(this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cover.setClipToOutline(true);
                cover.setBackground(ui.rounded(ui.control, 4, 0, 0));
                File file = new File(libraryDir, db.fileName);
                BookVisualUtil.loadCover(this, file, cover, ui.dp(24), ui.dp(34));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ui.dp(21), ui.dp(31));
                if (i > 0) cp.leftMargin = ui.dp(2);
                covers.addView(cover, cp);
            }
            cell.addView(covers, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(33)));
            if (books.size() > 2) {
                TextView more = label("+" + (books.size() - 2), 7.5f, ui.accent, true);
                more.setGravity(Gravity.CENTER);
                cell.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(9)));
            }
        } else if (activityMs > 0L) {
            TextView old = label("●  " + ui.formatDuration(activityMs), 7.7f, ui.accent, true);
            old.setGravity(Gravity.CENTER);
            cell.addView(old, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(29)));
        }
        return cell;
    }

    private void openDay(int day, String key) {
        Intent i = new Intent(this, ReadingDayActivity.class);
        i.putExtra("year", year); i.putExtra("month", month); i.putExtra("day", day); i.putExtra("day_key", key);
        startActivity(i);
    }

    private void moveMonth(int delta) {
        month += delta;
        if (month < 1) { month = 12; year--; }
        if (month > 12) { month = 1; year++; }
        render();
    }

    private TextView icon(String value, float size) {
        TextView v = label(value, size, ui.accent, false);
        v.setGravity(Gravity.CENTER);
        v.setBackground(ui.rounded(ui.control, 18, 1, ui.stroke));
        return v;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(text == null ? "" : text); ui.text(v, size, color, bold); return v;
    }

    private View metric(String value, String name) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        TextView v = label(value, 14, ui.primary, true); v.setGravity(Gravity.CENTER);
        TextView n = label(name, 8.7f, ui.secondary, false); n.setGravity(Gravity.CENTER);
        box.addView(v); box.addView(n); return box;
    }

    private View divider() { View v = new View(this); v.setBackgroundColor(ui.stroke); return v; }

    private GridLayout.LayoutParams weightedCell(int h) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0; lp.height = h; lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(ui.dp(1), ui.dp(1), ui.dp(1), ui.dp(1)); return lp;
    }

    private static int blend(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(Math.round(Color.red(from) * (1f - t) + Color.red(to) * t),
                Math.round(Color.green(from) * (1f - t) + Color.green(to) * t),
                Math.round(Color.blue(from) * (1f - t) + Color.blue(to) * t));
    }
}
''')

# ---------- Day detail + daily note ----------
write("app/src/main/java/com/whisper/wowreader/ReadingDayActivity.java", r'''package com.whisper.wowreader;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ReadingDayActivity extends Activity {
    private SharedPreferences prefs;
    private ReadingCalendarUi ui;
    private File libraryDir;
    private int year, month, day;
    private String dayKey;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);
        ui = new ReadingCalendarUi(this);
        libraryDir = new File(getFilesDir(), "library");
        year = getIntent().getIntExtra("year", Calendar.getInstance().get(Calendar.YEAR));
        month = getIntent().getIntExtra("month", Calendar.getInstance().get(Calendar.MONTH) + 1);
        day = getIntent().getIntExtra("day", Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        dayKey = getIntent().getStringExtra("day_key");
        if (dayKey == null || dayKey.isEmpty()) dayKey = ReadingStatsStore.dayKey(year, month, day);
        render();
    }

    @Override protected void onResume() { super.onResume(); if (ui != null) render(); }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.background);
        root.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12));

        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = button("‹", 28); back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)));
        TextView topTitle = text("Reading Day", 20, ui.primary, true);
        top.addView(topTitle, new LinearLayout.LayoutParams(0, ui.dp(46), 1f));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(52)));

        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0, ui.dp(4), 0, ui.dp(18));
        scroll.addView(content);

        Calendar date = Calendar.getInstance(); date.clear(); date.set(year, month - 1, day, 12, 0, 0);
        MyanmarCalendarBridge.Info info = MyanmarCalendarBridge.info(year, month, day);
        LinearLayout hero = card(); hero.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14)); hero.setGravity(Gravity.CENTER);
        TextView mm = text(info.monthName + (info.moonPhase.isEmpty() ? "" : " · " + info.moonPhase + " " + info.fortnightDay), 18, ui.primary, true); mm.setGravity(Gravity.CENTER);
        TextView yr = text(info.year.isEmpty() ? "" : "မြန်မာနှစ် " + info.year, 11, ui.accent, true); yr.setGravity(Gravity.CENTER);
        TextView west = text(new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(date.getTime()), 10.5f, ui.secondary, false); west.setGravity(Gravity.CENTER);
        hero.addView(mm); hero.addView(yr); hero.addView(west);
        content.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(96)));

        final long total = ReadingStatsStore.dayTime(prefs, dayKey);
        final List<ReadingStatsStore.DayBook> books = ReadingStatsStore.booksForDay(prefs, dayKey);
        LinearLayout metrics = new LinearLayout(this); metrics.setGravity(Gravity.CENTER); metrics.setPadding(ui.dp(6), ui.dp(5), ui.dp(6), ui.dp(5)); metrics.setBackground(ui.rounded(ui.card, 18, 1, ui.stroke));
        metrics.addView(metric(String.valueOf(books.size()), "Books read"), new LinearLayout.LayoutParams(0, ui.dp(52), 1f));
        View line = new View(this); line.setBackgroundColor(ui.stroke); metrics.addView(line, new LinearLayout.LayoutParams(ui.dp(1), ui.dp(32)));
        metrics.addView(metric(ui.formatDuration(total), "Reading time"), new LinearLayout.LayoutParams(0, ui.dp(52), 1f));
        LinearLayout.LayoutParams metricsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)); metricsLp.topMargin = ui.dp(8); content.addView(metrics, metricsLp);

        TextView section = text("Books from this day", 15.5f, ui.primary, true); section.setPadding(ui.dp(2), ui.dp(14), ui.dp(2), ui.dp(6)); content.addView(section);
        if (books.isEmpty()) {
            TextView empty = text(total > 0 ? "Reading activity is saved for this day. Book-by-day tracking starts with the new calendar version." : "No reading activity on this day yet.", 12, ui.secondary, false);
            empty.setGravity(Gravity.CENTER); empty.setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(18)); empty.setBackground(ui.rounded(ui.card, 16, 1, ui.stroke));
            content.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(88)));
        } else {
            for (ReadingStatsStore.DayBook item : books) content.addView(bookRow(item));
        }

        LinearLayout noteCard = card(); noteCard.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        LinearLayout noteHead = new LinearLayout(this); noteHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView noteTitle = text("Daily Reading Note", 14.5f, ui.primary, true); noteHead.addView(noteTitle, new LinearLayout.LayoutParams(0, ui.dp(36), 1f));
        TextView edit = text("Edit", 11.5f, ui.accent, true); edit.setGravity(Gravity.CENTER); edit.setBackground(ui.rounded(ui.control, 16, 1, ui.stroke)); edit.setOnClickListener(v -> editDailyNote());
        noteHead.addView(edit, new LinearLayout.LayoutParams(ui.dp(62), ui.dp(34))); noteCard.addView(noteHead);
        String note = ReadingStatsStore.dailyNote(prefs, dayKey);
        TextView noteBody = text(note.isEmpty() ? "နေ့ရက်အတွက် ဖတ်ရှုမှတ်စုလေးရေးနိုင်ပါတယ်။" : note, 12.5f, note.isEmpty() ? ui.secondary : ui.primary, false);
        noteBody.setPadding(ui.dp(2), ui.dp(7), ui.dp(2), ui.dp(4)); noteBody.setLineSpacing(ui.dp(2), 1.12f); noteCard.addView(noteBody);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); noteLp.topMargin = ui.dp(14); content.addView(noteCard, noteLp);

        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root); AppWindowInsets.apply(this, root, ui.background, ui.darkSystemIcons);
    }

    private View bookRow(ReadingStatsStore.DayBook item) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(ui.dp(9), ui.dp(8), ui.dp(10), ui.dp(8)); row.setBackground(ui.rounded(ui.card, 16, 1, ui.stroke)); row.setClickable(true);
        File file = new File(libraryDir, item.fileName);
        ImageView cover = new ImageView(this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP); cover.setClipToOutline(true); cover.setBackground(ui.rounded(ui.control, 8, 0, 0));
        row.addView(cover, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(74))); BookVisualUtil.loadCover(this, file, cover, ui.dp(130), ui.dp(190));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(ui.dp(12), 0, ui.dp(5), 0); copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(BookVisualUtil.title(prefs, item.fileName), 13.5f, ui.primary, true); title.setMaxLines(2); copy.addView(title);
        String author = BookVisualUtil.author(prefs, item.fileName);
        TextView meta = text((author.isEmpty() ? "" : author + " · ") + ui.formatDuration(item.durationMs), 10.3f, ui.secondary, false); meta.setPadding(0, ui.dp(4), 0, 0); copy.addView(meta);
        String memory = ReadingStatsStore.bookDayNote(prefs, dayKey, item.fileName);
        if (!memory.isEmpty()) { TextView mem = text(memory, 9.8f, ui.accent, false); mem.setMaxLines(1); mem.setPadding(0, ui.dp(5), 0, 0); copy.addView(mem); }
        row.addView(copy, new LinearLayout.LayoutParams(0, ui.dp(74), 1f));
        TextView arrow = text("›", 22, ui.secondary, false); arrow.setGravity(Gravity.CENTER); row.addView(arrow, new LinearLayout.LayoutParams(ui.dp(26), ui.dp(58)));
        row.setOnClickListener(v -> { Intent i = new Intent(this, ReadingMemoryActivity.class); i.putExtra("book_name", item.fileName); i.putExtra("day_key", dayKey); i.putExtra("year", year); i.putExtra("month", month); i.putExtra("day", day); startActivity(i); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(90)); lp.bottomMargin = ui.dp(7); row.setLayoutParams(lp); return row;
    }

    private void editDailyNote() {
        Dialog dialog = new Dialog(this); dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout box = card(); box.setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(16));
        TextView title = text("Daily Reading Note", 19, ui.primary, true); box.addView(title);
        EditText input = new EditText(this); input.setText(ReadingStatsStore.dailyNote(prefs, dayKey)); input.setHint("ဒီနေ့ ဖတ်ခဲ့တာနဲ့ ပတ်သက်ပြီး မှတ်ထားရန်…"); input.setTextSize(13); input.setTextColor(ui.primary); input.setHintTextColor(ui.secondary); input.setGravity(Gravity.TOP); input.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10)); input.setBackground(ui.rounded(ui.control, 15, 1, ui.stroke)); input.setTypeface(ui.myanmarTypeface); box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(146)));
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = action("Cancel", false); cancel.setOnClickListener(v -> dialog.dismiss());
        TextView save = action("Save", true); save.setOnClickListener(v -> { ReadingStatsStore.setDailyNote(prefs, dayKey, input.getText().toString()); dialog.dismiss(); render(); GoogleAutoSync.scheduleSoon(this); });
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(ui.dp(88), ui.dp(40)); c.rightMargin = ui.dp(8); actions.addView(cancel, c); actions.addView(save, new LinearLayout.LayoutParams(ui.dp(90), ui.dp(40))); box.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(54)));
        dialog.setContentView(box); dialog.show(); styleDialog(dialog, .90f);
    }

    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setBackground(ui.rounded(ui.card, 18, 1, ui.stroke)); return v; }
    private TextView text(String value, float size, int color, boolean bold) { TextView v = new TextView(this); v.setText(value == null ? "" : value); ui.text(v, size, color, bold); return v; }
    private TextView button(String value, float size) { TextView v = text(value, size, ui.accent, false); v.setGravity(Gravity.CENTER); v.setBackground(ui.rounded(ui.control, 18, 1, ui.stroke)); return v; }
    private View metric(String value, String label) { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); TextView a = text(value, 14, ui.primary, true); a.setGravity(Gravity.CENTER); TextView b = text(label, 9, ui.secondary, false); b.setGravity(Gravity.CENTER); box.addView(a); box.addView(b); return box; }
    private TextView action(String value, boolean primary) { TextView v = text(value, 12, primary ? Color.WHITE : ui.primary, true); v.setGravity(Gravity.CENTER); v.setBackground(ui.rounded(primary ? ui.accent : ui.control, 16, 1, primary ? ui.accent : ui.stroke)); return v; }
    private void styleDialog(Dialog dialog, float fraction) { android.view.Window w = dialog.getWindow(); if (w == null) return; w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); w.setDimAmount(.42f); w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND); int sw = getResources().getDisplayMetrics().widthPixels; w.setLayout(Math.min((int)(sw * fraction), ui.dp(620)), ViewGroup.LayoutParams.WRAP_CONTENT); }
}
''')

# ---------- Per-book reading memory/details ----------
write("app/src/main/java/com/whisper/wowreader/ReadingMemoryActivity.java", r'''package com.whisper.wowreader;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ReadingMemoryActivity extends Activity {
    private SharedPreferences prefs;
    private ReadingCalendarUi ui;
    private String bookName, dayKey;
    private int year, month, day;
    private File bookFile;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE); ui = new ReadingCalendarUi(this);
        bookName = getIntent().getStringExtra("book_name"); dayKey = getIntent().getStringExtra("day_key");
        year = getIntent().getIntExtra("year", Calendar.getInstance().get(Calendar.YEAR)); month = getIntent().getIntExtra("month", Calendar.getInstance().get(Calendar.MONTH) + 1); day = getIntent().getIntExtra("day", Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        if (dayKey == null) dayKey = ReadingStatsStore.dayKey(year, month, day);
        bookFile = new File(new File(getFilesDir(), "library"), bookName == null ? "" : bookName);
        render();
    }

    @Override protected void onResume() { super.onResume(); if (ui != null) render(); }

    private void render() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12)); root.setBackgroundColor(ui.background);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = smallButton("‹", 28); back.setOnClickListener(v -> finish()); top.addView(back, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)));
        TextView head = text("Book Details", 20, ui.primary, true); top.addView(head, new LinearLayout.LayoutParams(0, ui.dp(46), 1f)); root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(52)));

        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0, ui.dp(5), 0, ui.dp(18)); scroll.addView(content);
        LinearLayout bookCard = card(); bookCard.setOrientation(LinearLayout.HORIZONTAL); bookCard.setGravity(Gravity.CENTER_VERTICAL); bookCard.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        ImageView cover = new ImageView(this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP); cover.setClipToOutline(true); cover.setBackground(ui.rounded(ui.control, 10, 0, 0)); bookCard.addView(cover, new LinearLayout.LayoutParams(ui.dp(92), ui.dp(134))); BookVisualUtil.loadCover(this, bookFile, cover, ui.dp(230), ui.dp(340));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(ui.dp(14), 0, 0, 0); copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(BookVisualUtil.title(prefs, bookName), 17, ui.primary, true); title.setMaxLines(3); copy.addView(title);
        String author = BookVisualUtil.author(prefs, bookName); TextView a = text(author.isEmpty() ? "Unknown author" : author, 11, ui.secondary, false); a.setPadding(0, ui.dp(5), 0, 0); copy.addView(a);
        int progress = prefs.getInt("percent_" + bookName, 0); TextView p = text(progress >= 100 ? "✓ Finished · 100%" : progress + "% read", 11.5f, ui.accent, true); p.setPadding(0, ui.dp(11), 0, 0); copy.addView(p);
        bookCard.addView(copy, new LinearLayout.LayoutParams(0, ui.dp(134), 1f)); content.addView(bookCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(158)));

        Calendar date = Calendar.getInstance(); date.clear(); date.set(year, month - 1, day, 12, 0, 0); MyanmarCalendarBridge.Info info = MyanmarCalendarBridge.info(year, month, day);
        TextView dateTitle = text((info.monthName.isEmpty() ? "" : info.monthName + " · ") + new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(date.getTime()), 12, ui.secondary, true); dateTitle.setPadding(ui.dp(3), ui.dp(12), ui.dp(3), ui.dp(5)); content.addView(dateTitle);
        LinearLayout stats = new LinearLayout(this); stats.setGravity(Gravity.CENTER); stats.setBackground(ui.rounded(ui.card, 16, 1, ui.stroke));
        stats.addView(metric(ui.formatDuration(ReadingStatsStore.bookTimeForDay(prefs, dayKey, bookName)), "This day"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        stats.addView(metric(ui.formatDuration(ReadingStatsStore.totalBookTime(prefs, bookName)), "Total reading"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f)); content.addView(stats, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(62)));

        LinearLayout memory = card(); memory.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(14));
        LinearLayout mh = new LinearLayout(this); mh.setGravity(Gravity.CENTER_VERTICAL); TextView mt = text("Reading Memory", 15.5f, ui.primary, true); mh.addView(mt, new LinearLayout.LayoutParams(0, ui.dp(38), 1f)); TextView edit = text("Edit", 11.5f, ui.accent, true); edit.setGravity(Gravity.CENTER); edit.setBackground(ui.rounded(ui.control, 15, 1, ui.stroke)); edit.setOnClickListener(v -> editMemory()); mh.addView(edit, new LinearLayout.LayoutParams(ui.dp(62), ui.dp(34))); memory.addView(mh);
        String note = ReadingStatsStore.bookDayNote(prefs, dayKey, bookName); TextView body = text(note.isEmpty() ? "ဒီနေ့ ဒီစာအုပ်ဖတ်ရင်း မှတ်ထားချင်တာကို ရေးနိုင်ပါတယ်။" : note, 12.5f, note.isEmpty() ? ui.secondary : ui.primary, false); body.setLineSpacing(ui.dp(2), 1.14f); body.setPadding(ui.dp(2), ui.dp(8), ui.dp(2), ui.dp(4)); memory.addView(body); LinearLayout.LayoutParams memLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); memLp.topMargin = ui.dp(12); content.addView(memory, memLp);

        if (bookFile.isFile()) { TextView open = text("Open book  ›", 13, Color.WHITE, true); open.setGravity(Gravity.CENTER); open.setBackground(ui.rounded(ui.accent, 19, 0, 0)); open.setOnClickListener(v -> { Intent i = new Intent(this, BookReaderActivity.class); i.putExtra("path", bookFile.getAbsolutePath()); startActivity(i); }); LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48)); op.topMargin = ui.dp(12); content.addView(open, op); }
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)); setContentView(root); AppWindowInsets.apply(this, root, ui.background, ui.darkSystemIcons);
    }

    private void editMemory() {
        Dialog dialog = new Dialog(this); dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE); LinearLayout box = card(); box.setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(16)); box.addView(text("Reading Memory", 19, ui.primary, true));
        EditText input = new EditText(this); input.setText(ReadingStatsStore.bookDayNote(prefs, dayKey, bookName)); input.setHint("ဒီစာအုပ်ဖတ်ရင်း ခံစားချက်၊ မှတ်ချက်…"); input.setTextSize(13); input.setTextColor(ui.primary); input.setHintTextColor(ui.secondary); input.setGravity(Gravity.TOP); input.setTypeface(ui.myanmarTypeface); input.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10)); input.setBackground(ui.rounded(ui.control, 15, 1, ui.stroke)); box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(150)));
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); TextView cancel = action("Cancel", false); cancel.setOnClickListener(v -> dialog.dismiss()); TextView save = action("Save", true); save.setOnClickListener(v -> { ReadingStatsStore.setBookDayNote(prefs, dayKey, bookName, input.getText().toString()); dialog.dismiss(); render(); GoogleAutoSync.scheduleSoon(this); }); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ui.dp(88), ui.dp(40)); cp.rightMargin = ui.dp(8); actions.addView(cancel, cp); actions.addView(save, new LinearLayout.LayoutParams(ui.dp(90), ui.dp(40))); box.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(54))); dialog.setContentView(box); dialog.show(); styleDialog(dialog);
    }

    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setBackground(ui.rounded(ui.card, 18, 1, ui.stroke)); return v; }
    private TextView text(String value, float size, int color, boolean bold) { TextView v = new TextView(this); v.setText(value == null ? "" : value); ui.text(v, size, color, bold); return v; }
    private TextView smallButton(String value, float size) { TextView v = text(value, size, ui.accent, false); v.setGravity(Gravity.CENTER); v.setBackground(ui.rounded(ui.control, 18, 1, ui.stroke)); return v; }
    private View metric(String value, String label) { LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setGravity(Gravity.CENTER); TextView v = text(value, 14, ui.primary, true); v.setGravity(Gravity.CENTER); TextView l = text(label, 9, ui.secondary, false); l.setGravity(Gravity.CENTER); b.addView(v); b.addView(l); return b; }
    private TextView action(String value, boolean primary) { TextView v = text(value, 12, primary ? Color.WHITE : ui.primary, true); v.setGravity(Gravity.CENTER); v.setBackground(ui.rounded(primary ? ui.accent : ui.control, 16, 1, primary ? ui.accent : ui.stroke)); return v; }
    private void styleDialog(Dialog dialog) { android.view.Window w = dialog.getWindow(); if (w == null) return; w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); w.setDimAmount(.42f); w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND); int sw = getResources().getDisplayMetrics().widthPixels; w.setLayout(Math.min((int)(sw * .90f), ui.dp(620)), ViewGroup.LayoutParams.WRAP_CONTENT); }
}
''')

# ---------- MainActivity: Statistics -> Calendar + Shelf management ----------
replace_once(
    "app/src/main/java/com/whisper/wowreader/MainActivity.java",
    '        LinearLayout sheet = premiumSheet("Reading statistics", "Your reading activity", dialog);\n'
    '        sheet.addView(statSheetRow("◷", "Today", "Time spent reading today", formatReadingTimeLong(stats.todayMs), themeAccent()));',
    '        LinearLayout sheet = premiumSheet("Reading statistics", "Your reading activity", dialog);\n'
    '        sheet.addView(statSheetActionRow("▦", "Reading calendar", "Myanmar calendar · books and memories by day", "Open  ›", themeAccent(), () -> {\n'
    '            dialog.dismiss();\n'
    '            startActivity(new Intent(this, ReadingCalendarActivity.class));\n'
    '        }));\n'
    '        sheet.addView(statSheetRow("◷", "Today", "Time spent reading today", formatReadingTimeLong(stats.todayMs), themeAccent()));',
)
replace_once(
    "app/src/main/java/com/whisper/wowreader/MainActivity.java",
    '    private View statSheetRow(String iconText, String title, String subtitle, String value, int accent) {',
    '    private View statSheetActionRow(String iconText, String title, String subtitle, String value, int accent, Runnable action) {\n'
    '        View row = statSheetRow(iconText, title, subtitle, value, accent);\n'
    '        row.setClickable(true);\n'
    '        row.setOnClickListener(v -> action.run());\n'
    '        return row;\n'
    '    }\n\n'
    '    private View statSheetRow(String iconText, String title, String subtitle, String value, int accent) {',
)
replace_once(
    "app/src/main/java/com/whisper/wowreader/MainActivity.java",
    '        TextView newShelf = filterChoice("＋ New", false);\n'
    '        newShelf.setOnClickListener(v -> { dialog.dismiss(); showCreateShelfDialog(null); });\n'
    '        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(dp(82), dp(38)); newLp.leftMargin = dp(6); shelfRow.addView(newShelf, newLp);\n'
    '        shelfScroll.addView(shelfRow);',
    '        TextView newShelf = filterChoice("＋ New", false);\n'
    '        newShelf.setOnClickListener(v -> { dialog.dismiss(); showCreateShelfDialog(null); });\n'
    '        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(dp(82), dp(38)); newLp.leftMargin = dp(6); shelfRow.addView(newShelf, newLp);\n'
    '        TextView manageShelf = filterChoice("Manage", false);\n'
    '        manageShelf.setOnClickListener(v -> { dialog.dismiss(); showShelvesDialog(); });\n'
    '        LinearLayout.LayoutParams manageLp = new LinearLayout.LayoutParams(dp(82), dp(38)); manageLp.leftMargin = dp(6); shelfRow.addView(manageShelf, manageLp);\n'
    '        shelfScroll.addView(shelfRow);',
)
replace_once(
    "app/src/main/java/com/whisper/wowreader/MainActivity.java",
    '        for (String shelf : shelves) {\n'
    '            int count = LibraryShelfStore.count(prefs, shelf);\n'
    '            list.addView(premiumChoiceRow(shelf, count + (count == 1 ? " book" : " books"), shelf.equals(shelfFilter), () -> {\n'
    '                shelfFilter = shelf;\n'
    '                refreshLibrary();\n'
    '                dialog.dismiss();\n'
    '            }));\n'
    '        }',
    '        for (String shelf : shelves) {\n'
    '            int count = LibraryShelfStore.count(prefs, shelf);\n'
    '            list.addView(premiumShelfRow(shelf, count, shelf.equals(shelfFilter), dialog));\n'
    '        }',
)
replace_once(
    "app/src/main/java/com/whisper/wowreader/MainActivity.java",
    '    private View premiumChoiceRow(String titleText, String subtitleText, boolean selected, Runnable action) {',
    r'''    private View premiumShelfRow(String shelf, int count, boolean selected, android.app.Dialog parentDialog) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(4), dp(6), dp(4));
        row.setBackground(roundRect(selected ? themeSelectedSurface() : themeControlSurface(),
                dp(15), dp(1), selected ? themeAccent() : themeStroke()));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(shelf);
        title.setTextSize(13f);
        title.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        title.setTextColor(selected ? themeAccent() : themePrimaryText());
        copy.addView(title);
        TextView sub = new TextView(this);
        sub.setText(count + (count == 1 ? " book" : " books"));
        sub.setTextSize(9.5f);
        sub.setTextColor(themeSecondaryText());
        copy.addView(sub);
        copy.setClickable(true);
        copy.setOnClickListener(v -> {
            shelfFilter = shelf;
            refreshLibrary();
            parentDialog.dismiss();
        });
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(46), 1f));

        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextSize(20);
        more.setTextColor(themeSecondaryText());
        more.setGravity(Gravity.CENTER);
        more.setContentDescription("Shelf options");
        more.setBackground(roundRect(themeCardSurface(), dp(15), dp(1), themeStroke()));
        more.setOnClickListener(v -> {
            parentDialog.dismiss();
            showShelfManageDialog(shelf);
        });
        row.addView(more, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private void showShelfManageDialog(String shelf) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("Shelf options", shelf, dialog);
        sheet.addView(premiumChoiceRow("Rename shelf", "Keep all books in this shelf", false, () -> {
            dialog.dismiss();
            showRenameShelfDialog(shelf);
        }));

        TextView delete = new TextView(this);
        delete.setText("⌫   Delete shelf");
        delete.setTextSize(13f);
        delete.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        delete.setTextColor(Color.rgb(205, 63, 63));
        delete.setGravity(Gravity.CENTER_VERTICAL);
        delete.setPadding(dp(14), 0, dp(14), 0);
        delete.setBackground(roundRect(isBlackAppTheme() ? Color.rgb(55, 35, 38) : Color.rgb(255, 247, 247), dp(15), dp(1), Color.rgb(222, 150, 150)));
        delete.setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeleteShelf(shelf);
        });
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        deleteLp.topMargin = dp(8);
        sheet.addView(delete, deleteLp);
        presentBottomSheet(dialog, sheet, 0.62f);
    }

    private void showRenameShelfDialog(String oldName) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("Rename shelf", "Books stay in the shelf", dialog);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(oldName);
        input.setSelection(input.length());
        input.setTextSize(14f);
        input.setTextColor(themePrimaryText());
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(roundRect(themeControlSurface(), dp(16), dp(1), themeStroke()));
        sheet.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = filterChoice("Cancel", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        TextView save = filterChoice("Rename", true);
        save.setTextColor(Color.WHITE);
        save.setBackground(roundRect(themeAccent(), dp(17), 0, 0));
        save.setOnClickListener(v -> {
            String next = input.getText().toString().trim();
            if (!LibraryShelfStore.renameShelf(prefs, oldName, next)) {
                Toast.makeText(this, "Use a new, unique shelf name", Toast.LENGTH_SHORT).show();
                return;
            }
            if (oldName.equals(shelfFilter)) shelfFilter = next;
            dialog.dismiss();
            refreshLibrary();
            maybeAutoGoogleSync();
            showShelvesDialog();
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(94), dp(40)); cancelLp.rightMargin = dp(8);
        actions.addView(cancel, cancelLp);
        actions.addView(save, new LinearLayout.LayoutParams(dp(104), dp(40)));
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)); actionLp.topMargin = dp(9);
        sheet.addView(actions, actionLp);
        presentBottomSheet(dialog, sheet, 0.62f);
        input.requestFocus();
    }

    private void confirmDeleteShelf(String shelf) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("Delete shelf?", shelf, dialog);
        TextView message = new TextView(this);
        message.setText("Only this shelf will be deleted. The books inside it will stay safely in your Library.");
        message.setTextSize(12.5f);
        message.setTextColor(themeSecondaryText());
        message.setLineSpacing(dp(2), 1.12f);
        message.setPadding(dp(12), dp(12), dp(12), dp(12));
        message.setBackground(roundRect(themeControlSurface(), dp(15), dp(1), themeStroke()));
        sheet.addView(message);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = filterChoice("Cancel", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        TextView remove = filterChoice("Delete shelf", true);
        remove.setTextColor(Color.WHITE);
        remove.setBackground(roundRect(Color.rgb(205, 63, 63), dp(17), 0, 0));
        remove.setOnClickListener(v -> {
            if (LibraryShelfStore.deleteShelf(prefs, shelf)) {
                if (shelf.equals(shelfFilter)) shelfFilter = "";
                refreshLibrary();
                maybeAutoGoogleSync();
            }
            dialog.dismiss();
            showShelvesDialog();
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(96), dp(40)); cancelLp.rightMargin = dp(8);
        actions.addView(cancel, cancelLp);
        actions.addView(remove, new LinearLayout.LayoutParams(dp(118), dp(40)));
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)); actionLp.topMargin = dp(10);
        sheet.addView(actions, actionLp);
        presentBottomSheet(dialog, sheet, 0.64f);
    }

    private View premiumChoiceRow(String titleText, String subtitleText, boolean selected, Runnable action) {''',
)

# ---------- Build workflows ----------
for path in [".github/workflows/build-apk.yml", ".github/workflows/build-production.yml"]:
    text = read(path)
    text = text.replace("v2.17.1", "v2.17.2").replace("v41", "v42")
    text = text.replace("versionCode 41", "versionCode 42").replace("versionName '2.17.1'", "versionName '2.17.2'")
    text = text.replace("versionCode='41'", "versionCode='42'").replace("versionName='2.17.1'", "versionName='2.17.2'")
    write(path, text)

replace_once(
    ".github/workflows/build-apk.yml",
    "          test -s app/src/main/java/com/whisper/wowreader/SplashActivity.java\n",
    "          test -s app/src/main/java/com/whisper/wowreader/SplashActivity.java\n"
    "          test -s app/src/main/java/com/whisper/wowreader/ReadingCalendarActivity.java\n"
    "          test -s app/src/main/java/com/whisper/wowreader/ReadingDayActivity.java\n"
    "          test -s app/src/main/java/com/whisper/wowreader/ReadingMemoryActivity.java\n"
    "          test -s app/src/main/java/com/whisper/wowreader/MyanmarCalendarBridge.java\n",
)
replace_once(
    ".github/workflows/build-apk.yml",
    "          grep -q \"versionName '2.17.2'\" app/build.gradle\n",
    "          grep -q \"versionName '2.17.2'\" app/build.gradle\n"
    "          grep -q \"myanmar-calendar:1.1.1.RELEASE\" app/build.gradle\n"
    "          grep -q \"ReadingCalendarActivity.class\" app/src/main/java/com/whisper/wowreader/MainActivity.java\n"
    "          grep -q \"renameShelf\" app/src/main/java/com/whisper/wowreader/LibraryShelfStore.java\n"
    "          grep -q \"deleteShelf\" app/src/main/java/com/whisper/wowreader/LibraryShelfStore.java\n",
)

# ---------- Docs ----------
readme = read("README.md")
readme = readme.replace("Lab: **WoW Reader v2.17.1** (`versionCode 41`)", "Lab: **WoW Reader v2.17.2** (`versionCode 42`)")
readme = readme.replace("Lab v41 is based on the approved production v40 source and is the place for future experiments and fixes before promotion.", "Lab v42 is based on the approved production v40 line and adds a test-only Myanmar Reading Calendar / Reading Memory experience plus custom shelf rename/delete.")
readme = readme.replace("- Reading Statistics / streaks\n", "- Reading Statistics / streaks\n- Myanmar Reading Calendar with book covers by reading day\n- Daily Reading Notes and per-book Reading Memory\n")
readme = readme.replace("- Smart Library / shelves\n", "- Smart Library / shelves, including custom shelf rename/delete\n")
write("README.md", readme)

handoff = read("NEXT_CHAT_HANDOFF.md")
handoff = handoff.replace("Lab: **v2.17.1**, `versionCode 41`", "Lab: **v2.17.2**, `versionCode 42`")
handoff = handoff.replace("Lab v41 is based on the approved production v40 source.", "Lab v42 is based on the approved production v40 line. v42 adds a Myanmar Reading Calendar / Reading Memory prototype and custom shelf rename/delete for real-device testing.")
handoff = handoff.replace("- Reading Statistics / streaks\n", "- Reading Statistics / streaks\n- Myanmar Reading Calendar + daily/book Reading Memory (v42 Lab test)\n")
handoff = handoff.replace("- Smart Library / shelves\n", "- Smart Library / shelves with custom shelf rename/delete\n")
write("NEXT_CHAT_HANDOFF.md", handoff)

print("v42 reading calendar patch prepared")
