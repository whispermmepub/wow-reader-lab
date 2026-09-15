# WoW Reader Production Signing

The production keystore is intentionally **not** stored in this public repository. Keep the original keystore and passwords permanently; normal Android updates must continue using the same signing identity.

## Production identity

- Key alias: `wowreader-production`
- Certificate owner: `CN=WoW Reader, OU=Android, O=Whisper Of Words, C=MM`
- SHA-1: `21:17:D3:1E:01:EB:24:EA:E3:FE:4A:26:88:C8:C7:12:CD:76:71:F1`
- SHA-256: `29:FC:A2:9F:8D:B1:84:AA:F5:13:35:EF:BE:A8:C5:0D:51:76:9D:77:48:AE:53:56:17:C2:47:9E:39:89:AC:A5`

Play Console → App integrity → **App signing key certificate SHA-1 is now the same production SHA-1 above**. Keep Firebase/OAuth registration aligned with that identity.

Historical/test SHA-1 kept in Firebase where needed:

`7B:E3:95:61:C7:05:E5:09:5A:2E:EF:4F:A0:BF:80:E7:32:C7:10:91`

Do not confuse the Play **App signing key** with an Upload Key. If Play rejects an upload for signing, check the Upload key certificate separately before changing anything.

## Local production build

Set these environment variables without committing their values:

- `WOW_RELEASE_STORE_FILE`
- `WOW_RELEASE_STORE_PASSWORD`
- `WOW_RELEASE_KEY_ALIAS`
- `WOW_RELEASE_KEY_PASSWORD`

Then build:

```bash
gradle :app:clean :app:assembleRelease :app:bundleRelease :app:lintRelease
```

Afterward verify package, versionCode/versionName, min/target SDK and signer fingerprints. Do not trust build success alone.

## CI secrets, if signed CI is enabled later

Use private repository secrets such as:

- `WOW_RELEASE_KEYSTORE_BASE64`
- `WOW_RELEASE_STORE_PASSWORD`
- `WOW_RELEASE_KEY_ALIAS`
- `WOW_RELEASE_KEY_PASSWORD`

The current generic repository CI intentionally supports unsigned release verification without requiring private signing material in the repository.

## Secret handling

Never commit or paste into public files:

- `.jks` / `.keystore`
- Base64 keystore contents
- store/key passwords
- `SIGNING-RECOVERY.txt`
- private `.pem`, `.key`, `.p8`, `.p12` material

Keep the private signing recovery kit in secure offline backups. Never regenerate the signing identity merely because a build environment changed.
