# WoW Reader — v64 Handoff

## Current source

- Repository: `whispermmepub/wow-reader-lab`
- Branch: `main`
- Version: `2.20.0`
- versionCode: `64`
- Package: `com.whisper.wowreader`
- minSdk: 23
- targetSdk: 36
- Java: 17

## v64 purpose

This release establishes the **100,000+ book** scalability and incremental Google Drive sync foundation.

Normal operations must stay proportional to the visible page or changed items, not total library size.

### Required architecture behavior

- SQLite indexed book/state source of truth
- paged/lazy library queries
- bounded cover decode/cache
- per-book SHA-256 Drive identity
- dirty queue and small sync batches
- resumable upload checkpoints
- paginated restore
- tombstones for deletes
- state/metadata separate from large EPUB/PDF binaries
- legacy full ZIP kept only as compatibility fallback
- local-first reading; cloud sync never blocks active reading
- custom cover is an app-side override and never rewrites EPUB/PDF bytes
- EPUB whole-book page numbering uses cached pagination keyed by layout fingerprint

## Release testing

Test all three:

1. **Experimental v64 → final v64 update**
2. **v63 → final v64 update**
3. **Uninstall → fresh v64 install → Google Sign-In → Drive restore**

For scale testing use 100 / 1,000 / 10,000 and synthetic 100,000-book metadata sets where practical.

For sync, verify:

- 100,000 existing + 1 new → 1 book upload
- 100,000 existing + 2 new → 2 book uploads
- one changed book → only that book is uploaded
- network interruption → checkpoint/retry without rebuilding all books
- delete on one device → tombstone prevents resurrection
- fresh device → paginated restore
- custom cover/font/state restore
- legacy ZIP remains readable

## Data safety

Never clear app data or regenerate signing identity to make migration easier.

Keep production keystore/passwords and release artifacts outside Git.

## Next development rule

Do not start another large architecture rewrite until real-device v64 testing has produced evidence. Any new feature must first pass the **100,000+ scale rule**.
