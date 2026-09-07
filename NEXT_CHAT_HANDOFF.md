# WoW Reader handoff

User-approved stable base: v2.18.2 / versionCode 52 on `fix/v52-unified-install`.
Current Lab candidate: v2.18.3 / versionCode 53 on `feature/v53-reading-calendar-recap`.
Do not merge/promote v53 until the user approves it on a real device.

Package: `com.whisper.wowreader` (unchanged).
Original production signing certificate SHA-256:
`29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`
Never generate a replacement signing key. Private signing material is not stored in this public repository.

## v52 approved base
- Footnote preview/navigation fixes and stale callback protection.
- Legacy WebView Page-mode column compatibility.
- Smart Sync calendar merge/deletion behavior.
- Exact original-production-signed update/fresh verification passed on API 23, 29 and 35 in Actions run 33991610151.
- Verified exact signed v52 SHA-256: `c78023f1a6826a95d7d0dabdf555b761645effff12d7f4da89c3bd68cc42d883`.
- User tested v52 on a real device and explicitly approved it as working very well.

## v53 Reading Calendar Recap candidate
Reading Calendar remains the home of the feature; no new main navigation item was added.

- Adds segmented `Week / Month / Year` views.
- Month view keeps the existing Myanmar calendar/day-book experience.
- Week view shows Mon-Sun reading activity and book covers.
- Year view shows 12 month summary cards and can jump into a month.
- Each period gets a `Week/Month/Year in Books` recap using local data only.
- Recaps show only genuinely completed books, plus reading days and reading time.
- `ReadingProgressStore` records a historical `finished_at_<filename>` timestamp the first time a book reaches 100%; rereading or moving backward does not erase/change the original completion date.
- Existing v52 users with already-finished books are backfilled conservatively from their latest recorded reading day, with last-opened time only as a fallback.
- Re-importing a same-name book cannot reuse an older completion timestamp from before the new `added_at_` time.
- Full recap screen renders a polished share card with finished-book covers and `Share Image` through Android's secure FileProvider/cache path.
- No server/backend/database migration was added; this is local-first and uses existing SharedPreferences/library/calendar data.

## v53 verification state
- Version: v2.18.3 / versionCode 53 / minSdk 23 / targetSdk 36.
- Latest source build, release APK/AAB build, Android lint and APK smoke checks PASS: Actions run 34117156642 (source commit `8ec6b449bb34e219e71beb686c960e3a71233701`).
- Feature-branch compile + lint PASS: Actions run 34117156686.
- A production-signed v53 real-device test APK was created outside the public repo using the preserved production signing kit. Exact APK SHA-256: `6bdf3f59a1f1587a94325bce18907c7c6a9e5e04f36b641d76abeb54915c2b39`.
- That APK verifies with v1/v2/v3 signatures and the same production certificate SHA-256 above.
- Real-device v52 -> v53 update and UI/share-card approval are still pending; keep v53 on this feature branch until approved.

Google authentication and live Drive network sync are unchanged by v53 and still require account/device checks when those services are specifically modified.
