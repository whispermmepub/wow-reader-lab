#!/usr/bin/env bash
set -euo pipefail
PKG=com.whisper.wowreader
OLD=/tmp/wow-v40.apk
NEW=/tmp/wow-v49.apk

echo '=== v40 -> v49 update ==='
adb install "$OLD"
adb shell run-as "$PKG" mkdir -p files/library shared_prefs
printf 'v40-library-preserved\n' | adb shell "run-as $PKG sh -c 'cat > files/library/__v40_update_sentinel.txt'"
cat > /tmp/wow_reader.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name="percent_update-test.epub" value="37" />
    <string name="library_shelves_json">{&quot;Favorites&quot;:[&quot;update-test.epub&quot;]}</string>
    <string name="reading_stats_day_notes_json">{&quot;2026-09-05&quot;:&quot;v40 update sentinel&quot;}</string>
</map>
XML
cat /tmp/wow_reader.xml | adb shell "run-as $PKG sh -c 'cat > shared_prefs/wow_reader.xml'"
adb install -r "$NEW"
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 5
adb shell dumpsys package "$PKG" | grep -q 'versionCode=49'
adb shell run-as "$PKG" test -f files/library/__v40_update_sentinel.txt
adb shell run-as "$PKG" grep -q 'value="37"' shared_prefs/wow_reader.xml
adb shell run-as "$PKG" grep -q 'library_shelves_json' shared_prefs/wow_reader.xml
adb shell run-as "$PKG" grep -q 'reading_stats_day_notes_json' shared_prefs/wow_reader.xml
adb shell pidof "$PKG" >/dev/null
if adb logcat -d | grep -A8 -B2 'FATAL EXCEPTION' | grep -q "$PKG"; then
  echo 'WoW Reader fatal exception after v40 update' >&2
  exit 1
fi

echo '=== clean -> v49 ==='
adb uninstall "$PKG"
adb install "$NEW"
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 5
adb shell dumpsys package "$PKG" | grep -q 'versionCode=49'
adb shell run-as "$PKG" test -d files/library
adb shell run-as "$PKG" test -d files/cover_cache
if adb shell run-as "$PKG" test -f files/library/__v40_update_sentinel.txt; then
  echo 'Fresh install unexpectedly retained update sentinel' >&2
  exit 1
fi
adb shell pidof "$PKG" >/dev/null
if adb logcat -d | grep -A8 -B2 'FATAL EXCEPTION' | grep -q "$PKG"; then
  echo 'WoW Reader fatal exception after fresh install' >&2
  exit 1
fi

cat > compatibility-report.txt <<'REPORT'
WoW Reader v2.17.9 / versionCode 49
Package: com.whisper.wowreader
v2.17.0/v40 -> v49 in-place update: PASS
Existing library sentinel: PASS
Existing reading progress/shelf/calendar prefs: PASS
Clean v49 fresh install: PASS
New library/cache initialization: PASS
Launch fatal-exception check: PASS
REPORT
