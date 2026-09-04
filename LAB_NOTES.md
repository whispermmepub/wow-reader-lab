# WoW Reader Lab Notes

This repository is the experimental integration track for WoW Reader. The production repository remains separate.

## Working agreement

- **Lab first, production only after explicit approval.**
- Preserve the production package identity `com.whisper.wowreader`.
- Preserve offline EPUB/PDF reading and current reader behavior unless a change is intentionally being tested.
- Preserve Google sign-in, Drive `appDataFolder` backup/restore, and existing sync behavior.
- Prefer local-first data so new features can reuse the current backup/sync path.
- Do not promote a Lab build merely because CI passes; real-device user approval is required.

## Production baseline

- Production repo: `whispermmepub/wow-reader-app`
- Stable release: **v2.17.0**
- `versionCode 40`
- Package: `com.whisper.wowreader`
- Target SDK: 36
- Production main commit at the time v40 was finalized: `afc75339dece9ff175f8d498a9744fc88bbb6a62`

## Current Lab state

- Lab version: **v2.17.1**
- `versionCode 41`
- v41 is based on the production v40 source and adds an **unfinished Telegram Auto Library prototype**.
- This work is intentionally **paused**. Do not assume it is production-ready.

## Telegram Auto Library goal

Source channel: `@TheBookR`

Bot username: `@autoposttoapp_bot`

Desired UX:

1. Admin posts an EPUB in `@TheBookR`.
2. Backend indexes the Telegram document.
3. WoW Reader downloads/imports the EPUB into its own private `files/library` storage using the same ownership/metadata conventions as manual Add Book.
4. The book appears in the normal Library and can be opened immediately.
5. If the user has Google sync enabled, the locally imported book participates in the existing sync flow.

No separate permanent EPUB copy is required on our backend. Telegram remains the original file source; backend storage is intended to hold only catalog/index metadata.

## What is already implemented in v41

- `AutoLibrarySync.java` fetches a remote catalog, downloads EPUBs, validates `META-INF/container.xml`, imports them into the app library, extracts title/author/cover metadata, and marks them as library-owned.
- `SplashActivity` starts Auto Library sync when the app launches.
- Cloudflare Worker/D1 prototype exists under `cloudflare/telegram-auto-library/`.
- D1 schema and Telegram webhook handler are present.
- A provisioning script exists for creating D1, uploading the Worker, enabling a `workers.dev` endpoint, and setting the Telegram webhook.
- `endpoint.txt` is a remote-config placeholder so the Lab APK can learn the Worker URL after provisioning without another app code change.
- Lab build identity was bumped to `2.17.1 / 41`.
- Lab CI was updated to build v41 and check the Auto Library source.

## Important current limitations / blockers

- **Cloudflare provisioning is not complete.** `cloudflare/telegram-auto-library/endpoint.txt` still contains only a placeholder.
- The provided Cloudflare credential was treated as a Global API Key style credential; deployment authentication was not completed before the pause.
- Temporary Railway provisioning attempts were used only as a one-shot bridge. They are not part of the intended final architecture and should not be relied on for the feature.
- Railway auto-deploy for the temporary repo provisioner has been paused.
- The latest v41 GitHub Actions run successfully completed source checks and APK/AAB assembly, but `lintRelease` failed on one v41-specific issue: `AutoLibrarySync.java` calls `URLConnection#getContentLengthLong()`, which requires API 24 while the app supports API 23. Fix this without raising `minSdk` (for example by parsing the `Content-Length` header with API-23-safe code), then rerun CI.
- Current Auto Library prototype has a **20 MB EPUB auto-import limit**.
- Current prototype sync is triggered on app launch. It is not yet an always-immediate background push implementation; FCM/background delivery can be added later if needed.
- No end-to-end real-device test has been completed for: Telegram post → Worker catalog → app download → Library import → Google sync.

## Security / secrets

Do **not** place any of these in GitHub:

- Telegram bot token
- Cloudflare API token / Global API Key
- Cloudflare account login email when it is only needed for authentication
- Firebase server-side credentials
- Release keystore or signing passwords

Credentials were provided during setup attempts but must remain outside source control. New chats should ask the user to place valid credentials in the selected secret store rather than copying them into source files.

## Resume order

Read `NEXT_CHAT_HANDOFF.md` first. Then:

1. Fix the API-23 lint issue in `AutoLibrarySync.java` and get Lab CI green.
2. Verify the bot is still an admin of `@TheBookR` and can receive `channel_post` updates.
3. Provision Cloudflare Worker + D1 using valid secrets, without Railway as a permanent dependency.
4. Confirm `/health` and `/api/books` work.
5. Set the Telegram webhook and verify it points to the Worker.
6. Write the final Worker base URL into `cloudflare/telegram-auto-library/endpoint.txt`.
7. Build a production-signed **Lab v41** APK using the same app package/signing lineage used for earlier Lab tests.
8. Test on a real phone with a newly posted EPUB in `@TheBookR`.
9. Verify duplicate handling, offline/retry behavior, Library metadata/cover, reading, and Google sync.
10. Only after explicit user approval, plan the production promotion. Do not modify `wow-reader-app` before that approval.
