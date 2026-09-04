# WoW Reader — Next Chat Handoff

**Read this file first in any new chat before modifying the project.**

This file exists so work can resume without relying on chat history.

## 1) Ground rules / source of truth

The user’s working rule is:

> **“ဒီမှာ စမ်း ဟိုမှာ အတည်”** — test here, finalize there.

- Experimental/testing repo: **`whispermmepub/wow-reader-lab`**
- Production/stable repo: **`whispermmepub/wow-reader-app`**
- Do all new work in Lab first.
- Build and test an APK on a real device.
- Do **not** merge/promote to production until the user explicitly says the Lab build is stable/approved.
- Preserve existing working behavior while adding features; avoid broad rewrites.

## 2) Production baseline that must not be disturbed

Production is finalized at:

- App: **WoW Reader v2.17.0**
- `versionCode 40`
- Package: `com.whisper.wowreader`
- `minSdk 23`
- `targetSdk 36`
- Production main commit when v40 was finalized: `afc75339dece9ff175f8d498a9744fc88bbb6a62`

The stable v40 source was promoted from the previously approved Lab line. It is intended to support update installs from the same official signing lineage and fresh installs.

Production signing certificate fingerprints recorded during finalization:

- SHA-1: `21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`
- SHA-256: `29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`

**Never commit signing passwords, private keys, keystores, Telegram tokens, Cloudflare credentials, or Firebase server credentials.**

## 3) Stable product behavior/features already present before v41

Do not accidentally remove/regress these:

- Offline EPUB/PDF reading
- Google account sign-in
- Google Drive `appDataFolder` private backup/restore + auto sync
- Reading Statistics / streaks
- Smart Library / Shelves
- Notes & Highlights Hub
- Per-book Typography
- Smart Sync Merge
- Premium Home/Library UI
- Coming Soon / book-review feed
- Custom App Theme
- `Justify · Normal`
- `Justify · Auto spacing` for Myanmar smart spacing
- Fast chapter transitions via adjacent-chapter preload/warmed WebView
- Multi-book EPUB/PDF import
- Corrected Highlight/Note text-to-DOM mapping
- App-wide system inset handling
- Scroll font scaling fix

Important UX/design preferences already established for WoW Reader:

- compact, premium, book-first UI
- light/white background with dark/navy text and restrained purple accent is the preferred default visual language
- avoid giant stock `AlertDialog`s, giant blank bottom sheets, oversized pills/cards/buttons, and duplicate controls
- keep phones/tablets/landscape/foldables responsive
- Home style remains the compact original/A-style direction
- book action menu should stay a compact floating popup style

## 4) Important fixes/history already embodied in production

These are useful when debugging regressions:

- v32-v36: chapter transition work removed the visible chapter shrink/flash and improved speed with snapshot/preload/warmed WebView handling.
- v37: annotation offsets were corrected so Highlights/Notes map to the selected text; multi-book picker import was added.
- v38: explicit `Justify · Normal` vs `Justify · Auto spacing`, per-book typography, and Custom App Theme were added.
- v39: Coming Soon/blog reading typography and paragraph separation were polished.
- v40: approved stable source was finalized in production.

## 5) Current Lab version

Lab is now:

- **v2.17.1**
- `versionCode 41`
- Package remains `com.whisper.wowreader`
- Based on production v40 source
- Contains an **unfinished Telegram Auto Library prototype**

The experiment is deliberately **paused**. It is not production-ready and has not passed the required end-to-end real-device test.

## 6) Telegram Auto Library — exact desired product behavior

The user wants this behavior:

> When an EPUB file is posted to the Telegram channel, it should appear inside each WoW Reader user’s Library **like the user added the EPUB file manually**. The user should only need to open the book and read it.

Source channel:

- `@TheBookR`
- `https://t.me/TheBookR`

Telegram bot username:

- `@autoposttoapp_bot`

Do **not** put the bot token in source control.

Desired logical flow:

`@TheBookR EPUB post → Telegram bot/webhook → Cloudflare Worker → D1 catalog metadata → WoW Reader download/import → normal local Library → existing Google sync if enabled`

The user does **not** want a separate permanent copy of every EPUB stored by us after delivery. Telegram is intended to remain the original file source. D1 only needs metadata/index data such as Telegram chat/message/file IDs, filename, size, date, etc.

## 7) Why Railway is not the permanent backend

The user’s Railway usage is temporary/free-trial based and accounts may change. Therefore this feature must **not** depend permanently on Railway.

Intended final backend:

- Cloudflare Worker
- Cloudflare D1
- Telegram as EPUB source
- optional FCM/background improvements later

Temporary Railway provisioning attempts were only used as a bridge while trying to create/configure Cloudflare resources. They are not the final architecture.

The temporary Railway repo provisioner has been paused from automatic redeploys. Do not treat Railway as authoritative state for this feature.

## 8) v41 implementation currently in the repo

### Android side

`app/src/main/java/com/whisper/wowreader/AutoLibrarySync.java`

Current behavior includes:

- Reads Worker endpoint from remote config file `cloudflare/telegram-auto-library/endpoint.txt` via GitHub raw content.
- Requests `/api/books?after_id=...` from the Worker.
- Maintains a local cursor `auto_library_last_id`.
- Downloads eligible EPUBs.
- Has a current 20 MB auto-import limit.
- Validates that the downloaded file is a ZIP containing `META-INF/container.xml`.
- Moves the EPUB into the app’s private `files/library` directory.
- Reuses EPUB summary parsing for title/author/cover.
- Writes the same style of `library_owned_`, `library_title_`, `library_author_`, `added_at_`, and sync-related preferences expected by the normal Library flow.
- Handles filename conflicts and basic duplicate/cursor behavior.

`SplashActivity.java`

- Calls `AutoLibrarySync.sync(...)` on app launch.

**Important:** the current v41 prototype is **launch-triggered polling/sync**, not yet guaranteed immediate background delivery while the app is closed. If the user later wants “instant even while closed”, add FCM/background worker behavior carefully after the basic end-to-end path is stable.

### Backend side

Directory:

`cloudflare/telegram-auto-library/`

Important files:

- `worker.js` — Worker routes/webhook/catalog/download proxy behavior
- `schema.sql` — D1 schema
- `provision.mjs` — one-shot provisioning helper
- `Dockerfile` — temporary provisioning runner image
- `endpoint.txt` — Worker base URL remote config; currently still a placeholder

The provisioning helper is intended to:

1. authenticate to Cloudflare
2. create/find D1 database `wow-reader-auto-library`
3. apply schema
4. upload Worker `wow-reader-auto-library`
5. bind D1 + Telegram secrets
6. enable `workers.dev`
7. verify `/health`
8. configure Telegram webhook for `channel_post` / `edited_channel_post`

## 9) Exact pause point / known blockers

### Cloudflare

Provisioning did **not** complete.

- `cloudflare/telegram-auto-library/endpoint.txt` still contains only the placeholder comment.
- A Cloudflare credential was supplied during the setup attempt, but authentication/provisioning was not completed before the user paused the work.
- Secrets must not be copied into GitHub.
- When resuming, prefer a properly scoped Cloudflare API Token. If using a Global API Key style credential, authentication requires the corresponding account email too; keep both only in a secret store.

### Railway provisioning attempts

There were temporary `cf-provisioner` / `cf-provisioner-repo` experiments. One attempt failed because an image-backed Railway service ignored the intended start command; a repo/Dockerfile route was then tried. These attempts are not important to the final product except as history.

Do not spend time repairing Railway unless it is explicitly needed as a one-shot helper. The target remains Railway-independent Cloudflare infrastructure.

### Lab CI

The latest v41 GitHub Actions run at the pause point had:

- source checks: **passed**
- release APK/AAB assembly: **passed**
- Android lint: **failed** on one v41-specific API compatibility error

Exact lint error:

`AutoLibrarySync.java` calls `URLConnection#getContentLengthLong()`, which requires API 24 while the app supports API 23.

Fix it without increasing `minSdk`. A simple safe direction is to read/parse the `Content-Length` header with API-23-compatible APIs, then rerun CI.

There was also a Kotlin metadata warning from Firebase Auth during build/lint, but assembly succeeded; do not confuse that warning with the actual v41 lint blocker above.

## 10) Resume checklist — do this in this order

1. **Read this file and `LAB_NOTES.md`.**
2. Confirm no work is being done directly in `wow-reader-app`.
3. Fix the API-23 lint issue in `AutoLibrarySync.java` without changing `minSdk 23`.
4. Run/verify Lab CI: source checks, release APK/AAB assembly, lint, smoke checks.
5. Verify `@autoposttoapp_bot` is still an admin of `@TheBookR` and can receive channel posts.
6. Put current valid Telegram/Cloudflare credentials in a secret store only; never GitHub.
7. Provision Cloudflare Worker + D1 directly or with a disposable helper.
8. Verify Worker `/health`.
9. Verify Worker `/api/books` returns catalog JSON.
10. Configure Telegram webhook to the Worker and verify webhook status.
11. Post a **new test EPUB** to `@TheBookR` and confirm D1 catalog receives it.
12. Write the confirmed Worker base URL into `cloudflare/telegram-auto-library/endpoint.txt`.
13. Build a **production-signed Lab v41 APK** using the same official signing lineage used in prior Lab testing; never expose signing secrets.
14. Install/update on a real phone.
15. Launch WoW Reader and verify the newly posted EPUB downloads/imports automatically into the normal Library.
16. Open/read the EPUB; verify title, author, cover, pagination/reader behavior.
17. Test duplicate post/duplicate filename handling.
18. Test offline → reconnect retry behavior.
19. Test Google-connected user sync after auto import, then verify restore/sync behavior as appropriate.
20. Only after the user explicitly approves v41 as stable should a production promotion be planned.

## 11) What NOT to do on resume

- Do not immediately merge Lab v41 to production.
- Do not raise `minSdk` just to silence lint.
- Do not commit any token/key/email/password/keystore.
- Do not move EPUB storage permanently to Railway.
- Do not force every user to click a Telegram “Add to App” button; that is not the requested UX.
- Do not redesign the entire app while implementing Auto Library.
- Do not claim real-device success until the user actually tests it.

## 12) Definition of success for this feature

A successful Lab test is:

1. A new EPUB is posted to `@TheBookR`.
2. Backend detects/indexes it.
3. WoW Reader receives/downloads/imports it with no manual file picker.
4. It appears as a normal local Library book.
5. User opens it and reads normally.
6. Existing Google sync works afterward for users who have Google connected.
7. Existing production features remain unaffected.

Only then should the user decide whether to promote it to production.
