# Historical v75 validation note

This note is preserved from the README before the repository-polish pass. It
records v75 preparation only, not current release status or new build/upload
authorization. Local paths are historical references, not public downloads.
For current facts, see [publication status](../STORE_PUBLICATION_STATUS.md).

## Original README note

### v75 validation and delivery
Accepted signed APK and matching AAB are under `/workspace/wow-reader-v75-update/`. The existing validation records 509 passing host tests, zero failures/errors/skips, passing contracts and desktop Chromium reader suites, and lint with 177 warnings but no fatal/errors. Physical Android touch/FPS and live-account restore are not established by those tests; first uncached chapters still need full engine pagination. See `docs/STORE_PUBLICATION_STATUS.md` and `docs/WHATS_NEW_v75.txt`. No Google Play upload is authorized for this workflow.
