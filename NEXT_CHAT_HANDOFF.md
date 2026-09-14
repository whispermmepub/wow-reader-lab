# WoW Reader handoff

Current trusted release: **v2.19.1 / versionCode 61**.
Package: `com.whisper.wowreader` (unchanged).
Official stable branch: `stable/v61`.
Equivalent stable file-share branch: `stable/v61-file-share`.
Original v61 baseline commit: `58f39edafe7b9a1bab9d0e4bbe39d31930056c05`.

Original production signing certificate SHA-256:
`29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`

Never generate a replacement signing key. Private signing material is not stored in this public repository.

## v61 stable definition

v61 is the trusted v60 baseline plus **actual EPUB/PDF File Share** and the v61 version bump.

Actual File Share uses the Android system share sheet with `ACTION_SEND`, `EXTRA_STREAM`, `ClipData`, temporary read permission and `FileProvider`.

MIME types:
- EPUB: `application/epub+zip`
- PDF: `application/pdf`
- fallback: `application/octet-stream`

**Do not add WoW Audio handoff/integration back into v61.** Earlier experimental builds that contained WoW Audio handoff are not the trusted v61 release.

## Stable behavior to preserve

- Offline EPUB/PDF reading
- Existing library/books and local files
- Reading progress/history
- Shelves and custom shelf rename/delete
- Notes/highlights and Reading Memory
- Reading Statistics and streaks
- Myanmar Reading Calendar
- Google/Firebase sign-in
- Google Drive `appDataFolder` backup/restore/auto-sync
- Smart Sync Merge
- Custom fonts and typography
- Myanmar/English dictionary behavior
- EPUB footnote/endnote navigation
- PDF continuous reading/import handling
- Multi-import
- Existing app settings and SharedPreferences

No destructive migration/reset should be added.

## Android identity

- applicationId: `com.whisper.wowreader`
- minSdk: 23
- targetSdk: 36
- versionName: `2.19.1`
- versionCode: `61`

## Production signing rule

Use the preserved original production signing identity for updates and fresh installs where update compatibility is required.

Registered production fingerprints:
- SHA-1: `21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`
- SHA-256: `29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`
- alias: `wowreader-production`

Never commit the JKS, private key, Base64 keystore, or passwords.

## Play Store state

The current Play Store release line is v61. The production artifact should be an AAB built from the trusted v61 source and signed with the preserved production identity.

When configuring Google Play App Signing, preserve compatibility with previously sideloaded production-signed APKs if that update path is required.

## Next-development rule

Start future work from the current v61 stable line on a separate feature branch. Do not modify the trusted v61 behavior in place unless explicitly requested.
