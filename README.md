# WoW Reader Lab

This repository is the **experimental / testing track** for WoW Reader.

> **Working rule:** build and test here first. Only merge to the production repo after explicit real-device approval.
>
> **Next-chat handoff:** read [`NEXT_CHAT_HANDOFF.md`](NEXT_CHAT_HANDOFF.md) before changing anything.

## Repositories

- Lab / testing: `whispermmepub/wow-reader-lab`
- Production / stable: `whispermmepub/wow-reader-app`

The production source of truth is currently **WoW Reader v2.17.0 (versionCode 40)**. Lab is currently **v2.17.1 (versionCode 41)** and contains an unfinished Telegram Auto Library prototype.

## Android identity

- Package: `com.whisper.wowreader`
- Minimum Android: 6.0 / API 23
- Target SDK: 36
- Java: 17

## Current stable feature set carried into Lab

- Offline EPUB/PDF reading
- Google sign-in + private Google Drive `appDataFolder` backup/restore + auto sync
- Reading Statistics / streaks
- Smart Library / shelves
- Notes & Highlights Hub
- Per-book typography
- Smart Sync Merge
- Home / Library / Notes / Explore navigation
- Coming Soon / book-review feed
- Custom App Theme
- `Justify · Normal` and `Justify · Auto spacing`
- Fast chapter transitions with adjacent-chapter preloading
- Multi-book EPUB/PDF import
- Corrected highlight/note text mapping
- System-inset and scroll/font-scaling fixes

## Lab v41 experiment — Telegram Auto Library

Goal: when an EPUB is posted in `@TheBookR`, WoW Reader should receive it into the local Library **as if the user manually added the file**, so the user only needs to open/read it. If Google sync is enabled, the imported local book then participates in the existing sync flow.

Current prototype files:

- `app/src/main/java/com/whisper/wowreader/AutoLibrarySync.java`
- `cloudflare/telegram-auto-library/worker.js`
- `cloudflare/telegram-auto-library/schema.sql`
- `cloudflare/telegram-auto-library/provision.mjs`
- `cloudflare/telegram-auto-library/endpoint.txt`

The Cloudflare endpoint is **not provisioned yet** and `endpoint.txt` is intentionally still a placeholder. The experiment is paused; see `NEXT_CHAT_HANDOFF.md` for exact status, blockers, and resume steps.

## Community links

- Telegram books channel: https://t.me/TheBookR
- Discussion group: https://t.me/+rUiqzi2mdhNiNGZl
- Website: https://saroatsin.com

## Secrets

Never commit Telegram bot tokens, Cloudflare credentials, Firebase server credentials, release keystore files, or signing passwords. Keep them only in secret stores / environment variables.
