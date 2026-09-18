# WoW Reader — Store Publication Status

## Google Play

Current Android release candidate:

- versionName: `2.20.0`
- versionCode: `64`
- package: `com.whisper.wowreader`
- category: Books & Reference

v64 is the 100k-scale library/incremental-sync release candidate. The exact verified AAB should be uploaded only after checking every Play track and confirming that versionCode 64 is available.

### v64 “What’s new”

`Improved performance and stability for large libraries, faster Google Drive incremental sync and restore, custom book covers, whole-book EPUB page numbering, and various bug fixes.`

### Runtime release gates

Before Production:

- v63 → v64 update preserves books, progress, shelves, notes/highlights, calendar/history and settings
- experimental-v64 → v64 update preserves local data and sync state
- fresh install → Google sign-in → Drive restore works without the legacy full ZIP
- 100k synthetic library remains bounded in memory and responsive
- adding one book to an existing large library uploads only that book
- changing one book does not rebuild/upload the entire library
- interrupted upload resumes safely
- deleted books are not resurrected by another device
- custom covers restore without modifying original EPUB/PDF bytes
- EPUB whole-book `Page X / Y` remains correct after layout changes
- low-memory Android devices do not crash during library/cover operations

## Signing / Google identity

Keep the original production App Signing identity and Firebase/OAuth registration unchanged.

Production SHA-1:

`21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`

Do not commit private signing material.

## Previous baseline

v63 / versionCode 63 remains the rollback/reference baseline. Keep its verified artifacts and signing recovery kit outside the public repository.
