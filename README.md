# WoW Reader Lab

Official Android source repository for **WoW Reader**.

## Current release

- **Version:** `2.20.0`
- **versionCode:** `64`
- **Package:** `com.whisper.wowreader`
- **Minimum Android:** 6.0 / API 23
- **Target SDK:** 36
- **Java:** 17
- **Source of truth:** `main`

v64 is the 100k-scale library/sync foundation release. It preserves the v63 update/data/signing contract while moving normal library and cloud-sync work away from whole-library operations.

## Start here

Read these before changing the project:

- `NEXT_CHAT_HANDOFF.md` — current release/testing continuation
- `docs/STORE_PUBLICATION_STATUS.md` — Play publication state
- `docs/ANDROID_IOS_RELEASE_HANDOFF.md` — release identity contract
- `docs/superpowers/specs/2026-09-18-v64-100k-sync-design.md` — v64 architecture
- `SIGNING.md` — public signing metadata only

Do not switch to the older `whispermmepub/wow-reader-app` repository unless explicitly requested.

## v64 highlights

- Indexed SQLite library/state foundation designed for **100,000+ books**
- Paginated/lazy library access instead of loading the whole library into memory
- Incremental Google Drive book sync: only new/changed books are uploaded
- Per-book SHA-256 content identity and duplicate-safe restore
- Paginated cloud restore with integrity verification
- Resumable upload checkpoints with persistent sync state
- Tombstone-based delete handling to prevent deleted books being resurrected
- Custom cover state kept separate from original EPUB/PDF files
- Optimized custom-cover storage/cache behavior
- EPUB whole-book page numbering: `Page X / Y`, with layout-aware pagination cache
- Legacy full ZIP backup retained as restore compatibility fallback
- Background/local-first sync so reading does not depend on cloud work

### Scale rule

Every new library, storage, sync, cover, search, calendar or reader-state decision must be evaluated against **100,000+ books**.

The app must not:

- scan/hash the entire library on normal page turns
- load 100,000 books/covers into RAM
- rebuild a whole backup ZIP for one changed book
- decode every cover at full resolution
- keep unlimited page/scroll events
- make active reading wait for Google Drive

One-time migration/restore may be O(N), but it must be bounded, restart-safe and not turn normal use into O(N).

## Stable features preserved

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
- Custom book metadata and app-side cover overrides

Existing books, progress, notes, shelves, calendar/history, settings and account/sync compatibility must survive in-place updates.

## Storage policy

Only source assets required by the app belong in Git. Build outputs, APK/AAB files, local Gradle/IDE state, keystores, passwords and recovery material are ignored and must stay outside the repository.

Runtime caches such as generated thumbnails must remain rebuildable and must never be the only copy of user-selected metadata.

Do not add generated release APK/AAB files to the repository.

## Production identity

Keep the original production signing identity unchanged.

Production SHA-1:

`21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`

Production SHA-256:

`29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`

Private keystores/passwords must never be committed.

## Release rules

1. Keep package `com.whisper.wowreader`.
2. Keep the original production/Play signing identity.
3. Check every Play track before choosing the next versionCode.
4. Never use a destructive migration to simplify an update.
5. Build, lint, test and verify APK/AAB before release.
6. Test v63 → v64 in-place update, fresh install + Drive restore, and the experimental-v64 → v64 update path.
7. Test Google sign-in/sync and large-library behavior on real devices before Production.

## Community

- Telegram books: https://t.me/TheBookR
- Discussion group: https://t.me/+rUiqzi2mdhNiNGZl
- Website: https://saroatsin.com
- Reviews: https://whispermmepub.github.io/Review/
