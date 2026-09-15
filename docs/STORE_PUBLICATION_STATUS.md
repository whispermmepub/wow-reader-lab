# WoW Reader — Store Publication Status

This file records the current publication state so source development and store submission are not confused in future chats.

## Google Play

Current Android Play update candidate:

- versionName: `2.19.3`
- versionCode: `63`
- package: `com.whisper.wowreader`
- release AAB: `WoW-Reader-v2.19.3-v63-PlayStore-production.aab`
- AAB SHA-256: `3587777e5f9586ca112bd92cbee65e74c82b0b29f615167da075f75d584e35ae`
- category: Books & Reference
- support email: `aungsoemoe.mm0@gmail.com`
- privacy policy: `https://wowreaderapp.blogspot.com/p/privacy-policy.html`
- tester opt-in: `https://play.google.com/apps/testing/com.whisper.wowreader`

### Signing / Google identity

Play Console App integrity now reports the original production App Signing SHA-1:

`21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`

This matches the production Firebase/OAuth identity used by `com.whisper.wowreader`.

Historical note: a previous Play-distributed signing mismatch was the leading cause of Play-only Google Sign-In `[16] Account reauth failed`. Do not reintroduce that mismatch and do not regenerate the app-signing identity.

### Current release state

v63 has passed repository build/lint/package/version checks and local production-signature verification. It is a **Closed Testing / Play Store release candidate**, not yet treated as fully runtime-approved Production until device tests pass.

Required runtime checks before Production:

- in-place update keeps books/progress/shelves/notes/calendar/settings
- Google Sign-In from Play-installed build works without `[16] Account reauth failed`
- 30+ minute reading session with Google connected does not return unexpectedly to Home
- repeated Telegram/open-with import of the same EPUB does not create duplicates
- EPUB front matter/TOC does not falsely show large overall progress
- >12 and >50 finished-book recap sharing includes all books and remains responsive
- Google Drive backup/restore/manual sync/auto sync work
- EPUB/PDF sharing remains functional

### Play Console upload rule

Before uploading, inspect all Play tracks. If `versionCode 63` is already consumed anywhere, do not reuse it; increment above the highest existing track and rebuild/sign/verify.

### Play Store release note

`Improved reading progress accuracy, fixed duplicate EPUB imports, optimized Reading Calendar and sharing performance, improved stability with Google account sync, and enhanced library performance for large book collections.`

### Account / Data Safety

The Android app uses Google sign-in/Firebase Authentication and Google Drive appDataFolder sync. Play Console Data Safety and account-deletion answers must match the app's actual behavior at submission time. Keep a public privacy policy and a valid public account-deletion page/flow where Play policy requires it.

Do not commit private tester email lists, signing credentials or account secrets to this repository.

## Previous Android baseline

Previous stable source branch: `stable/v61` (`2.19.1 / 61`). Keep its verified artifacts as rollback/reference material; do not overwrite or lose the original signing recovery kit.

## Apple App Store

The existing native SwiftUI iOS handoff remains around marketing version `2.19.1`, build `61`, bundle ID `com.whisper.wowreader`, iOS 16.0+, iPhone + iPad.

It is a separate port foundation and does not automatically include the Android v63 fixes. Final iOS build/archive/codesign/upload still requires macOS/Xcode and the publisher's Apple Developer signing credentials.
