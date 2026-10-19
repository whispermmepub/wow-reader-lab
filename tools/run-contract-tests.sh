#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d /tmp/wow-reader-contract-tests.XXXXXX)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/LibraryQuerySpec.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/DriveObjectNamer.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/SyncBatchPolicy.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/WholeBookPageModel.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/DriveAppPropertyPolicy.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/CoverSearchPlanner.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/DriveRestorePlanner.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/DriveDeletionGuard.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/HomeContinuePolicy.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/ReaderLocationPolicy.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/ReadingReminderPolicy.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/PagePreviewPolicy.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/PdfRenderPolicy.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/EpubOpeningPolicy.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/EpubUtil.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/ReaderAppearancePolicy.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/ReaderTypographyCss.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/ChapterTransitionPolicy.java" \
  "$ROOT/app/src/main/java/com/whisper/wowreader/ResumableUploadPolicy.java" \
  "$ROOT"/tools/contract-tests/com/whisper/wowreader/*.java
for source in "$ROOT"/tools/contract-tests/com/whisper/wowreader/*Test.java; do
  t="$(basename "$source" .java)"
  java -cp "$OUT" "com.whisper.wowreader.$t"
done
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
source = (root / 'app/src/main/java/com/whisper/wowreader/MainActivity.java').read_text()
assert '{"discussion", "Discussion", "Reader community", "https://t.me/+oKgpGSA_lKc4YTU1"}' in source
assert '{"telegram", "Telegram", "New books", "https://t.me/TheBookR"}' in source
assert 'https://t.me/+rUiqzi2mdhNiNGZl' not in source
print('COMMUNITY_LINK_CONTRACT_TEST_PASS')
PY
