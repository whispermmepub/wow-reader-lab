# WoW Reader handoff

Current stable source: v2.18.4 / versionCode 54.
Package: `com.whisper.wowreader` (unchanged).

Original production signing certificate SHA-256:
`29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`
Never generate a replacement signing key. Private signing material is not stored in this public repository.

## v54 stable
v54 includes all approved v52 behavior, the v53 Reading Calendar recap work, and the v54 reader/PDF/OEM fixes.

- Reading Calendar has Week / Month / Year views and shareable period recap cards.
- Historical completion timestamps are retained safely for finished books.
- EPUB footnote/endnote navigation preserves the exact source reference/page and does not overwrite reading progress.
- Reader page/scroll modes and page/slide navigation regressions are covered by instrumentation.
- PDF continuous reading and import-type handling are included.
- Existing local library, SharedPreferences, shelves, calendar data, notes/highlights and Google Drive/Firebase architecture remain in their existing locations; no destructive migration/reset was added.
- Package identity remains `com.whisper.wowreader`; minSdk 23; targetSdk 36.

## v54 verification
Final stable gate: GitHub Actions run `34125656776`.

PASS on all matrix devices:
- Android API 23 / x86
- Android API 29 / x86_64
- Android API 35 / x86_64

Each matrix verifies:
- v51 -> v54 update + launch + seeded data retention
- v52 -> v54 update + launch + seeded data retention
- v53 -> v54 update + launch + seeded data retention
- clean/fresh v54 install + launch
- reader/navigation/calendar/shelf instrumentation regression suite

Build-fixtures, version/package checks and lint also passed. Two earlier CI-only timing/isolation races in the instrumentation harness were fixed; the final run is fully green.

## Production release rule
Use one original-production-signed v54 APK for both in-place updates and fresh installs. Sign only with the preserved original production key/certificate above. Never commit signing material.

## Next feature: v55 in-app dictionary
Implement on a separate branch from stable v54:
- English -> Myanmar and Myanmar -> English.
- Offline-first dictionary data plus online enrichment/fallback.
- Reader selection `Translate` opens the dictionary/result inside WoW Reader; never redirect to Google Translate or an external browser.
- Add a dedicated in-app Dictionary screen/search entry.
- Prefer permissive/shareable dictionary data with clear attribution; do not silently bundle incompatible third-party dictionary databases.
- Keep existing reader, Firebase sign-in, Drive appDataFolder sync, backup/restore and library behavior unchanged.
