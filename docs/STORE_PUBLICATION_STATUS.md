# WoW Reader — store publication status

This file records the current publication work so a future chat does not confuse store setup with source development.

## Google Play

Current Android release line:

- versionName: `2.19.1`
- versionCode: `61`
- package: `com.whisper.wowreader`
- release file: `WoW-Reader-v2.19.1-v61-PlayStore-production.aab`
- category: Books & Reference
- support email: `aungsoemoe.mm0@gmail.com`
- privacy policy: `https://wowreaderapp.blogspot.com/p/privacy-policy.html`

### Pending publication work

- Publish a dedicated public Account Deletion Blogger page and use its live URL in Play Console.
- Closed testing is being configured.
- The tester opt-in/share link was not yet available when this handoff was written.
- A private tester email list has already been prepared outside the repository. Do not commit those addresses here.
- Re-check the current Play Console testing/account/data-safety requirements at submission time because store policy can change.

### Account / Data Safety context

The Android app uses Google sign-in with Firebase Authentication. Play Console account/data deletion answers must match the app's actual sign-in/account implementation and any deletion flow that is ultimately shipped.

### Signing warning

WoW Reader has existing production-signed sideloaded installs. Preserve update compatibility when configuring Play App Signing if those installs must update directly to the Play release.

## Apple App Store

Current native handoff:

- `WoW-Reader-iOS-v1.0-AppStore-Handoff.zip`
- version `2.19.1`
- build `61`
- default bundle ID `com.whisper.wowreader`
- iPhone + iPad
- iOS 16.0+

The handoff contains no Apple private signing credentials. Final archive/codesign/upload requires the publisher's Apple Developer team in Xcode on macOS.

App Store Connect metadata still needs to be supplied/confirmed by the publisher, including app name, description, category, screenshots, age rating, privacy answers, support URL and review contact details.
