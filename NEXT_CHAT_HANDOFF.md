# WoW Reader Lab — Next Chat Handoff

Read this before modifying the project in a new chat.

## Working rule

- Experimental/testing repo: `whispermmepub/wow-reader-lab`
- Production/stable repo: `whispermmepub/wow-reader-app`
- Test new changes in Lab first.
- Do not promote to production until the user explicitly approves a real-device-tested Lab build.
- Preserve existing working behavior and avoid broad rewrites.

## Current versions

- Production: **v2.17.0**, `versionCode 40`
- Lab: **v2.17.1**, `versionCode 41`
- Package: `com.whisper.wowreader`
- `minSdk 23`, `targetSdk 36`, Java 17

Lab v41 is based on the approved production v40 source.

## Important product decision

The Telegram Auto Library experiment was intentionally removed from Lab. It is not part of the current app or current development plan. Do not reintroduce `AutoLibrarySync`, the Cloudflare Telegram auto-library backend, polling hooks, endpoint config, or related CI checks unless the user explicitly requests that feature again.

## Stable features to preserve

- Offline EPUB/PDF reading
- Google account sign-in and private Google Drive appDataFolder backup/restore/auto sync
- Reading Statistics / streaks
- Smart Library / shelves
- Notes & Highlights Hub
- Per-book typography
- Smart Sync Merge
- Home / Library / Notes / Explore navigation
- Coming Soon / book-review feed
- Custom App Theme
- Justify Normal / Auto spacing
- Fast chapter transitions and adjacent-chapter preloading
- Multi-book EPUB/PDF import
- Correct highlight/note text mapping
- System inset and scroll/font-scaling fixes

## Development flow

1. Inspect current Lab main before changing code.
2. Make the smallest safe change.
3. Run Lab CI: source checks, APK/AAB build, lint, smoke checks.
4. Build a signed Lab APK only when real-device testing is needed.
5. User tests on a real device.
6. Promote to production only after explicit approval.

Never commit credentials, signing secrets, private keys, keystores, Telegram tokens, Cloudflare credentials, or Firebase server credentials.
