#!/usr/bin/env bash
set -euo pipefail

PKG=com.whisper.wowreader
APK_DIR="$GITHUB_WORKSPACE/update-apks"
REPORT_DIR="$GITHUB_WORKSPACE/verification-v54"
mkdir -p "$REPORT_DIR"
exec > >(tee "$REPORT_DIR/verification.txt") 2>&1

adb wait-for-device
adb shell input keyevent 82 || true

version_code() {
  adb shell dumpsys package "$PKG" | tr -d '\r' | sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -n1
}

uninstall_app() {
  adb uninstall "$PKG" >/dev/null 2>&1 || true
  adb uninstall "$PKG.test" >/dev/null 2>&1 || true
}

launch_app() {
  adb shell am force-stop "$PKG" || true
  adb shell am start -W -n "$PKG/.MainActivity" | tee "$REPORT_DIR/launch.txt"
  grep -Eq 'Status: (ok|OK)' "$REPORT_DIR/launch.txt"
}

seed_private_sentinel() {
  local label="$1"
  adb shell "run-as $PKG sh -c 'mkdir -p files; echo $label > files/stable-sentinel.txt'"
  adb shell "run-as $PKG cat files/stable-sentinel.txt" | tr -d '\r' | grep -qx "$label"
}

verify_private_sentinel() {
  local label="$1"
  adb shell "run-as $PKG cat files/stable-sentinel.txt" | tr -d '\r' | grep -qx "$label"
}

verify_update() {
  local from="$1"
  local expected="$2"
  local old_apk="$APK_DIR/${from}-debug.apk"
  echo "=== UPDATE $from -> v54 ==="
  uninstall_app
  adb install "$old_apk"
  test "$(version_code)" = "$expected"
  launch_app
  seed_private_sentinel "$from"
  adb install -r "$APK_DIR/v54-debug.apk"
  test "$(version_code)" = "54"
  verify_private_sentinel "$from"
  launch_app
  verify_private_sentinel "$from"
  echo "$from -> v54: PASS"
}

verify_fresh() {
  echo '=== FRESH v54 ==='
  uninstall_app
  adb install "$APK_DIR/v54-debug.apk"
  test "$(version_code)" = "54"
  launch_app
  seed_private_sentinel fresh-v54
  echo 'fresh v54: PASS'
}

verify_instrumentation() {
  echo '=== v54 INSTRUMENTATION ==='
  adb install -r "$APK_DIR/v54-androidTest.apk"
  local inst
  inst=$(adb shell pm list instrumentation | tr -d '\r' | grep "target=$PKG" | head -n1 | sed -E 's/^instrumentation:([^ ]+).*/\1/')
  test -n "$inst"
  echo "instrumentation=$inst"
  adb shell am instrument -w "$inst" | tee "$REPORT_DIR/instrumentation.txt"
  grep -q 'REGRESSION_PASS' "$REPORT_DIR/instrumentation.txt"
  ! grep -q 'REGRESSION_FAILED' "$REPORT_DIR/instrumentation.txt"
  echo 'instrumentation: PASS'
}

verify_update v51 51
verify_update v52 52
verify_update v53 53
verify_fresh
verify_instrumentation

echo 'STABLE_V54_INSTALL_REGRESSION_PASS'
