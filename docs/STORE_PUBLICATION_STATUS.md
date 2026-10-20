# WoW Reader — Store Publication Status

## Google Play: v77 live / published

- **Current source on `main`:** versionName `2.21.11`, versionCode `77`.
- **Package:** `com.whisper.wowreader`.
- **Publication:** **live / published**, confirmed by the owner on **2026-10-20**: v77 has been published and is publicly available. This supersedes the earlier accepted-only / publication-unconfirmed status.
- **Play Store:** [WoW Reader](https://play.google.com/store/apps/details?id=com.whisper.wowreader).
- **Latest owner-confirmed published release:** versionName `2.21.11`, versionCode `77`. Publication is recorded from the owner's confirmation; no Play Console track, rollout percentage or regional availability was independently inspected.
- **Next app release:** versionCode **`78` or higher**; check availability across all Play tracks. Do not reuse `77` for a new release.

## Why the minimum Android version changed

Play Console required: **“Play automatic protection requires minimum SDK version 24 or higher”**. The accepted v77 AAB uses minSdk `24` (Android 7.0+). **Android 6.0 / API 23 support is intentionally dropped in v77.** Do not revert to minSdk 23 for this release.

## Source alignment and signing

The v77 source is already on `main` at commit `bfaefb102f9a385a588320bb20264d367b0a2e9e`. That source-alignment commit changed `app/build.gradle` from the owner-tested v76 baseline (`a44c97db3cc07fe24e078d53c6e074b40c314d4d`) to minSdk `24`, versionCode `77` and versionName `2.21.11`, plus release/handoff documentation. It introduced no other app behavior changes. This publication-status update changes documentation only; app code and version values remain unchanged.

The existing v77 AAB uses the same production upload signing certificate as the v64 Play Store AAB. Keep the package, signing identity and Firebase/OAuth registration unchanged. Never commit signing material, APK/AAB binaries or build outputs.

This task is authorized only to commit and push documentation to GitHub `main`. It builds no APK/AAB and performs no Play upload or publication; the owner has already published v77. It does not independently reproduce or revalidate the existing v77 bundle; historical automated test evidence is not a fresh v77 test run.

## Before any future publication

The owner handles Play Console. Confirm the actual track/review/rollout status there rather than inferring it from bundle acceptance. Verify an in-place update without uninstalling or clearing data, including Google sign-in, Drive restore/cancellation/retry, books/titles, progress, notes, shelves, history and reader behavior on supported devices.

See [NEXT_CHAT_HANDOFF.md](../NEXT_CHAT_HANDOFF.md) for historical changes, validation limits and continuation context.
