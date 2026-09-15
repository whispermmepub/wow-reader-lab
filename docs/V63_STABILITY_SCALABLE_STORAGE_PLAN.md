# WoW Reader v63 — Stability + Scalable Library/Reading State Plan

Base: `release/v62-google-signin` (v2.19.2 / versionCode 62)

## Goals

This work combines the closed-testing feedback with a storage architecture that stays fast and safe when the library grows from tens of books to hundreds or thousands.

### Closed-testing issues to fix

1. EPUB overall reading percentage can be wrong (for example, TOC/front-matter can already show ~20%).
2. Reading Calendar / finished-book recap becomes slow, especially when sharing a photo card.
3. Opening the same EPUB from Telegram/other apps can create duplicate local library files.
4. Recap sharing only shows the first 12 finished books.
5. Google-account sync can interrupt reading / return the user to Home; this is more reproducible after connecting Google.
6. Current per-book state/statistics storage must remain reliable and performant with hundreds or thousands of books.

## What the current v62 code does

- Per-book percentage is stored as `percent_<filename>` in SharedPreferences.
- EPUB location is stored as `epub_chapter_<filename>` + `epub_scroll_<filename>`; PDF location as `pdf_page_<filename>`.
- Reading statistics use large JSON objects inside SharedPreferences (`reading_stats_days_json`, `reading_stats_books_json`, `reading_stats_day_books_json`, notes JSON, etc.).
- Google Smart Sync currently rebuilds a ZIP containing the whole local library, fonts and preference state.
- Recap generation scans local library files and share-card rendering creates/scales/compresses a bitmap.
- Incoming ACTION_VIEW/ACTION_SEND imports use a unique filename strategy, so a file that already exists can be copied again with a timestamp suffix.

This is acceptable for a small library but creates avoidable O(n) parsing/scanning/write amplification as the library and history grow.

## Chosen architecture

### 1. Use Room/SQLite for structured library state

Use AndroidX Room (Java annotation processor; minSdk 23 matches WoW Reader) as the durable index for structured data. SharedPreferences remains for small app settings only (theme, UI toggles, reader defaults, connected-account flags).

Recommended tables:

### `books`

One row per unique book file/content.

- `bookId` (stable primary key; SHA-256 content hash or generated ID tied to the hash)
- `contentHash` UNIQUE
- `fileName`
- `filePath`
- `format` (`epub`/`pdf`)
- `fileSize`
- `modifiedAt`
- `title`
- `author`
- `addedAt`
- `lastOpenedAt`
- `progressPermille` (0..1000)
- `finishedAt`
- `epubSpineIndex`
- `epubOffsetPermille`
- `pdfPage`
- `contentUnitsTotal` (for weighted EPUB progress)
- `dirtyRevision` / `lastChangedAt`

Indexes: `contentHash` UNIQUE, `lastOpenedAt`, `finishedAt`, optionally `(format, lastOpenedAt)`.

### `reading_day`

One row per calendar day:

- `day` PRIMARY KEY (`yyyy-MM-dd`)
- `totalMs`
- `dailyNote`

### `book_day`

Aggregated reading time per book/day. Do not save every page/scroll event.

- `day`
- `bookId`
- `durationMs`
- `bookDayNote`
- PRIMARY KEY (`day`, `bookId`)

Index: `bookId`, and `(day, bookId)` primary key.

### Optional later tables

Bookmarks/annotations can move to dedicated tables after the v63 reliability release. They do not need to block this migration.

## Reading-point policy

Do **not** keep hundreds of raw reading-point snapshots per book. For normal reading resume, only the latest durable locator is needed.

Store:

- latest progress / locator per book (one row),
- last-opened timestamp,
- aggregated reading duration per book/day,
- finished timestamp once completion is reached.

If a true navigation history feature is added later, keep a bounded ring/history (for example 20–50 checkpoints per book), not unlimited events.

This makes storage growth proportional to number of books + days read, not page turns.

## EPUB overall progress

Replace equal-spine weighting with content-weighted progress.

At import/first metadata scan:

1. Parse readable EPUB spine items.
2. Exclude navigation/TOC/cover-only/non-reading resources when identifiable.
3. Compute a lightweight content weight for each readable spine item (normalized text length; fallback to a small nonzero unit for image-only readable chapters).
4. Cache the cumulative weight table.
5. Overall progress = completed readable-content units + current chapter fraction × current chapter weight, divided by total readable-content units.

Do not rescan the entire EPUB on every page turn. Compute/cache weights once, invalidate only if the file hash changes.

PDF progress remains page-based.

## Duplicate import policy

Incoming files from Telegram/other apps must be imported idempotently.

1. Stream the incoming file in a background thread.
2. Compute SHA-256 while copying to a temporary file (one pass).
3. Query the `books.contentHash` unique index.
4. If the hash already exists, delete temp file and open/focus the existing library book instead of creating another copy.
5. If filename matches but hash differs, keep both as genuinely different files using a safe disambiguated filename.

This also detects the same book after it is renamed externally.

## Recap/calendar performance

- Query finished books by indexed `finishedAt` range instead of scanning every file.
- Query day/month/year reading totals using SQL aggregation instead of parsing whole JSON blobs repeatedly.
- Load only fields needed for each screen.
- Keep cover extraction cached and lazy; never decode every cover at full resolution.
- Render/share images off the main UI thread.

### More than 12 finished books

Do not build one unbounded giant bitmap. Keep readable card pages (for example 12–16 covers per 1080px share image), generate as many pages as needed, and share all pages with `ACTION_SEND_MULTIPLE`. Thus all finished books are included without an OOM-prone enormous image.

## Google Drive sync redesign

Reading must remain local-first and must never wait for cloud sync.

### Immediate v63 safety rule

- While `BookReaderActivity` is in the foreground, save locally only.
- Do not start/flush whole-library Drive Smart Sync from the reader.
- Schedule sync when returning to Home/background, or on explicit user Sync.

### Scalable sync direction

Separate small state sync from large book-file backup:

- Metadata/progress/notes: small versioned state/manifest, debounced and incremental.
- Book files: upload only when imported/changed; identify by content hash.
- Deletes: tombstone/revision in manifest rather than rebuilding every file.
- Use WorkManager/unique work with network constraints for persistent background sync instead of an Activity-owned delayed Handler.

A full ZIP of every EPUB/PDF should not be rebuilt merely because the reader moved to another page.

## Migration / data-safety rules

Existing users must keep library/progress/shelves/notes/highlights/calendar/settings.

Migration should be idempotent:

1. Create Room DB.
2. Enumerate current library files once in background.
3. Read existing per-file SharedPreferences values and insert/upsert rows.
4. Convert existing reading-stat JSON into `reading_day`/`book_day` rows.
5. Mark migration complete only after transaction success.
6. During the transition release, retain legacy values as fallback/read-only safety data; do not destructively clear them.
7. Backup/restore must export/import Room-backed structured state before legacy cleanup is considered in a later release.

## Scale target

Design target for testing:

- 1,000 books: Home/Library/Calendar remain responsive.
- 10,000 metadata rows: indexed lookup/sort/query remains normal database work; UI must page/lazy-bind rather than inflate everything at once.
- Multi-year reading calendar: SQL range queries/aggregates, not full JSON parse on every render.
- No operation on a page turn should scan the entire library, hash the book again, rebuild a whole backup ZIP, or render full-resolution covers.

## Release sequencing

### v63 reliability release

- Stop cloud sync work from interrupting active reading.
- Deduplicate ACTION_VIEW/ACTION_SEND imports by stable content identity.
- Fix EPUB weighted overall progress.
- Remove recap's 12-books-total limitation by paged multi-image sharing.
- Move recap image work off UI thread and reduce repeated scans.
- Introduce Room-backed book/progress/read-stat index with safe migration, while preserving legacy data.

### After closed-test validation

- Switch cloud sync to manifest/incremental file model via WorkManager.
- Migrate remaining large structured preference blobs (annotations/bookmarks if needed) after compatibility testing.
- Only then consider retiring old legacy keys.

## Non-negotiable release checks

- Preserve package `com.whisper.wowreader`.
- Preserve production signer and Play App Signing identity.
- versionCode must be greater than 62.
- Test upgrade from v62 without clearing app data.
- Test 1 / 100 / 1,000-book synthetic libraries.
- Test Google account connected and disconnected.
- Test 30+ minute reading session with no unexpected return to Home.
- Test Telegram same-file open repeatedly: library count must not increase.
- Test EPUB with large TOC/front-matter and verify progress near 0% before real reading starts.
- Test recap with >12, >50 finished books and low-memory device conditions.
