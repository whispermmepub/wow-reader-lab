# WoW Reader Lab

Official Android source repository for **WoW Reader**.

## License

Project-owned source code in this repository is licensed under the
[PolyForm Noncommercial License 1.0.0](LICENSE)
(`PolyForm-Noncommercial-1.0.0`). This is a **source-available, noncommercial
license**, **not an OSI-approved open-source license**. Use, modification and
redistribution are subject to the full license terms; public source availability
does not grant unrestricted commercial-use rights.

This license applies only to material the project owns and has the right to
license. Third-party components, dependencies, fonts, dictionaries and other
assets remain subject to their own licenses, copyright notices and attribution
requirements; they are not relicensed under PolyForm Noncommercial by this
repository. In particular:

- The bundled dictionary database is separately licensed under **CC BY-SA 4.0**;
  see its [source attribution and license notice](app/src/main/assets/dictionary/NOTICE.md).
- The Myanmar spelling word list has its own **WTFPL** notice;
  see [SPELLING-NOTICE.txt](app/src/main/assets/dictionary/SPELLING-NOTICE.txt).
- Bundled [fonts](app/src/main/assets/fonts/), other third-party assets and
  dependencies retain their respective owners' terms. Do not assume that an
  absent or incomplete notice grants PolyForm rights over third-party material;
  verify the applicable upstream license before reusing or redistributing it.

## Current source: v81 / 2.21.15 — Play upload succeeded (owner-reported)

The owner accepted the **v80 / 2.21.14 test APK** and authorized publishing its approved source to this repository and preparing a **v81 / 2.21.15 production-signed Google Play AAB**. v81 changes only the release version relative to the approved v80 runtime source. **The owner subsequently confirmed that the v81 / 2.21.15 AAB upload to Play Store succeeded.** This records the owner's report, not independently verified review approval, publication, rollout or live availability. The latest explicitly owner-confirmed live release remains **v77 / 2.21.11**, confirmed on 2026-10-20 at [Google Play](https://play.google.com/store/apps/details?id=com.whisper.wowreader). No Play Console operation was performed by this workflow. See [store status](docs/STORE_PUBLICATION_STATUS.md) for exact build evidence and [handoff](NEXT_CHAT_HANDOFF.md) for continuation details.

## Current Android identity

- **Version:** `2.21.15`
- **versionCode:** `81` (upload succeeded per owner; use `82` or higher for the next release and check all Play tracks)
- **Package:** `com.whisper.wowreader`
- **Minimum Android:** 7.0 / API 24
- **Target SDK:** 36
- **Java:** 17
- **Source of truth:** `main`

v64 is the 100k-scale library/sync foundation release. It preserves the v63 update/data/signing contract while moving normal library and cloud-sync work away from whole-library operations.

### v78–v81 changes

The approved reader work adds memory-pressure-aware single-renderer fallback and one-shot renderer recovery, sampled cover decoding, progressive first-page pagination, resource-aware readiness, cancellable adjacent-resource disk warmup, and privacy-safe chapter-stage traces. Publisher typography and original reader assets remain preserved. **No book/file-size rejection hard cap was added**: renderer thresholds and bounded warmup budgets control speculative work, not whether a book may be imported/read. Existing preview-only limits and full-chapter parsing costs remain.

The package, higher versionCode and unchanged production signer support update compatibility in principle and retain the normal fresh-install path. They are **not proof of actual phone installation or data retention**: physical Vivo Y30, the owner's original failing EPUB, actual fresh installation, in-place phone upgrade, and live Google sign-in/Drive restore were not tested in this release preparation. Do not uninstall or clear data to test an update. Android 6.0 / API 23 remains intentionally unsupported.

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
- Global typography profile with Original EPUB and Reader settings, plus custom fonts
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
6. Test the live v77 → v81 in-place update without uninstall/clear-data, v80 → v81 where relevant, and fresh install + Drive restore; verify books, titles, progress, notes, shelves, calendar/history and settings.
7. Test Google sign-in/sync and large-library behavior on real devices before Production.

## Community

- Telegram books: https://t.me/TheBookR
- Discussion group: https://t.me/+oKgpGSA_lKc4YTU1
- Website: https://saroatsin.com
- Reviews: https://whispermmepub.github.io/Review/

### v75 validation and delivery
Accepted signed APK and matching AAB are under `/workspace/wow-reader-v75-update/`. The existing validation records 509 passing host tests, zero failures/errors/skips, passing contracts and desktop Chromium reader suites, and lint with 177 warnings but no fatal/errors. Physical Android touch/FPS and live-account restore are not established by those tests; first uncached chapters still need full engine pagination. See `docs/STORE_PUBLICATION_STATUS.md` and `docs/WHATS_NEW_v75.txt`. No Google Play upload is authorized for this workflow.
