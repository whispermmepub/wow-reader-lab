# WoW Reader — Android + iOS Release Handoff

This document complements `NEXT_CHAT_HANDOFF.md` and records the minimum release/update contract for both platforms.

## Android current candidate

- versionName: `2.19.3`
- versionCode: `63`
- applicationId: `com.whisper.wowreader`
- minSdk: 23
- targetSdk: 36
- Java: 17
- current source: `main`
- Play Store release branch: `release/v63-playstore`
- previous stable production baseline: `stable/v61` (`2.19.1 / 61`)

The v63 candidate adds stability/scalability fixes for progress calculation, duplicate imports, Reading Calendar/recap sharing, Google-connected reading stability and larger libraries. It keeps existing user data compatible through non-destructive migration.

### Android update contract

Every accepted Android update must preserve:

- package `com.whisper.wowreader`
- original production signing identity
- monotonically increasing `versionCode`
- local EPUB/PDF files
- reading progress/resume location/history
- shelves
- notes/highlights/Reading Memory
- Reading Calendar/statistics
- settings/custom fonts/typography
- Firebase sign-in
- Google Drive `appDataFolder` backup/restore/sync

Do not use destructive data resets to simplify migrations.

### Android signing identity

- Alias: `wowreader-production`
- SHA-1: `21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`
- SHA-256: `29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`

Play App Signing now uses the same original production identity. Private keystore/password material stays outside the public repository.

### Verified v63 artifacts

- AAB: `WoW-Reader-v2.19.3-v63-PlayStore-production.aab`
  - SHA-256 `3587777e5f9586ca112bd92cbee65e74c82b0b29f615167da075f75d584e35ae`
- APK: `WoW-Reader-v2.19.3-v63-production-signed.apk`
  - SHA-256 `2ca3a1b4487e98c1ae2e0969cb571457dee032b2daff4ec05656d2ab07a3db25`

Artifact app-source commit: `21894db4f10a5ea588e99b4b07dbaba181ecd70f`.

Repository cleanup/documentation may be newer than the artifact source without changing `app/`. If app source changes after this point, rebuild and verify new artifacts before Play upload.

### Android final gate

The v63 candidate must pass real-device Closed Testing before Production, especially:

- update without data loss
- Play-installed Google Sign-In
- long Google-connected reading session without unexpected return to Home
- Telegram duplicate-import regression
- EPUB weighted-progress regression
- large finished-book recap sharing
- Drive backup/restore/sync

## iOS native handoff baseline

The current iOS handoff remains separate from the Android v63 candidate:

- archive: `WoW-Reader-iOS-v1.0-AppStore-Handoff.zip`
- SHA-256: `d62efd5272ec1d751d0d3b124bd34ae5d6ece2e7eec0340eae165fbdd6705c7e`
- Xcode project: `WoWReader.xcodeproj`
- marketing version: `2.19.1`
- build: `61`
- default bundle ID: `com.whisper.wowreader`
- deployment target: iOS 16.0
- iPhone + iPad
- SwiftUI / Swift 5
- ZIPFoundation 0.9.20
- signing style: Automatic

Implemented iOS foundation includes EPUB/PDF import/read, local library, search, reading progress, metadata/shelf/notes, reading activity summary, share sheet, icons and privacy manifest.

The iOS project does **not** automatically contain the Android v63 fixes. Port and test required behavior explicitly.

## Apple publishing credentials

No Apple private signing credentials are stored in the repository/handoff.

The publisher must provide their own:

- Apple Developer Team ID
- explicit App ID / Bundle ID
- Apple Distribution certificate/private key
- App Store provisioning profile or Automatic Signing
- optional App Store Connect `.p8` API key + Key ID + Issuer ID

Never invent or regenerate Apple ownership/signing material that was never supplied.

## Cross-platform release rule

1. Read `NEXT_CHAT_HANDOFF.md` first.
2. Check current `main` and store state before changing anything.
3. Keep Android package/signing identity stable.
4. Check all Play tracks before assigning the next Android `versionCode`.
5. Decide explicitly which Android changes need an iOS port.
6. Keep iOS build numbers monotonically increasing independently.
7. Build/lint/sign/verify Android artifacts after any app-source change.
8. Run Closed Testing before Android Production.
9. Build/archive/sign iOS only on macOS/Xcode with the publisher's credentials.
10. Update README/handoff/store-status docs after each accepted release.

## Source-of-truth warning

`whispermmepub/wow-reader-lab` is the source of truth. The separate `whispermmepub/wow-reader-app` repository is an older source line unless explicitly synchronized later.
