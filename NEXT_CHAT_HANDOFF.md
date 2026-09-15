# WoW Reader — New Chat / Play Store v63 Handoff

Use this file as the **first source of truth** in a new chat. The goal is to continue the Google Play update without rediscovering signing, Firebase, source, or release-state details.

## 1. Current Android candidate

- App: **WoW Reader / WoW Reader Lab**
- Repository: `whispermmepub/wow-reader-lab`
- Current source branch: `main`
- Play Store release branch: `release/v63-playstore`
- Version name: **2.19.3**
- versionCode: **63**
- Package/applicationId: `com.whisper.wowreader`
- minSdk: 23
- targetSdk: 36
- Java: 17
- Previous stable production baseline: `stable/v61` (`2.19.1 / 61`)
- v62 test/release branch: `release/v62-google-signin`

Do not use the older `whispermmepub/wow-reader-app` repository as the current source.

## 2. Why v63 exists

Closed-test feedback exposed these problems:

1. EPUB progress could show around 20% while the reader was still near the TOC/front matter.
2. Reading Calendar / finished-book sharing became slow.
3. Opening an EPUB from Telegram could create duplicate library files.
4. Finished-book share cards only showed 12 books.
5. Google-account sync could interrupt reading or return the reader to Home, often after roughly a minute.
6. Large libraries/history needed a storage design that remains responsive with hundreds or thousands of books.

v63 addresses those issues while preserving existing user data.

## 3. v63 implementation summary

- EPUB progress uses cached readable-content weighting instead of equal-spine/chapter weighting.
- TOC/cover/title/copyright-style front matter is excluded or heavily reduced when identifiable.
- EPUB progress analysis is bounded so unusually large chapter HTML does not cause unbounded memory growth.
- Incoming EPUB/PDF imports use SHA-256 content identity. The same content should open the existing library copy instead of creating a timestamp-suffixed duplicate.
- `ReaderStateDb` uses indexed Android SQLite via `SQLiteOpenHelper` for scalable book/progress/day statistics state.
- The migration is non-destructive: legacy SharedPreferences/stat JSON remain as compatibility/fallback data during the transition release.
- Only the latest durable reading locator is kept per book; the app does not accumulate hundreds of raw page/scroll checkpoints.
- Reading duration is aggregated per day/book instead of saving every page event.
- API-23-safe SQLite `INSERT OR IGNORE + UPDATE` logic is used instead of newer UPSERT syntax unavailable on older Android SQLite.
- Active `BookReaderActivity` no longer starts/flushes Google cloud sync. Reading is local-first; sync is deferred outside the active reading session.
- Reading recap/calendar queries use structured indexed state instead of repeatedly scanning/parsing growing data where possible.
- Share-card generation runs off the UI thread.
- More than 12 finished books are shared as multiple card pages instead of truncating or creating one giant OOM-prone bitmap.

The design document originally considered Room. The actual v63 candidate intentionally uses `SQLiteOpenHelper`/SQLite to reduce dependency and migration risk for this reliability release.

## 4. Production signing — never replace this identity

Keep the original production keystore permanently. Never generate a new signing key for a normal update.

Safe metadata:

- Keystore filename: `wow-reader-production.jks`
- Alias: `wowreader-production`
- Certificate owner: `CN=WoW Reader, OU=Android, O=Whisper Of Words, C=MM`
- SHA-1: `21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`
- SHA-256: `29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`

Play Console → App integrity → **App signing key certificate SHA-1 now matches the same production SHA-1 above**.

Private signing passwords/JKS are not stored in this public repository. If a rebuild must be production-signed in a new chat, the user must provide the private signing kit/recovery files from their secure backup.

Gradle release environment variables:

- `WOW_RELEASE_STORE_FILE`
- `WOW_RELEASE_STORE_PASSWORD`
- `WOW_RELEASE_KEY_ALIAS`
- `WOW_RELEASE_KEY_PASSWORD`

Do not print raw passwords in chat or commit them.

## 5. Firebase / Google identity

- Firebase project: `wow-reader`
- Project number: `1027420568326`
- Package: `com.whisper.wowreader`
- Production Android OAuth client: `1027420568326-m9f98kke24it1uvsfdlki6uanvrd2lna.apps.googleusercontent.com`
- Production SHA-1: `21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`

The current `app/google-services.json` is aligned with the package and original production signing identity. Do not casually replace it with an older config.

Historical context: Play-only Google Sign-In previously failed with `[16] Account reauth failed` when the Play-distributed signer did not match the Firebase/OAuth identity. The Play App Signing certificate has since been changed/aligned to the original production certificate above.

## 6. Verified v63 artifacts

Use the AAB for Google Play.

- `WoW-Reader-v2.19.3-v63-PlayStore-production.aab`
  - SHA-256: `3587777e5f9586ca112bd92cbee65e74c82b0b29f615167da075f75d584e35ae`
- `WoW-Reader-v2.19.3-v63-production-signed.apk`
  - SHA-256: `2ca3a1b4487e98c1ae2e0969cb571457dee032b2daff4ec05656d2ab07a3db25`
- Release candidate bundle SHA-256: `8fbe4070a60e67ad6a61ad580eec2c8cc35719bb2cfa7e44a3f9d09139b36d38`

Artifact app-source commit: `21894db4f10a5ea588e99b4b07dbaba181ecd70f`.

Repository cleanup/docs can be newer than the artifact source commit. Before rebuilding, compare `app/` against that release candidate and do not introduce unrelated feature changes.

## 7. CI state

The final v63 release-candidate CI run completed successfully:

- configuration/safety invariant checks: PASS
- Gradle release build: PASS
- release lint: PASS
- APK package/version/minSdk/targetSdk verification: PASS
- AAB/APK artifact collection: PASS

The repository now keeps a generic `.github/workflows/build-production.yml` for future releases instead of old one-off v52/v54/v62/v63 verification workflows.

## 8. Play Store status and exact next action

Tester opt-in URL:

`https://play.google.com/apps/testing/com.whisper.wowreader`

The next chat should **not** redesign v63 first. Continue the release path:

1. Re-check Play Console tracks and confirm no `versionCode >= 63` is already active/drafted. If 63 is already consumed, increment above the highest Play track before building a replacement.
2. Upload `WoW-Reader-v2.19.3-v63-PlayStore-production.aab` to Closed Testing if code 63 is still available.
3. Use the English release note below.
4. Roll out to testers.
5. Test update from the previous Play build without clearing data.
6. For a clean auth verification device/account, uninstall only when it is safe to lose local-only data, reinstall from the Play tester link, then test Google Sign-In.
7. Do not promote to Production until the runtime checklist passes.

### Play Store “What’s new”

`Improved reading progress accuracy, fixed duplicate EPUB imports, optimized Reading Calendar and sharing performance, improved stability with Google account sync, and enhanced library performance for large book collections.`

## 9. Mandatory Closed Testing checklist

Verify on real devices, including with Google account connected:

- v61/v62 → v63 in-place update keeps books, progress, shelves, notes/highlights, Reading Memory, calendar/history and settings.
- Google Sign-In succeeds from the Play-installed build; `[16] Account reauth failed` does not return.
- Read continuously for at least 30 minutes with Google connected; reader must not unexpectedly return to Home.
- Repeat-open the same EPUB from Telegram several times; library count must not increase.
- Repeat the duplicate test with the same content under a renamed incoming filename.
- Test an EPUB with large TOC/front matter; progress should stay near the beginning until real content is read.
- Test normal EPUBs and PDFs for resume position after app restart.
- Test Reading Calendar and recap with >12 and preferably >50 finished books; all books must be shareable across generated card pages.
- Verify share generation does not freeze the UI or crash on lower-memory devices.
- Test Google Drive backup, restore, manual Sync and automatic sync behavior.
- Test large-library browsing/search/calendar behavior; target at least 100 books, and synthetic/engineering testing toward 1,000 where practical.
- Verify EPUB/PDF system File Share still works.

If any test fails, fix on a new branch from current `main`, increment `versionCode` if the uploaded Play code is already consumed, rebuild/sign/verify, and repeat Closed Testing.

## 10. Production promotion rule

Only after Closed Testing passes:

- move/merge the accepted v63 code to the stable release line,
- keep `main` and release docs synchronized,
- upload/promote the exact verified AAB through Play Console,
- re-check Data safety, account deletion URL, privacy policy and store listing before Production,
- keep the previous known-good artifacts and signing recovery kit permanently.

Never regenerate the Android signing identity for an update.

## 11. Data that must survive every update

Preserve:

- local EPUB/PDF files
- reading progress and resume location
- finished timestamps/history
- Reading Calendar and statistics/streaks
- shelves/custom shelves
- notes/highlights/Reading Memory
- custom fonts/typography/settings
- dictionary settings/data
- Firebase Google account state
- Drive `appDataFolder` backup/restore/sync compatibility

Do not add a destructive database reset or preference wipe merely to simplify migration.

## 12. iOS note

The existing SwiftUI iOS handoff remains a separate older foundation around Android-era v2.19.1/61. Do not claim it has automatic parity with the Android v63 changes. Any iOS release must explicitly port/test required v63 behavior and still requires macOS/Xcode plus the publisher’s Apple signing credentials.

## 13. New-chat prompt

Paste this into a new chat:

> Continue the WoW Reader Google Play update from `whispermmepub/wow-reader-lab`. Read `NEXT_CHAT_HANDOFF.md`, `README.md`, `docs/STORE_PUBLICATION_STATUS.md`, and `SIGNING.md` first. Current Android candidate is v2.19.3 / versionCode 63 / package `com.whisper.wowreader`. The verified Play AAB is `WoW-Reader-v2.19.3-v63-PlayStore-production.aab` with SHA-256 `3587777e5f9586ca112bd92cbee65e74c82b0b29f615167da075f75d584e35ae`. Production and Play App Signing SHA-1 is `21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`. Preserve the signing identity and all user data. Continue from Closed Testing through runtime verification and only then Production. Do not redesign or regenerate keys unless a test proves a change is required.
