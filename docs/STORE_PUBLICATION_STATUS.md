# WoW Reader — Store Publication Status

## Google Play

- **Last owner-confirmed live release:** versionName `2.20.0`, versionCode `64`
- **Prepared owner-accepted update:** versionName `2.21.6`, versionCode `72`
- **Package:** `com.whisper.wowreader`
- **Status:** v72 is prepared/accepted but has **not** been uploaded to Google Play. The owner will upload it personally.

Before any upload, the owner must check that versionCode `72` is available across **all Play tracks**. This repository cannot establish that availability, and acceptance of the update or automated validation is not a production guarantee. Device, account, Drive, OEM, and Play review behavior still require owner verification.

## v72 scope

v72 is the cumulative update from live v64 through v72. It includes smoother chapter switching; preservation of original EPUB formatting with global Original EPUB/Reader typography settings; larger adaptive previews; reminder and home improvements; and Drive restore recovery for ghost cards, hash-based titles, and deletion handling. It also includes stability and large-library/incremental-sync foundations carried forward from v64.

The v72 recovery work must not be described as repairing unrecoverable metadata. Physical-phone and live-Drive validation are not complete, and all phones have not been tested.

## Release checks for the owner

- Confirm versionCode `72` is unused across every Play track before upload.
- Upload the owner-accepted, production-signed AAB through Play Console; this repository does not upload it.
- Verify the in-place update from the live v64 install without uninstalling or clearing app data.
- Verify Google sign-in, Drive restore, books, progress, notes, shelves, history, reminders, home behavior, chapter transitions, previews, and global typography settings on representative devices.
- Treat any successful host/build validation as supporting evidence, not a guarantee of production behavior or Play acceptance.

## Identity

- **Package:** `com.whisper.wowreader`
- **Category:** Books & Reference
- **Minimum Android:** 6.0 / API 23
- **Target SDK:** 36

Keep the original production App Signing identity and Firebase/OAuth registration unchanged. Do not commit private signing material or build binaries.

Production SHA-1:

`21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`

## Previous baseline

v64 / versionCode 64 remains the last owner-confirmed live and rollback/reference baseline.
