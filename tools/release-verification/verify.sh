#!/usr/bin/env bash
set -euo pipefail
mkdir -p verification
PKG=com.whisper.wowreader
adb root
adb wait-for-device
check_launch() {
  adb logcat -c
  adb shell am start -W -n "$PKG/.MainActivity" > verification/launch.txt
  sleep 4
  adb shell pidof "$PKG"
  adb logcat -d > verification/logcat.txt
  if grep -A20 'FATAL EXCEPTION' verification/logcat.txt | grep -F "$PKG"; then exit 1; fi
}
for old in v48 v51; do
  adb uninstall "$PKG" >/dev/null 2>&1 || true
  adb install "signed-verification/$old.apk"
  check_launch
  adb shell am force-stop "$PKG"
  adb shell mkdir -p "/data/user/0/$PKG/files/library" "/data/user/0/$PKG/files/reader_fonts" "/data/user/0/$PKG/shared_prefs"
  # Seed data in the old production-signed installation, then use Android's normal update path.
  # Some clean launches do not create wow_reader.xml until a preference is first written.
  # Try the pull directly so this works even on old Android shells where `test -f` is unreliable.
  if adb pull "/data/user/0/$PKG/shared_prefs/wow_reader.xml" verification/old-prefs.xml; then
    :
  else
    printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" '<map />' > verification/old-prefs.xml
  fi
  python3 tools/release-verification/seed.py
  APP_UID=$(adb shell stat -c %u "/data/user/0/$PKG" | tr -d '\r')
  adb push verification/seed-prefs.xml "/data/user/0/$PKG/shared_prefs/wow_reader.xml"
  adb push verification/update-book.epub "/data/user/0/$PKG/files/library/update-book.epub"
  adb push app/src/main/assets/fonts/pyidaungsu_native.ttf "/data/user/0/$PKG/files/reader_fonts/update-font.ttf"
  adb shell chown -R "$APP_UID:$APP_UID" "/data/user/0/$PKG/shared_prefs" "/data/user/0/$PKG/files"
  adb shell restorecon -RF "/data/user/0/$PKG"
  adb install -r signed-verification/v52.apk
  check_launch
  adb shell am force-stop "$PKG"
  adb pull "/data/user/0/$PKG/shared_prefs/wow_reader.xml" verification/updated-prefs.xml
  adb pull "/data/user/0/$PKG/files/library/update-book.epub" verification/updated-book.epub
  adb pull "/data/user/0/$PKG/files/reader_fonts/update-font.ttf" verification/updated-font.ttf
  python3 tools/release-verification/seed.py verify
  echo "$old original-signed -> v52: update, data and launch PASS" | tee -a verification/install-report.txt
done
adb uninstall "$PKG"
adb install signed-verification/v52.apk
check_launch
adb shell test -d "/data/user/0/$PKG/files/library"
adb shell test ! -e "/data/user/0/$PKG/files/library/update-book.epub"
echo 'clean -> same signed v52 APK: PASS' | tee -a verification/install-report.txt
adb install signed-verification/test.apk
timeout 420 adb shell am instrument -w "$PKG.test/com.whisper.wowreader.RegressionInstrumentation" | tee verification/signed-regression.txt
adb logcat -d > verification/signed-logcat.txt
grep -q REGRESSION_PASS verification/signed-regression.txt
