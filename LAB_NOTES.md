# WoW Reader Lab notes

The current trusted line is **WoW Reader v2.19.1 / versionCode 61**.

## Source of truth

- Stable branch: `stable/v61`
- Equivalent branch: `stable/v61-file-share`
- Package: `com.whisper.wowreader`
- minSdk: 23
- targetSdk: 36

## Current release scope

v61 is the approved v60 baseline plus actual EPUB/PDF File Share.

The trusted v61 release:
- keeps all existing reader/library/sync behavior
- adds Android system File Share for EPUB and PDF
- does **not** include WoW Audio handoff
- preserves the original production signing identity

## Development rules

- Start future features from the current v61 stable source on a new branch.
- Preserve offline EPUB/PDF reading, Firebase sign-in, Drive appDataFolder backup/restore/auto-sync, local library state, reading progress, shelves, notes/highlights, Reading Calendar and settings.
- Avoid broad rewrites of working reader behavior.
- Do not commit private signing material or credentials.
- Promote only after build/regression checks and real-device approval when required.
