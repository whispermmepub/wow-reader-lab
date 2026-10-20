# WoW Reader — Store Publication Status

## Google Play: v77 accepted, publication unconfirmed

- **Current source on `main`:** versionName `2.21.11`, versionCode `77`.
- **Package:** `com.whisper.wowreader`.
- **Owner-reported outcome:** Play Console accepted the existing v77 AAB after the owner added the `.aab` filename extension.
- **Publication:** not confirmed. Acceptance of an uploaded bundle is not evidence of a published release or rollout. No Play track was inspected in this source-alignment task.
- **Last owner-confirmed published release in this record:** versionName `2.20.0`, versionCode `64`.
- **Next app release:** versionCode **`78` or higher**; check availability across all Play tracks. Do not reuse `77` for a new release.

## Why the minimum Android version changed

Play Console required: **“Play automatic protection requires minimum SDK version 24 or higher”**. The accepted v77 AAB uses minSdk `24` (Android 7.0+). **Android 6.0 / API 23 support is intentionally dropped in v77.** Do not revert to minSdk 23 for this release.

## Source alignment and signing

Previous `main` was `a44c97db3cc07fe24e078d53c6e074b40c314d4d`, the owner-tested v76 / 2.21.10 Drive restore recovery and initialization fixes, with minSdk `23`. This alignment changes `app/build.gradle` to minSdk `24`, versionCode `77` and versionName `2.21.11`, plus release/handoff documentation. No other app behavior is changed.

The existing v77 AAB uses the same production upload signing certificate as the v64 Play Store AAB. Keep the package, signing identity and Firebase/OAuth registration unchanged. Never commit signing material, APK/AAB binaries or build outputs.

This task is authorized only to commit and push source/documentation to GitHub `main`. It creates no AAB and performs no Play upload or publication. It does not independently reproduce or revalidate the existing v77 bundle; historical automated test evidence is not a fresh v77 test run.

## Before any future publication

The owner handles Play Console. Confirm the actual track/review/rollout status there rather than inferring it from bundle acceptance. Verify an in-place update without uninstalling or clearing data, including Google sign-in, Drive restore/cancellation/retry, books/titles, progress, notes, shelves, history and reader behavior on supported devices.

See [NEXT_CHAT_HANDOFF.md](../NEXT_CHAT_HANDOFF.md) for historical changes, validation limits and continuation context.
