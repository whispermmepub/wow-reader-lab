# WoW Reader Lab changelog

This is a **partial release history**, not an exhaustive account of every version.
For current source/store state, see [publication status](STORE_PUBLICATION_STATUS.md).

## v2.21.15 / versionCode 81 — approved reader work

- Carries the owner-approved v80 runtime work into the next release version:
  memory-aware renderer fallback/recovery, sampled covers, progressive pagination
  and readiness, bounded cancellable disk warmup and privacy-safe chapter traces.
- No book/file-size rejection hard cap was added; whole-chapter costs remain.
- AAB upload succeeded according to the owner on 2026-10-23; review approval,
  publication and rollout completion are not established. v77 / 2.21.11 remains
  the latest owner-confirmed live release (2026-10-20).
- See [v81 evidence](releases/v81.md) for exact source/artifact identifiers,
  recorded validation and the remaining device/live-service checks.

## Historical v75 preparation

The former README's [v75 validation note](history/README_v75_validation.md) is
preserved separately. Its local paths and workflow authorization are historical,
not the current release state or instructions.

## v2.20.0 / versionCode 64 — 100k Library + Incremental Sync

- Added the 100,000+ book scalability foundation.
- Library/state operations use indexed SQLite and bounded/paged access.
- Google Drive book sync is incremental and content-hash based instead of rebuilding one full library ZIP for each change.
- Large cloud restore is paginated and verifies SHA-256 before installing each book.
- Resumable upload state/checkpoints are persisted so interrupted transfers can retry safely.
- Delete tombstones prevent removed books from being resurrected by another device.
- Legacy `wow_reader_backup_v1.zip` remains available as a compatibility restore fallback.
- Added app-side custom cover support without rewriting EPUB/PDF files.
- Added whole-book EPUB page numbering with layout-aware pagination caching.
- Added custom-cover/font/state handling to the incremental restore path.
- Improved fresh-install restore behavior and remote-object identity preservation.
- Added regression contracts for 100k-scale queries, incremental sync and restore.

## v2.19.1 / versionCode 61 — File Share stable

- Promoted the trusted v60 baseline to v61.
- Added actual EPUB/PDF File Share through the Android system share sheet.
- Preserved package, signing identity and existing user data.
