# WoW Reader — Android + iOS release handoff

This document complements `NEXT_CHAT_HANDOFF.md` and records the minimum release/update contract for both platforms.

## Android stable baseline

Current trusted Android release:

- versionName: `2.19.1`
- versionCode: `61`
- applicationId: `com.whisper.wowreader`
- minSdk: 23
- targetSdk: 36
- source: current aligned `main` / `stable/v61` / `stable/v61-file-share`
- original clean v61 baseline commit: `58f39edafe7b9a1bab9d0e4bbe39d31930056c05`

v61 is the approved v60 line plus actual EPUB/PDF system File Share. No WoW Audio handoff belongs in this baseline.

### Android update contract

Every accepted Android update must preserve:

- package `com.whisper.wowreader`
- original production signing identity
- monotonically increasing `versionCode`
- existing library/book files
- progress/history
- shelves
- notes/highlights/Reading Memory
- Reading Calendar
- SharedPreferences/settings
- Firebase sign-in and Drive `appDataFolder` backup/restore/auto-sync

Do not release old experimental v61/v62 artifacts.

### Android signing identity

- Alias: `wowreader-production`
- SHA-1: `21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`
- SHA-256: `29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`

Private keystore/password material stays outside the public repository.

### Verified v61 artifacts

- AAB: `WoW-Reader-v2.19.1-v61-PlayStore-production.aab`
  - SHA-256 `67eb5d6270e5ca6ba353fc6c1a9f7e5eb6643fb80a1a4277a47d2f0664b790db`
- APK: `WoW-Reader-v2.19.1-v61-production-signed-file-share.apk`
  - SHA-256 `32fb93eb0d8c912847d626369a735ac55a3ad683984010dcc28b6fa96fec2090`

## iOS native handoff baseline

Current iOS handoff archive:

- `WoW-Reader-iOS-v1.0-AppStore-Handoff.zip`
- SHA-256 `d62efd5272ec1d751d0d3b124bd34ae5d6ece2e7eec0340eae165fbdd6705c7e`

Project identity:

- Xcode project: `WoWReader.xcodeproj`
- marketing version: `2.19.1`
- build: `61`
- bundle ID: `com.whisper.wowreader` by default
- iOS deployment target: 16.0
- iPhone + iPad
- SwiftUI / Swift 5
- Xcode project target: Xcode 26
- ZIPFoundation: 0.9.20
- signing style: Automatic

Implemented foundation:

- EPUB import/read
- PDF import/read
- local offline library
- search
- reading progress
- book metadata/shelf/notes
- reading activity summary
- EPUB/PDF share sheet
- app icons
- privacy manifest

## Apple publishing credentials

No Apple private signing credentials are stored in the handoff.

The publisher must provide their own:

- Apple Developer Team ID
- explicit App ID / Bundle ID
- Apple Distribution certificate and private key
- distribution provisioning profile or Automatic Signing
- optional App Store Connect API `.p8` key + Key ID + Issuer ID for automation

Never invent, regenerate or claim recovery of Apple private credentials that were never supplied.

## Cross-platform release rule

Android and iOS may share a marketing version, but each platform has its own build/update identity. Before shipping a new release:

1. Choose the release scope.
2. Branch Android from the current trusted stable head.
3. Decide which changes also need an iOS port.
4. Increment Android `versionCode` above the currently published track.
5. Increment iOS build number above the last App Store/TestFlight build.
6. Preserve Android signing/update compatibility.
7. Validate iOS source and then build/archive/sign on macOS with Xcode.
8. Update README, handoff and release notes after acceptance.

## Source-of-truth warning

At the time this handoff was written, `whispermmepub/wow-reader-app` still contains an older source line. Do not silently switch to it. Use `whispermmepub/wow-reader-lab` until an explicit synchronization is performed.
