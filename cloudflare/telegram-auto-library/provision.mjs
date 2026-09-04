const apiBase = "https://api.cloudflare.com/client/v4";
const cfToken = process.env.CF_TOKEN;
const tgToken = process.env.TG_TOKEN;
const workerName = process.env.CF_WORKER_NAME || "wow-reader-auto-library";
const channelUsername = (process.env.TG_CHANNEL_USERNAME || "TheBookR").replace(/^@/, "");
const dbName = "wow-reader-auto-library";
const rawBase = "https://raw.githubusercontent.com/whispermmepub/wow-reader-lab/main/cloudflare/telegram-auto-library";

if (!cfToken || !tgToken) throw new Error("Missing provisioning credentials");

const authHeaders = { Authorization: `Bearer ${cfToken}` };

async function cf(path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set("Authorization", `Bearer ${cfToken}`);
  const response = await fetch(`${apiBase}${path}`, { ...options, headers });
  const text = await response.text();
  let body = null;
  try { body = JSON.parse(text); } catch { body = { raw: text }; }
  if (!response.ok || body?.success === false) {
    const errors = body?.errors?.map((e) => e.message || e.code).join("; ") || text || response.statusText;
    throw new Error(`Cloudflare ${options.method || "GET"} ${path}: ${response.status} ${errors}`);
  }
  return body;
}

async function tg(method, params = {}) {
  const url = new URL(`https://api.telegram.org/bot${tgToken}/${method}`);
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null) url.searchParams.set(k, typeof v === "string" ? v : JSON.stringify(v));
  }
  const response = await fetch(url);
  const body = await response.json().catch(() => null);
  if (!response.ok || !body?.ok) throw new Error(`Telegram ${method}: ${body?.description || response.statusText}`);
  return body.result;
}

function makeSecret() {
  const bytes = new Uint8Array(24);
  crypto.getRandomValues(bytes);
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function fetchText(url) {
  const response = await fetch(url, { headers: { "cache-control": "no-store" } });
  if (!response.ok) throw new Error(`Fetch failed ${response.status}: ${url}`);
  return response.text();
}

async function main() {
  const verify = await cf("/user/tokens/verify");
  console.log(`CF token: ${verify?.result?.status || "verified"}`);

  const accounts = await cf("/accounts?per_page=50");
  const accountList = Array.isArray(accounts.result) ? accounts.result : [];
  if (!accountList.length) throw new Error("No Cloudflare account visible to this API token");
  const account = accountList[0];
  const accountId = account.id;
  console.log(`Cloudflare account: ${account.name || accountId}`);

  const dbList = await cf(`/accounts/${accountId}/d1/database?per_page=100`);
  let database = (dbList.result || []).find((d) => d.name === dbName);
  if (!database) {
    const created = await cf(`/accounts/${accountId}/d1/database`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ name: dbName }),
    });
    database = created.result;
    console.log("D1: created");
  } else {
    console.log("D1: existing");
  }
  const dbId = database.uuid || database.id;
  if (!dbId) throw new Error("D1 database id missing");

  const schema = await fetchText(`${rawBase}/schema.sql?ts=${Date.now()}`);
  await cf(`/accounts/${accountId}/d1/database/${dbId}/query`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ sql: schema }),
  });
  console.log("D1 schema: ready");

  const workerCode = await fetchText(`${rawBase}/worker.js?ts=${Date.now()}`);
  const webhookSecret = makeSecret();
  const metadata = {
    main_module: "worker.js",
    bindings: [
      { type: "d1", name: "DB", id: dbId },
      { type: "secret_text", name: "TELEGRAM_BOT_TOKEN", text: tgToken },
      { type: "secret_text", name: "TELEGRAM_WEBHOOK_SECRET", text: webhookSecret },
      { type: "plain_text", name: "TELEGRAM_CHANNEL_USERNAME", text: channelUsername },
    ],
  };
  const form = new FormData();
  form.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }), "metadata.json");
  form.append("worker.js", new Blob([workerCode], { type: "application/javascript+module" }), "worker.js");
  await cf(`/accounts/${accountId}/workers/scripts/${workerName}`, { method: "PUT", body: form });
  console.log("Worker: uploaded");

  let subdomain;
  try {
    const sub = await cf(`/accounts/${accountId}/workers/subdomain`);
    subdomain = sub?.result?.subdomain;
  } catch (e) {
    console.log("Workers subdomain: not configured yet");
  }

  if (!subdomain) {
    const base = `wowreader-${accountId.slice(-8).toLowerCase()}`.replace(/[^a-z0-9-]/g, "");
    let lastError;
    for (let i = 0; i < 5 && !subdomain; i++) {
      const candidate = i === 0 ? base : `${base}-${Math.floor(Math.random() * 9000 + 1000)}`;
      try {
        const set = await cf(`/accounts/${accountId}/workers/subdomain`, {
          method: "PUT",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({ subdomain: candidate }),
        });
        subdomain = set?.result?.subdomain || candidate;
      } catch (e) {
        lastError = e;
      }
    }
    if (!subdomain) throw lastError || new Error("Could not configure workers.dev subdomain");
    console.log("Workers subdomain: configured");
  }

  await cf(`/accounts/${accountId}/workers/scripts/${workerName}/subdomain`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ enabled: true, previews_enabled: false }),
  });

  const workerUrl = `https://${workerName}.${subdomain}.workers.dev`;
  const health = await fetch(`${workerUrl}/health`, { headers: { "cache-control": "no-store" } });
  if (!health.ok) throw new Error(`Worker health failed: ${health.status}`);
  console.log(`Worker health: ${health.status}`);

  const me = await tg("getMe");
  console.log(`Telegram bot: @${me.username || "unknown"}`);
  try {
    const member = await tg("getChatMember", { chat_id: `@${channelUsername}`, user_id: me.id });
    console.log(`Channel status: ${member.status || "unknown"}`);
  } catch (e) {
    console.log(`Channel status: unable to verify (${e.message})`);
  }

  await tg("setWebhook", {
    url: `${workerUrl}/telegram/webhook`,
    secret_token: webhookSecret,
    allowed_updates: ["channel_post", "edited_channel_post"],
    drop_pending_updates: false,
  });
  const webhookInfo = await tg("getWebhookInfo");
  console.log(`Telegram webhook: ${webhookInfo.url === `${workerUrl}/telegram/webhook` ? "ready" : "mismatch"}`);
  console.log(`WORKER_URL=${workerUrl}`);
  console.log("Provisioning complete");
}

main().catch((error) => {
  console.error(`PROVISION_ERROR=${error?.message || error}`);
  process.exitCode = 1;
});
