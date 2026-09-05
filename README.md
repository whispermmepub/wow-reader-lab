# WoW Reader Lab

This repository is the **experimental / testing track** for WoW Reader.

> **Working rule:** test here first, finalize in production only after explicit real-device approval.

## Repositories

- Lab / testing: `whispermmepub/wow-reader-lab`
- Production / stable: `whispermmepub/wow-reader-app`

## Current state

- Lab: **WoW Reader v2.17.2** (`versionCode 42`)
- Production baseline: **WoW Reader v2.17.0** (`versionCode 40`)
- Package: `com.whisper.wowreader`
- Minimum Android: 6.0 / API 23
- Target SDK: 36
- Java: 17

Lab v42 is based on the approved production v40 line and adds a test-only Myanmar Reading Calendar / Reading Memory experience plus custom shelf rename/delete.

## Important decision

The unfinished **Telegram Auto Library** experiment has been removed from this repository and is **not part of the current app**. Do not reintroduce it unless the user explicitly requests it again.

## Stable feature set carried into Lab

- Offline EPUB/PDF reading
- Google sign-in + private Google Drive `appDataFolder` backup/restore + auto sync
- Reading Statistics / streaks
- Myanmar Reading Calendar with book covers by reading day
- Daily Reading Notes and per-book Reading Memory
- Smart Library / shelves, including custom shelf rename/delete
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

## Development rule

1. Make new changes in Lab first.
2. Build APK/AAB and run lint/smoke checks.
3. Test the signed Lab APK on a real device.
4. Preserve existing working behavior; avoid broad rewrites.
5. Promote to `wow-reader-app` only after explicit user approval.

## Community links

- Telegram books channel: https://t.me/TheBookR
- Discussion group: https://t.me/+rUiqzi2mdhNiNGZl
- Website: https://saroatsin.com
- Book reviews: https://whispermmepub.github.io/Review/

## Secrets

Never commit signing passwords, private keys, keystores, Telegram tokens, Cloudflare credentials, Firebase server credentials, or other secrets.
