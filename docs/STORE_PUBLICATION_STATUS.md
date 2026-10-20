# WoW Reader — Store Publication Status

## Google Play

- **Last owner-confirmed live release:** versionName `2.20.0`, versionCode `64`
- **Owner-accepted update:** versionName `2.21.9`, versionCode `75`
- **Package:** `com.whisper.wowreader`
- **Authorization:** publish the accepted v75 source to GitHub and prepare a matching production-signed AAB. **Do not upload to Google Play. The owner will upload personally.**

The owner must confirm that versionCode `75` is unused across **all Play tracks** before uploading. Track availability has not been checked. Source publication and build validation do not establish Play acceptance or a live release.

## What changed

This is the cumulative update from the last owner-confirmed live v64 through v75. It includes smoother chapter transitions, preserved original EPUB formatting with global Original EPUB/Reader typography settings, larger adaptive previews, reminder/home improvements, and Drive restore recovery for ghost entries, hash titles and deletion handling. Later improvements add at-most-three concurrent restore transfers, canonical titles across refresh and reading-history screens, bounded larger EPUB previews, retained ready preview page maps, and Drive stage/progress/cancel/retry controls.

Do not describe this as recovery of unrecoverable metadata, unlimited EPUB preview support, instant cold chapter pagination, or measured all-phone performance.

## Validation and owner checks

The accepted APK evidence records 509 passing host tests with zero failures/errors/skips, passing contracts and desktop Chromium reader suites, release build and instrumentation compilation, and lint with 177 warnings and zero fatal/errors. Instrumentation compilation is not a device test. The owner accepted the test update; physical-device latency, live Google-account restore and the original failing backup were not independently validated in the build environment.

Before release, the owner should:

1. Confirm versionCode `75` availability across every Play track.
2. Upload the matching signed `WoW-Reader-v2.21.9-v75-PlayStore-production.aab` personally. Artifacts and verification are outside Git in `/workspace/wow-reader-v75-update/`.
3. Verify an in-place update from the live install without uninstalling or clearing data.
4. Check Google sign-in, Drive restore/cancellation/retry, books and titles, progress, notes, shelves, history, reminders, chapter transitions, previews and global typography on representative devices.
5. Review Play Console validation and rollout settings before publishing.

Suggested release notes: `docs/WHATS_NEW_v75.txt`.

## Identity

- **Package:** `com.whisper.wowreader`
- **Category:** Books & Reference
- **Minimum Android:** 6.0 / API 23
- **Target SDK:** 36
- **Production SHA-1:** `21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`

Keep the original production signing identity and Firebase/OAuth registration unchanged. Upload-key acceptance must be checked separately if Play rejects the signature. Do not commit signing material or APK/AAB binaries.
