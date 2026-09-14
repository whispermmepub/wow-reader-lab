# WoW Reader Lab changelog

## v2.19.1 / versionCode 61 — File Share stable

- Promoted the trusted v60 baseline to v61.
- Added actual EPUB/PDF File Share through the Android system share sheet.
- Uses `ACTION_SEND`, `EXTRA_STREAM`, `ClipData`, `FLAG_GRANT_READ_URI_PERMISSION` and `FileProvider`.
- EPUB MIME: `application/epub+zip`.
- PDF MIME: `application/pdf`.
- Fallback MIME: `application/octet-stream`.
- Preserved package `com.whisper.wowreader`.
- Preserved minSdk 23 and targetSdk 36.
- Preserved the original production signing identity for update compatibility.
- Preserved existing library, reading progress/history, shelves, notes/highlights, Reading Calendar, Firebase sign-in and Google Drive sync behavior.
- Explicitly excluded WoW Audio handoff/integration from the trusted v61 release.

## Earlier stable work carried into v61

- Offline EPUB/PDF reader improvements.
- Reading Statistics and streaks.
- Myanmar Reading Calendar and Reading Memory.
- Smart Library and custom shelves.
- Notes & Highlights.
- Custom typography/fonts.
- Google sign-in plus private Drive appDataFolder backup/restore/auto-sync.
- EPUB footnote/endnote navigation fixes.
- PDF continuous reading/import handling fixes.
- Multi-import and reader transition improvements.
- Myanmar/English dictionary support.
