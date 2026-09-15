# WoW Reader Lab

Official Android source repository for **WoW Reader**.

## Current Android release candidate

- **Version:** `2.19.3`
- **versionCode:** `63`
- **Package:** `com.whisper.wowreader`
- **Minimum Android:** 6.0 / API 23
- **Target SDK:** 36
- **Java:** 17
- **Current source:** `main`
- **Play Store release branch:** `release/v63-playstore`
- **Previous stable production baseline:** `stable/v61` (`2.19.1 / 61`)

The v63 source is the current Play Store update candidate. It has passed repository CI build, lint, package/version checks and production-signing verification. Real-device Closed Testing is still the final gate before Production promotion.

## Start here in a new chat

Read these files before changing anything:

- `NEXT_CHAT_HANDOFF.md` — exact current continuation and Play Store update instructions
- `docs/STORE_PUBLICATION_STATUS.md` — Google Play/App Store publication state
- `docs/ANDROID_IOS_RELEASE_HANDOFF.md` — cross-platform release contract
- `docs/V63_STABILITY_SCALABLE_STORAGE_PLAN.md` — v63 stability/scalability design background
- `SIGNING.md` — public signing identity metadata; no private secrets

`whispermmepub/wow-reader-lab` is the source of truth. Do not switch to the older `whispermmepub/wow-reader-app` line unless explicitly requested.

## What v63 fixes

- More accurate EPUB overall progress using readable-content weighting instead of equal chapter count
- Avoids TOC/cover/front-matter causing large progress jumps near the beginning
- SHA-256 content identity for idempotent EPUB/PDF imports from Telegram and other apps
- Scalable structured reader state in indexed SQLite (`ReaderStateDb`) with non-destructive legacy migration
- Local-first reader behavior: cloud sync no longer runs inside active reading sessions
- Reading Calendar/recap data queries optimized for larger libraries
- Finished-book share cards support more than 12 books by generating multiple pages
- Share-card image rendering moved off the UI thread
- API 23-safe SQLite update logic
- Bounded EPUB progress analysis to reduce memory risk with unusually large chapter files

The original v63 design document discussed Room as an option. The shipped v63 candidate deliberately uses Android `SQLiteOpenHelper`/SQLite instead, reducing migration/dependency risk while keeping indexed structured storage.

## Stable feature set preserved

- Offline EPUB/PDF reading
- Firebase Google sign-in
- Private Google Drive `appDataFolder` backup/restore/sync
- Reading Statistics, streaks, Reading Calendar and Reading Memory
- Notes/highlights
- Smart Library and custom shelves
- Per-book typography and custom fonts
- Myanmar/English dictionary support
- EPUB footnote/endnote handling
- PDF continuous reading/import handling
- Multi-book import
- EPUB/PDF Android File Share

Existing user books, progress, notes, shelves, calendar/history and settings must remain compatible with in-place updates.

## Production signing and Google identity

Do not generate a replacement production key.

Production / Play App Signing SHA-1:

`21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`

The Play Console App signing key certificate now matches this original production identity. Firebase/Google configuration for `com.whisper.wowreader` is aligned with it.

Private keystore/password material must never be committed.

## Verified v63 release artifacts

- `WoW-Reader-v2.19.3-v63-PlayStore-production.aab`
  - SHA-256: `3587777e5f9586ca112bd92cbee65e74c82b0b29f615167da075f75d584e35ae`
- `WoW-Reader-v2.19.3-v63-production-signed.apk`
  - SHA-256: `2ca3a1b4487e98c1ae2e0969cb571457dee032b2daff4ec05656d2ab07a3db25`

These artifacts were built from app source commit `21894db4f10a5ea588e99b4b07dbaba181ecd70f` and verified with the original production signer. Subsequent repository cleanup/documentation commits do not alter the v63 app source.

## Play Store update note

> Improved reading progress accuracy, fixed duplicate EPUB imports, optimized Reading Calendar and sharing performance, improved stability with Google account sync, and enhanced library performance for large book collections.

## Release rule

1. Keep package `com.whisper.wowreader` unchanged.
2. Keep the original production signing identity unchanged.
3. Always check every Play track and use a `versionCode` higher than all existing tracks.
4. Preserve user data; never introduce destructive migration/reset without explicit approval.
5. Build/lint/verify APK+AAB before upload.
6. Test v62/v61 → v63 update, Google sign-in/sync, long reading sessions, duplicate imports and large recaps in Closed Testing.
7. Promote to Production only after device testing passes.

## Community links

- Telegram books channel: https://t.me/TheBookR
- Discussion group: https://t.me/+rUiqzi2mdhNiNGZl
- Website: https://saroatsin.com
- Book reviews: https://whispermmepub.github.io/Review/

## Secrets

Never commit signing passwords, keystores, private certificates/keys, recovery files, Telegram tokens, service-account credentials, Apple private keys or other secrets.
