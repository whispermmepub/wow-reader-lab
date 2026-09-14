# WoW Reader Lab

WoW Reader Lab is the Android development and stable-release repository for **WoW Reader**.

## Current official state

- **Version:** `2.19.1`
- **versionCode:** `61`
- **Package:** `com.whisper.wowreader`
- **Minimum Android:** 6.0 / API 23
- **Target SDK:** 36
- **Java:** 17
- **Official stable branch:** `stable/v61`
- **Equivalent file-share branch:** `stable/v61-file-share`
- **Original clean v61 baseline commit:** `58f39edafe7b9a1bab9d0e4bbe39d31930056c05`

The trusted v61 release is the approved v60 baseline plus **actual EPUB/PDF File Share** and the v61 version bump.

## Start here for continuation

For a new chat or future release/update, read these first:

- `NEXT_CHAT_HANDOFF.md` — complete current Android/iOS continuation context
- `docs/ANDROID_IOS_RELEASE_HANDOFF.md` — cross-platform release/update contract
- `docs/STORE_PUBLICATION_STATUS.md` — Google Play/App Store publication state
- `SIGNING.md` — public Android signing identity metadata and secret-variable names

`whispermmepub/wow-reader-lab` is the current source of truth. The separate `whispermmepub/wow-reader-app` repository still contains an older source line and should not be used for new work unless it is explicitly synchronized later.

## v61 File Share

WoW Reader can share the original EPUB or PDF file through the Android system share sheet.

Implementation uses:

- `Intent.ACTION_SEND`
- `EXTRA_STREAM`
- `ClipData`
- `FLAG_GRANT_READ_URI_PERMISSION`
- Android `FileProvider`
- EPUB MIME: `application/epub+zip`
- PDF MIME: `application/pdf`
- fallback MIME: `application/octet-stream`

This v61 release intentionally contains **no WoW Audio handoff/integration**.

## Stable feature set

- Offline EPUB/PDF reading
- Google sign-in with Firebase Authentication
- Private Google Drive `appDataFolder` backup / restore / auto sync
- Reading Statistics and streaks
- Myanmar Reading Calendar with book covers by reading day
- Daily Reading Notes and per-book Reading Memory
- Smart Library and custom shelves
- Custom shelf rename/delete
- Notes & Highlights Hub
- Per-book typography and custom fonts
- Myanmar / English dictionary support
- Smart Sync Merge
- Home / Library / Notes / Explore navigation
- Coming Soon / book-review feed
- Custom App Theme
- `Justify · Normal` and `Justify · Auto spacing`
- Fast chapter transitions with adjacent-chapter preloading
- Multi-book EPUB/PDF import
- EPUB footnote/endnote navigation fixes
- PDF continuous reading and import handling fixes
- Corrected highlight/note text mapping
- System-inset, scrolling, font-scaling and OEM compatibility fixes
- Actual EPUB/PDF File Share

## Update compatibility

Production updates must preserve:

- package `com.whisper.wowreader`
- the original WoW Reader production signing identity
- a monotonically increasing `versionCode`

Do not generate a replacement production signing key.

Existing library data, reading progress/history, shelves, notes/highlights, Reading Calendar data, SharedPreferences and Google/Firebase sync data must remain compatible with in-place updates.

## Play Store release

The current Play Store release line is **v2.19.1 / versionCode 61**. The Play Store artifact must be an Android App Bundle (`.aab`) built from the trusted v61 source and signed with the preserved production identity.

If Google Play App Signing is enabled while compatibility with previously sideloaded production-signed APKs is required, preserve the existing app-signing identity rather than allowing an unrelated new signing key.

## iOS handoff

A separate native SwiftUI iPhone/iPad handoff exists for version **2.19.1 (61)**. It contains the Xcode project, EPUB/PDF import/read foundation, local library, reading progress, sharing, icons and privacy manifest. It intentionally contains no Apple private signing credentials. See `docs/ANDROID_IOS_RELEASE_HANDOFF.md` for the exact iOS continuation contract.

## Development rule

1. Treat the current aligned `main` / `stable/v61` source as the source of truth.
2. Make new feature work on a separate branch.
3. Build APK/AAB and run regression checks before promotion.
4. Preserve existing reader, local data, Firebase and Drive behavior.
5. Decide explicitly which accepted Android changes also need an iOS port.
6. Do not reintroduce removed experiments unless explicitly requested.
7. Never commit private signing material or credentials.

## Community links

- Telegram books channel: https://t.me/TheBookR
- Discussion group: https://t.me/+rUiqzi2mdhNiNGZl
- Website: https://saroatsin.com
- Book reviews: https://whispermmepub.github.io/Review/

## Secrets

Never commit signing passwords, private keys, keystores, Telegram tokens, Firebase server credentials, Google service-account credentials, Apple private keys, or other secrets.
