# WoW Reader — new chat handoff

Use this file as the **first source of truth** when continuing WoW Reader work in a new chat.

## 1. Current trusted Android release

- Product: **WoW Reader / WoW Reader Lab**
- Version: **2.19.1**
- `versionCode`: **61**
- Package / applicationId: `com.whisper.wowreader`
- minSdk: 23
- targetSdk: 36
- Java: 17
- Repository: `whispermmepub/wow-reader-lab`
- Current source-of-truth branches: `main`, `stable/v61`, `stable/v61-file-share`
- Original clean v61 baseline commit: `58f39edafe7b9a1bab9d0e4bbe39d31930056c05`

The three source-of-truth branches are intentionally kept aligned. Future feature work should branch from the current aligned v61 head, not from the older `wow-reader-app` repository.

### Important repository note

`whispermmepub/wow-reader-app` still contains an older production-era source line and must **not** be treated as the current source of truth unless it is explicitly synchronized later. The current release source is `whispermmepub/wow-reader-lab`.

## 2. Exact v61 definition

Trusted v61 = trusted v60 baseline + **actual EPUB/PDF File Share** + version bump to 61.

Android File Share uses:

- `Intent.ACTION_SEND`
- `EXTRA_STREAM`
- `ClipData`
- `FLAG_GRANT_READ_URI_PERMISSION`
- `FileProvider`
- EPUB MIME: `application/epub+zip`
- PDF MIME: `application/pdf`
- fallback MIME: `application/octet-stream`

**Do not reintroduce WoW Audio handoff/integration into this v61 baseline.** Earlier experimental v61/v62 builds containing WoW Audio handoff are not trusted release artifacts.

## 3. Stable behavior that must survive Android updates

Preserve all existing user data and behavior unless explicitly changing it:

- Offline EPUB/PDF library and local files
- Reading progress/history
- Shelves and custom shelf rename/delete
- Notes, highlights and Reading Memory
- Reading Statistics and streaks
- Myanmar Reading Calendar
- Google/Firebase sign-in
- Google Drive `appDataFolder` backup / restore / auto-sync
- Smart Sync Merge
- Custom fonts and per-book typography
- Myanmar / English dictionary support
- EPUB footnote/endnote navigation fixes
- PDF continuous reading/import behavior
- Multi-book import
- Existing SharedPreferences and settings
- Actual EPUB/PDF File Share

Do not add destructive database/preference resets or migrations that wipe existing users.

## 4. Android production signing identity

Never generate a replacement production key for normal updates.

Safe identity metadata:

- Keystore filename: `wow-reader-production.jks`
- Alias: `wowreader-production`
- Certificate owner: `CN=WoW Reader, OU=Android, O=Whisper Of Words, C=MM`
- SHA-1: `21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`
- SHA-256: `29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`

Private signing passwords/key material are intentionally **not** stored in this public repository. The private recovery archive contains the original JKS, certificate and `SIGNING-RECOVERY.txt`.

Gradle release variables:

- `WOW_RELEASE_STORE_FILE`
- `WOW_RELEASE_STORE_PASSWORD`
- `WOW_RELEASE_KEY_ALIAS`
- `WOW_RELEASE_KEY_PASSWORD`

Earlier GitHub Actions production signing used:

- `WOW_RELEASE_KEYSTORE_BASE64`
- `WOW_RELEASE_STORE_PASSWORD`
- `WOW_RELEASE_KEY_ALIAS`
- `WOW_RELEASE_KEY_PASSWORD`

## 5. Current Android release artifacts

Known verified v61 artifacts:

- `WoW-Reader-v2.19.1-v61-PlayStore-production.aab`
  - SHA-256: `67eb5d6270e5ca6ba353fc6c1a9f7e5eb6643fb80a1a4277a47d2f0664b790db`
- `WoW-Reader-v2.19.1-v61-production-signed-file-share.apk`
  - SHA-256: `32fb93eb0d8c912847d626369a735ac55a3ad683984010dcc28b6fa96fec2090`

The APK/AAB were verified against the preserved production signing identity. Use the AAB for Google Play release tracks. Use the production-signed APK for direct install/update testing where appropriate.

For any next Android release:

1. Branch from current trusted v61.
2. Keep package `com.whisper.wowreader`.
3. Increment `versionCode` above 61 after checking all Play tracks.
4. Do **not** reuse an old experimental v62 APK as a release artifact.
5. Build APK + AAB.
6. Sign with the preserved production identity.
7. Verify signer, package, version and update-from-v61 behavior before promotion.

## 6. Firebase / Google identity

- Firebase project ID: `wow-reader`
- Project number: `1027420568326`
- Storage bucket: `wow-reader.firebasestorage.app`
- Android Firebase App ID: `1:1027420568326:android:a0e7e4dc4cddb8b3d2fc87`
- Package: `com.whisper.wowreader`

OAuth clients currently recorded:

- Production Android client: `1027420568326-m9f98kke24it1uvsfdlki6uanvrd2lna.apps.googleusercontent.com`
  - SHA-1: production SHA-1 above
- Older/test Android client: `1027420568326-pdbi6ecrdvv6nhnhj00fskq715e1ugtq.apps.googleusercontent.com`
  - SHA-1: `7B:E3:95:61:C7:05:E5:09:5A:2E:EF:4F:A0:BF:80:E7:32:C7:10:91`
- Web OAuth client: `1027420568326-3504abjnba1vjil1dgl590jctf3sr4pv.apps.googleusercontent.com`

Keep the original `google-services.json` in the private handoff/backup. Do not publish private credentials or service-account secrets.

## 7. Google Play publication state

Current Play line is **2.19.1 / 61**.

Working publication details:

- Category: Books & Reference
- Support email: `aungsoemoe.mm0@gmail.com`
- Privacy policy: `https://wowreaderapp.blogspot.com/p/privacy-policy.html`
- A separate public **Account Deletion** Blogger page is being prepared for the Play Console deletion URL.
- Closed testing is being configured; the tester opt-in link had not yet been received at the time of this handoff.
- Tester email addresses are private and must **not** be committed to this public repository.

Because the app uses Firebase Google sign-in, keep Play Console account/data deletion declarations consistent with the actual implemented sign-in/account behavior. Re-check current Play policy at submission time.

### Play App Signing warning

WoW Reader already has production-signed sideloaded installs. If direct update compatibility from those installs is required, Play App Signing must preserve a compatible app-signing identity. Do not casually replace the existing app signing identity with an unrelated key.

## 8. Current iOS / App Store handoff

A separate native SwiftUI iPhone/iPad handoff exists:

- Archive: `WoW-Reader-iOS-v1.0-AppStore-Handoff.zip`
- SHA-256: `d62efd5272ec1d751d0d3b124bd34ae5d6ece2e7eec0340eae165fbdd6705c7e`
- Xcode project: `WoWReader.xcodeproj`
- Marketing version: `2.19.1`
- Build: `61`
- Default bundle ID: `com.whisper.wowreader`
- Deployment target: iOS 16.0
- Target devices: iPhone + iPad
- SwiftUI / Swift 5
- Xcode project generated for Xcode 26
- Package dependency: ZIPFoundation 0.9.20
- Code signing style: Automatic

Implemented iOS handoff foundation:

- EPUB import/read
- PDF import/read
- Offline local library
- Search
- Reading progress
- Book details / author / shelf / notes
- Reading activity summary
- Actual EPUB/PDF iOS share sheet
- App icon assets
- Privacy manifest

### iOS signing state

The iOS handoff intentionally contains **no Apple private keys, distribution certificates, provisioning profiles, App Store Connect API keys, or Apple account credentials**.

Publishing requires the eventual publisher to supply:

1. Apple Developer Team ID
2. Explicit App ID / Bundle ID (prefer `com.whisper.wowreader` if available in that team)
3. Apple Distribution certificate + private key
4. App Store distribution provisioning profile, or Xcode Automatic Signing
5. Optional CI/App Store Connect API items: `.p8` key, Key ID and Issuer ID

For a future iOS update, increment `CURRENT_PROJECT_VERSION` above 61 and set the intended `MARKETING_VERSION`, then archive/sign/upload on macOS with Xcode. Never invent or replace Apple ownership/signing information.

### iOS parity warning

The iOS project is a native port foundation, not a claim that every Android-only feature is already at full parity. Before each iOS release, compare its implemented behavior with the current Android stable feature set and explicitly decide which features are included.

## 9. Cross-platform update workflow

When the user asks for a new WoW Reader update:

1. Read this handoff first.
2. Check the current `main` / `stable/v61` head before making changes.
3. Treat Android v61 as the current stable product baseline.
4. Make Android changes on a new feature/release branch.
5. Preserve package, signing identity and user data compatibility.
6. Decide whether the same feature should also be ported to the iOS SwiftUI project.
7. Keep Android `versionCode` and iOS build numbers monotonically increasing for their respective stores.
8. Build and verify Android artifacts before promotion.
9. For iOS, perform static/source validation here when possible, but final Xcode build/archive/codesign/App Store upload requires macOS/Xcode and the publisher's Apple signing account.
10. Update this handoff, README and release notes at the end of each accepted release.

## 10. Files the user should keep permanently

Private/offline backups should include:

- `wow-reader-production.jks`
- `wow-reader-production-cert.pem`
- `SIGNING-RECOVERY.txt`
- `google-services.json`
- `WoW-Reader-production-signing-kit.zip`
- v61 production AAB
- v61 production-signed APK
- `WoW-Reader-iOS-v1.0-AppStore-Handoff.zip`
- Store/signing recovery note
- New-chat handoff package

Do not commit raw secrets to GitHub.

## 11. New-chat instruction

A new chat can start with:

> Continue WoW Reader from `whispermmepub/wow-reader-lab`. Read `NEXT_CHAT_HANDOFF.md` first. Treat the current aligned `main` / `stable/v61` line as the trusted Android v2.19.1 (61) File Share baseline. Preserve production signing/update compatibility and all user data. Also use the existing iOS App Store handoff for iPhone/iPad work; do not invent Apple signing credentials. Check current repository state before changing anything.
