# WoW Reader

Official Android source repository for **WoW Reader**, an offline EPUB/PDF reader
with a personal library, reading history, notes and optional Google Drive sync.

[Google Play](https://play.google.com/store/apps/details?id=com.whisper.wowreader)
· [Report a bug](https://github.com/whispermmepub/wow-reader-lab/issues/new?template=bug_report.yml)
· [Request a feature](https://github.com/whispermmepub/wow-reader-lab/issues/new?template=feature_request.yml)
· [Contributing](CONTRIBUTING.md)

## Release status

| Source / store milestone | Status |
|---|---|
| Current Android source | **v81 / 2.21.15** on `main`; [app-source commit fe720ce](https://github.com/whispermmepub/wow-reader-lab/commit/fe720ce159d4f8661ac2e4bf4d8a0357b3eb0af7) |
| v81 Play AAB upload | **Succeeded, owner-reported on 2026-10-23** |
| Latest owner-confirmed live version | **v77 / 2.21.11**, confirmed on 2026-10-20 |

**Upload success is not confirmation of review approval, publication or completed
rollout.** Play Console tracks and regional availability have not been independently
inspected. See [publication status](docs/STORE_PUBLICATION_STATUS.md) and the
[portable v81 evidence record](docs/releases/v81.md) for artifact identifiers,
recorded checks and unverified items. Documentation commits are not release-source
tags or new builds.

## What the app does

- Read EPUB and PDF offline, import multiple books and share files through Android.
- Organize a Smart Library with custom shelves, metadata and cover overrides.
- Keep reading progress, notes/highlights, Reading Memory, statistics, streaks
  and a Myanmar Reading Calendar.
- Adjust typography and custom fonts; use Myanmar/English dictionary support
  and EPUB footnote/endnote navigation.
- Optionally sign in with Google/Firebase and back up, restore or incrementally
  sync through private Google Drive `appDataFolder` storage.

The v64 storage/sync foundation is designed around **100,000+ books** using indexed
SQLite, paginated access, per-book SHA-256 identity, incremental uploads,
resumable checkpoints and deletion tombstones. Legacy ZIP restore remains a
compatibility fallback. This is a design target, not an all-device scale guarantee.

The approved v78–v80 reader work carried into v81 adds memory-aware renderer
fallback/recovery, sampled covers, progressive pagination/readiness and bounded
cancellable disk warmup. **No book/file-size rejection hard cap was added.**
Whole-chapter parsing costs and existing preview-only limits remain. See
[feature status](docs/FEATURE_STATUS.md) for detail and test limitations.

## Android project at a glance

| Setting | Current source |
|---|---|
| Package | `com.whisper.wowreader` |
| Version | `2.21.15` / versionCode `81` |
| Minimum Android | 7.0 / API 24; Android 6.0 / API 23 is unsupported |
| Target / compile SDK | 36 |
| Java source compatibility | 17 |
| Source of truth | This repository, `main` |

For development setup and validation commands, read [CONTRIBUTING.md](CONTRIBUTING.md).
Production signing is separate; see [SIGNING.md](SIGNING.md). Never commit
keystores, passwords, tokens, recovery material, APK/AAB files, generated outputs
or local Gradle/IDE state.

## Engineering constraints

Preserve the package, original production signing identity and user data across
in-place updates: books/files, metadata, progress, notes, shelves, calendar/history,
settings and account/sync compatibility. Never use destructive migrations or ask
users to uninstall/clear data as an update test.

Normal reading must not scan/hash a whole library, load all books/covers into RAM,
rebuild a full backup ZIP for one changed book or wait for Drive. Migration and
restore may be O(N), but must be bounded and restart-safe. Rebuildable caches must
not be the only copy of user-selected metadata.

VersionCode **81 is consumed**. A future release needs **82 or higher**, after
checking every Play track. Package/signer continuity supports compatibility in
principle, not proof of an actual phone update or data retention. Recorded v81
checks did **not** include actual phone install/upgrade, Vivo Y30, the original
failing EPUB or live Google sign-in/Drive restore; instrumentation was compiled
only. Device and live-service checks remain necessary before claiming those work.

## Documentation map

- [Publication status](docs/STORE_PUBLICATION_STATUS.md) — current store facts and remaining QA.
- [v81 evidence](docs/releases/v81.md) — portable source/artifact/check summary without local paths.
- [Feature status](docs/FEATURE_STATUS.md) — current features and historical v61 reference.
- [Contributor guide](CONTRIBUTING.md) — focused changes, validation and safe reporting.
- [Release/update contract](docs/ANDROID_IOS_RELEASE_HANDOFF.md) — current Android identity plus explicitly historical Android/iOS records.
- [100k library/sync design](docs/superpowers/specs/2026-09-18-v64-100k-sync-design.md) — architecture and scale constraints.
- [Partial changelog](docs/CHANGELOG_LAB.md) — selected release history, not every version.
- [Maintainer handoff](NEXT_CHAT_HANDOFF.md) — detailed continuation notes; older sections are historical and local paths are not public downloads.

Despite the repository name, this is the original app's current official Android
source. Do not substitute the older `whispermmepub/wow-reader-app` source line.
Historical lab documents are context, not current release authorization.

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

## Community

[Telegram books](https://t.me/TheBookR) ·
[Discussion group](https://t.me/+oKgpGSA_lKc4YTU1) ·
[Website](https://saroatsin.com) ·
[Reviews](https://whispermmepub.github.io/Review/)
