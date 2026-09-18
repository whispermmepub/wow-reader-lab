#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="/tmp/wow-reader-contract-tests"
rm -rf "$OUT" && mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/LibraryQuerySpec.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/DriveObjectNamer.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/SyncBatchPolicy.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/WholeBookPageModel.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/DriveAppPropertyPolicy.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/CoverSearchPlanner.java" \
  "$ROOT"/tools/contract-tests/com/whisper/wowreader/*.java
for t in LibraryQuerySpecTest IncrementalSyncContractTest WholeBookPageModelTest DriveAppPropertyPolicyTest CoverSearchPlannerTest; do
  java -cp "$OUT" "com.whisper.wowreader.$t"
done
