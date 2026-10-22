# Feature status

## Current Android source

**v81 / 2.21.15**, package `com.whisper.wowreader`, minSdk **24**
(Android 7.0+), target/compileSdk **36**. The current source line is `main` in
`whispermmepub/wow-reader-lab`, not the historical `stable/v61` baseline or the
older `wow-reader-app` repository.

The owner reported a successful **v81 AAB upload on 2026-10-23**. Upload success
is not confirmation of review approval, publication or rollout. **v77 / 2.21.11
remains the latest owner-confirmed live version** (2026-10-20).
See [publication status](STORE_PUBLICATION_STATUS.md) and the
[portable v81 evidence record](releases/v81.md) for evidence and limitations.

## Existing feature areas

- Offline EPUB/PDF reading, multi-book import and Android file sharing.
- Google/Firebase sign-in and private Google Drive `appDataFolder` backup,
  restore and incremental sync.
- Reading Statistics, streaks, Myanmar Reading Calendar and Reading Memory.
- Notes/highlights, Smart Library, custom shelves, metadata and cover overrides.
- Typography settings, custom fonts and Myanmar/English dictionary support.
- EPUB footnote/endnote navigation and PDF continuous reading/import handling.
- Indexed library/state storage and bounded, paginated sync designed around
  **100,000+ books**; this is an architecture target, not an all-device guarantee.

## Approved reader work carried into v81

The v78–v80 reader work includes memory-pressure-aware renderer fallback and
recovery, sampled cover decoding, progressive pagination, resource-aware
readiness, bounded cancellable disk warmup and privacy-safe chapter-stage traces.
v81 changes release version metadata relative to the owner-approved v80 runtime.
No book/file-size rejection hard cap was added. Whole-chapter parsing costs and
existing preview-only limits remain.

These feature descriptions do not establish physical-device or live-service QA.
Actual phone fresh install/in-place upgrade, Vivo Y30, the original failing EPUB,
and live Google sign-in/Drive restore were not tested in the recorded v81
preparation. Instrumentation was compiled only, not run. Preserve books, progress,
notes, shelves, history, settings and account/sync state across in-place updates;
do not uninstall or clear data to test an update.

## Historical records

The former v61 status is preserved unchanged in
[FEATURE_STATUS_v61.md](history/FEATURE_STATUS_v61.md). Its API 23 identity,
`stable/v61` branching instruction and feature exclusions describe that historical
baseline, not current release instructions. The
[changelog](CHANGELOG_LAB.md) is a partial historical record, not an exhaustive
list of every release.
