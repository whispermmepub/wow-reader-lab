# WoW Reader — Store Publication Status

## v81 / 2.21.15: Play upload succeeded (owner-reported)

The owner accepted the v80 / 2.21.14 test APK and authorized pushing the approved original-app changes to `whispermmepub/wow-reader-lab` `main` and preparing the next production-signed Google Play bundle. **The owner subsequently confirmed that the v81 / 2.21.15 AAB upload to Play Store succeeded.** This is the owner's report, not an independently inspected Play Console result. No Play upload, review submission or rollout was performed by this workflow. This follow-up updates release documentation only; no app code/version or release artifact was changed. `wow-reader-plus` is outside this task and was not changed.

Upload success does **not** establish review approval, publication, rollout completion or live availability for v81. The latest explicitly owner-confirmed live release remains **v77 / 2.21.11**, confirmed on 2026-10-20. [Google Play listing](https://play.google.com/store/apps/details?id=com.whisper.wowreader). No Play Console tracks, review status, rollout percentage or regional availability were independently inspected. Treat versionCode **81** as consumed; the next release must use **82 or higher**, after checking all Play tracks. Do not repeat the successful upload as part of this documentation task.

## Exact source state

- Original pre-v78 baseline: `3e06e9f1fcd82be9d9e6c213c46c566bb98b117d` (local and remote `main` matched before work).
- Release app/source commit: [`fe720ce159d4f8661ac2e4bf4d8a0357b3eb0af7`](https://github.com/whispermmepub/wow-reader-lab/commit/fe720ce159d4f8661ac2e4bf4d8a0357b3eb0af7).
- Test-only follow-up: [`32cb58462fdd6706b4a43fa272302bf6c467a820`](https://github.com/whispermmepub/wow-reader-lab/commit/32cb58462fdd6706b4a43fa272302bf6c467a820). The legacy reader harness now waits for the authoritative map before checking end promotion. Its first run exposed a progressive-readiness timing race; no assertions were removed and no app runtime changes were needed.
- The subsequent release-doc commit changes README, this status and NEXT_CHAT_HANDOFF only. Use `git log -3` for that commit's own hash; a commit cannot embed its own hash.
- App version: **versionCode 81 / versionName 2.21.15**. Runtime DEX and all **124 reader content assets** are byte-identical to the owner-approved v80 APK. Generated ART optimization profiles are excluded from asset comparison. The release is not claimed to reproduce the entire v80 APK byte-for-byte because its manifest/version/signature metadata differ.

The approved v78–v80 work includes memory-aware renderer serialization/recovery, sampled cover decoding, progressive pagination, resource-aware readiness, root-confined cancellable disk warmup and privacy-safe chapter timing. No schema or destructive migration change was introduced. **No book/file-size rejection hard cap was added.** Renderer thresholds and warmup budgets limit speculative work, not reading/import eligibility.

## Final files and identity

- **Play upload AAB:** `/workspace/wow-reader-lab/build/v81-production/WoW-Reader-v2.21.15-v81-PlayStore-production.aab`
- **AAB size:** 15,492,311 bytes.
- **AAB SHA-256:** `81529f862c87466c43c301213cff5121df52bb190cd0663d305911da555a6688`
- Optional signed direct-install APK: `/workspace/wow-reader-lab/build/v81-production/WoW-Reader-v2.21.15-v81-production.apk`
- APK SHA-256: `233b181b9410f9f108bc01d880f54250c8849fac481187f49b9daf1fb17383f0`
- Package: **`com.whisper.wowreader`**; minSdk **24**; targetSdk/compileSdk **36**; non-debuggable release.
- Production certificate SHA-256: `29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5` — verified against the accepted v80 APK and documented production identity. Do not regenerate signing keys or change Firebase/OAuth identity.

Keep the filename's **`.aab` extension** when downloading and uploading. The AAB is for Google Play, not direct phone installation; use the signed APK for a direct-install check if needed. No APK/AAB, build outputs, keystore, password or private helper is committed to Git.

minSdk 24 preserves the Play automatic-protection requirement accepted for v77. Android 6.0 / API 23 remains intentionally unsupported.

## Validation completed for v81

| Check | Exact result |
|---|---|
| Contract runner | All 16 contract groups passed |
| Debug host/Robolectric suite | 695 tests; 0 failures, 0 errors, 0 skipped |
| Release host/Robolectric suite | 695 tests; 0 failures, 0 errors, 0 skipped |
| Release lint | 0 fatal/errors; 181 warnings |
| Clean Gradle build | `testDebugUnitTest`, `testReleaseUnitTest`, `lintRelease`, `assembleRelease`, `bundleRelease`, `compileDebugAndroidTestJavaWithJavac` succeeded; 104 tasks, 102 executed, 2 up-to-date |
| Instrumentation | Compiled only, not run on a phone/emulator |
| Desktop Chromium reader / publisher layout | 15 / 15 cases passed |
| Progressive / resource readiness | 13 / 8 cases passed |
| Chapter-stage comparison | 6 cases, 3 samples per candidate; 0 failures, identical final maps/image geometry; not a universal speed claim |
| Large-chapter Chromium stress | 6 cases passed; separate synthetic ZIP fixtures of 12 MiB and 32 MiB checked on host, not opened on Android |
| Synthetic restore benchmark | 120 objects, 3 runs, bounded concurrency 3; object count and scratch cleanup passed; not live Google Drive |
| AAB integrity | bundletool 1.18.3 validation, ZIP CRC, manifest identity and JAR signature passed |
| Payload parity | APK/AAB DEX, content assets and libraries match; v81/v80 DEX and 124 content assets match |
| APK signature/alignment | Production signer matches v80; 16-KiB zipalign check passed |
| Repository hygiene | Explicit source/test/tool/docs staging; generated Python caches ignored; staged path and secret-pattern checks plus `git diff --check` passed |

The first legacy Chromium run failed because first-page-ready was mistaken for full-map-ready; the corrected harness passed all 15 cases. A local verification-helper assertion also expected `sdkVersion` instead of aapt2's actual `minSdkVersion`; the manifest values were correct and independently checked with bundletool. Neither fix changed the approved app runtime.

The AAB was reordered to place `META-INF/MANIFEST.MF` and signature metadata first for streaming JAR verifiers. Every uncompressed entry's SHA-256 was preserved, then bundletool, signature and payload checks were rerun on the final deliverable. There are no unsigned-entry/streaming-order warnings. Standard JAR warnings about the Android self-signed certificate chain, no timestamp and unprotected ZIP POSIX attributes remain; signer expiry is 2054-01-18. Existing Firebase Kotlin metadata 2.3.0 versus tooling-expected 2.1.0 diagnostics appeared during lint analysis, but Gradle and lint completed successfully; this is not a warning-free tooling claim.

Evidence is beside the AAB: `VERIFICATION.json`, `SHA256SUMS.txt`, `AAB-MANIFEST.xml`, `AAB-VALIDATION.txt`, `AAB-SIGNATURE.txt`, `AAB-CERTIFICATE.txt`, APK verification reports and the seven host-harness JSON reports. Build/harness/verification logs and local helper are under `build/`, ignored by Git. See [handoff](../NEXT_CHAT_HANDOFF.md) for continuation commands.

## Update and new-install limits — owner QA remains necessary

Unchanged package/signing identity, higher versionCode and preserved data code support the existing in-place-update and fresh-install paths **in principle**, not as physical-phone proof. Do not uninstall or clear app data when checking an update. Before Production, test live **v77 → v81** and, if applicable, **v80 → v81**; check books/files, metadata/titles, progress, notes/highlights, shelves, calendar/history, settings and account/sync. Separately test fresh installation, Google sign-in and Drive restore/cancellation/retry.

**Not tested here:** physical Vivo Y30, the owner's original failing EPUB (not available), actual fresh-install or in-place phone installation, live Google account/Drive behavior, or any all-device guarantee. No emulator/device instrumentation was run.

WebView still parses/styles a whole spine chapter; progressive readiness does not bound initial DOM/layout work or native image memory. Previous-end/nonzero resume waits for the authoritative map. At a still-scanning frontier, a turn can return pending; tap again when more pages are available. Disk warmup is bounded I/O, not an image decode cache. Existing preview-only limits and whole-chapter search/footnote parsing remain. Host timings are not Vivo Y30 latency, Android renderer PSS/jank, or a complete APK navigation measurement.
