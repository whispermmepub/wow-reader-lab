const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "access-control-allow-origin": "*",
};

const MAX_TELEGRAM_BOT_DOWNLOAD = 20 * 1024 * 1024;

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: JSON_HEADERS });
}

function safeFileName(name) {
  const value = String(name || "book.epub").replace(/[\\/:*?"<>|\u0000-\u001f]/g, "_").trim();
  return value || "book.epub";
}

async function telegramApi(env, method, params = {}) {
  const url = new URL(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/${method}`);
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null) url.searchParams.set(key, String(value));
  }
  const response = await fetch(url.toString(), { headers: { "cache-control": "no-store" } });
  const body = await response.json().catch(() => null);
  if (!response.ok || !body || !body.ok) {
    throw new Error((body && body.description) || `Telegram ${method} failed (${response.status})`);
  }
  return body.result;
}

async function handleTelegramWebhook(request, env) {
  const receivedSecret = request.headers.get("X-Telegram-Bot-Api-Secret-Token") || "";
  if (!env.TELEGRAM_WEBHOOK_SECRET || receivedSecret !== env.TELEGRAM_WEBHOOK_SECRET) {
    return json({ ok: false, error: "forbidden" }, 403);
  }

  const update = await request.json().catch(() => null);
  if (!update) return json({ ok: true, ignored: "invalid_json" });

  const post = update.channel_post || update.edited_channel_post;
  if (!post) return json({ ok: true, ignored: "not_channel_post" });

  const username = String(post.chat && post.chat.username || "").toLowerCase();
  const expected = String(env.TELEGRAM_CHANNEL_USERNAME || "TheBookR").replace(/^@/, "").toLowerCase();
  if (!username || username !== expected) {
    return json({ ok: true, ignored: "different_channel" });
  }

  const document = post.document;
  if (!document || !document.file_id || !document.file_unique_id) {
    return json({ ok: true, ignored: "no_document" });
  }

  const fileName = safeFileName(document.file_name || "book.epub");
  if (!fileName.toLowerCase().endsWith(".epub")) {
    return json({ ok: true, ignored: "not_epub" });
  }

  const fileSize = Number(document.file_size || 0);
  const publishedAt = Number(post.date || 0) * 1000;
  const downloadable = fileSize <= MAX_TELEGRAM_BOT_DOWNLOAD ? 1 : 0;
  const now = Date.now();

  await env.DB.prepare(`
    INSERT INTO books (
      channel_id, message_id, file_id, file_unique_id, file_name, mime_type,
      file_size, caption, published_at, downloadable, active, created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
    ON CONFLICT(channel_id, message_id) DO UPDATE SET
      file_id = excluded.file_id,
      file_unique_id = excluded.file_unique_id,
      file_name = excluded.file_name,
      mime_type = excluded.mime_type,
      file_size = excluded.file_size,
      caption = excluded.caption,
      published_at = excluded.published_at,
      downloadable = excluded.downloadable,
      active = 1
  `).bind(
    String(post.chat.id),
    Number(post.message_id),
    String(document.file_id),
    String(document.file_unique_id),
    fileName,
    String(document.mime_type || "application/epub+zip"),
    fileSize,
    String(post.caption || ""),
    publishedAt,
    downloadable,
    now
  ).run();

  return json({ ok: true, accepted: true, downloadable: downloadable === 1 });
}

async function listBooks(request, env) {
  const url = new URL(request.url);
  const afterId = Math.max(0, Number.parseInt(url.searchParams.get("after_id") || "0", 10) || 0);
  const limit = Math.min(200, Math.max(1, Number.parseInt(url.searchParams.get("limit") || "100", 10) || 100));

  const result = await env.DB.prepare(`
    SELECT id, message_id, file_name, mime_type, file_size, caption, published_at, downloadable
    FROM books
    WHERE active = 1 AND id > ?
    ORDER BY id ASC
    LIMIT ?
  `).bind(afterId, limit).all();

  const origin = url.origin;
  const books = (result.results || []).map((row) => ({
    id: Number(row.id),
    message_id: Number(row.message_id),
    file_name: row.file_name,
    mime_type: row.mime_type || "application/epub+zip",
    file_size: Number(row.file_size || 0),
    caption: row.caption || "",
    published_at: Number(row.published_at || 0),
    downloadable: Number(row.downloadable || 0) === 1,
    download_url: `${origin}/api/books/${row.id}/download`,
  }));

  return json({ ok: true, after_id: afterId, books });
}

async function downloadBook(request, env, id) {
  const row = await env.DB.prepare(`
    SELECT id, file_id, file_name, mime_type, file_size, downloadable, active
    FROM books WHERE id = ? LIMIT 1
  `).bind(id).first();

  if (!row || Number(row.active || 0) !== 1) return json({ ok: false, error: "not_found" }, 404);
  if (Number(row.downloadable || 0) !== 1 || Number(row.file_size || 0) > MAX_TELEGRAM_BOT_DOWNLOAD) {
    return json({ ok: false, error: "telegram_bot_download_limit", max_bytes: MAX_TELEGRAM_BOT_DOWNLOAD }, 413);
  }

  const file = await telegramApi(env, "getFile", { file_id: row.file_id });
  if (!file || !file.file_path) return json({ ok: false, error: "telegram_file_path_missing" }, 502);

  const telegramFileUrl = `https://api.telegram.org/file/bot${env.TELEGRAM_BOT_TOKEN}/${file.file_path}`;
  const upstream = await fetch(telegramFileUrl, { headers: { "cache-control": "no-store" } });
  if (!upstream.ok || !upstream.body) {
    return json({ ok: false, error: "telegram_download_failed", status: upstream.status }, 502);
  }

  const headers = new Headers();
  headers.set("content-type", row.mime_type || "application/epub+zip");
  headers.set("content-disposition", `attachment; filename*=UTF-8''${encodeURIComponent(safeFileName(row.file_name))}`);
  headers.set("cache-control", "private, no-store");
  headers.set("x-content-type-options", "nosniff");
  headers.set("access-control-allow-origin", "*");
  if (row.file_size) headers.set("content-length", String(row.file_size));
  return new Response(upstream.body, { status: 200, headers });
}

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);

      if (request.method === "OPTIONS") {
        return new Response(null, {
          status: 204,
          headers: {
            "access-control-allow-origin": "*",
            "access-control-allow-methods": "GET, POST, OPTIONS",
            "access-control-allow-headers": "content-type, x-telegram-bot-api-secret-token",
            "access-control-max-age": "86400",
          },
        });
      }

      if (request.method === "GET" && url.pathname === "/health") {
        return json({ ok: true, service: "wow-reader-auto-library", channel: env.TELEGRAM_CHANNEL_USERNAME || "TheBookR" });
      }

      if (request.method === "POST" && url.pathname === "/telegram/webhook") {
        return await handleTelegramWebhook(request, env);
      }

      if (request.method === "GET" && url.pathname === "/api/books") {
        return await listBooks(request, env);
      }

      const match = request.method === "GET" && url.pathname.match(/^\/api\/books\/(\d+)\/download$/);
      if (match) return await downloadBook(request, env, Number(match[1]));

      return json({ ok: false, error: "not_found" }, 404);
    } catch (error) {
      console.error("wow-reader-auto-library", error && error.stack || error);
      return json({ ok: false, error: "internal_error" }, 500);
    }
  },
};
