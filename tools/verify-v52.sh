#!/usr/bin/env bash
set -euo pipefail
mkdir -p verification
adb install app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
adb shell am instrument -w com.whisper.wowreader.test/com.whisper.wowreader.RegressionInstrumentation | tee verification/regression.txt
adb logcat -d > verification/logcat.txt
grep -q REGRESSION_PASS verification/regression.txt
