# WoW Reader Lab changelog

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
