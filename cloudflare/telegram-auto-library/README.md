# WoW Reader Telegram Auto Library

Experimental v41 backend for `@TheBookR`.

Flow: Telegram channel EPUB post -> Telegram webhook -> Cloudflare Worker -> D1 catalog -> WoW Reader downloads the EPUB -> existing local Library + Google sync flow.

The EPUB itself is not permanently stored by Cloudflare. The Worker stores only catalog metadata in D1 and streams the file from Telegram when a WoW Reader client requests it.

Required Worker bindings:
- `DB` (D1)
- `TELEGRAM_BOT_TOKEN` (secret text)
- `TELEGRAM_WEBHOOK_SECRET` (secret text)
- `TELEGRAM_CHANNEL_USERNAME` (plain text, `TheBookR`)

API:
- `GET /health`
- `POST /telegram/webhook`
- `GET /api/books?after_id=0`
- `GET /api/books/:id/download`

Telegram Bot API currently limits bot downloads via `getFile` to 20 MB. Larger EPUBs remain catalogued with `downloadable=false` and are skipped by v41 until a larger-file transport is added.
