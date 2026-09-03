# WoW Reader production signing

The production keystore is intentionally not stored in this public repository.
Keep the original keystore and its passwords permanently; Android updates must
be signed by the same key.

## Registered certificates

- Existing test SHA-1: `7B:E3:95:61:C7:05:E5:09:5A:2E:EF:4F:A0:BF:80:E7:32:C7:10:91`
- Production SHA-1: `21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`
- Production SHA-256: `29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`
- Key alias: `wowreader-production`

Keep the test fingerprint in Firebase and add both production fingerprints to
the `com.whisper.wowreader` Android app.

## Local signed builds

Set these environment variables without committing their values:

- `WOW_RELEASE_STORE_FILE`: absolute path to the production JKS
- `WOW_RELEASE_STORE_PASSWORD`
- `WOW_RELEASE_KEY_ALIAS`
- `WOW_RELEASE_KEY_PASSWORD`

Then run:

```bash
gradle :app:clean :app:assembleRelease :app:bundleRelease :app:lintRelease
```

## GitHub Actions signed builds

The manually dispatched production workflow expects these repository secrets:

- `WOW_RELEASE_KEYSTORE_BASE64`
- `WOW_RELEASE_STORE_PASSWORD`
- `WOW_RELEASE_KEY_ALIAS`
- `WOW_RELEASE_KEY_PASSWORD`

Never commit a keystore, its Base64 form, or its passwords. A test build signed
with another certificate cannot be upgraded in place to the production build;
uninstall it once before installing the first production-signed APK.
