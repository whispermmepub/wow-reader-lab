# WoW Reader handoff

Production stable: v2.18.1 / versionCode 51, user approved in previous chat.
Current Lab candidate: v2.18.2 / versionCode 52 on `fix/v52-unified-install`.
Do not promote this candidate without new user real-device approval.

Package: `com.whisper.wowreader` (unchanged).
Original signing certificate SHA-256:
`29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`
Never generate a replacement signing key. Increment versionCode above the latest candidate for new releases.

## v52 fixes
- Footnote destination word-boundary regex corrected.
- Preview requests snapshot their href/label/spine and reject stale callbacks.
- Old backlink completion cannot clear a newer navigation session.
- Legacy WebViews receive prefixed CSS column properties for Page mode.
- Smart Sync merges calendar dates/books independently. Existing local note conflicts remain local.
- Empty note deletion markers prevent a local deletion from being resurrected by Smart Sync.

Existing preferences and files stay in the same locations; no destructive migration or reset.
Missing calendar preferences still initialize as empty objects. Empty note markers remain readable by older versions.

One original-production-signed APK serves both fresh install and in-place update.
CI source build, Android lint and debug reader regression are separate from original-signed install tests.
See `.github/workflows/verify-signed-v52.yml` and its run artifacts for exact results.
Private signing material is not in this repository. The verification deltas contain only already-signed APK bytes.
Artifact-backed installation replay requires the referenced CI artifacts before their retention expires.
Google authentication and live Drive network sync need account/device checks; emulator merge tests do not prove those services.

This chat's abandoned v49 draft was never applied. v51 is the base of this branch.
