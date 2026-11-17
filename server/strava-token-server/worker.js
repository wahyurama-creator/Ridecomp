// RideComp Strava token server (Cloudflare Worker, D-82).
//
// Strava requires the app's client secret to trade an authorization code for tokens, to refresh them and
// to revoke them, and offers no PKCE. The secret therefore lives here, as Worker secrets
// (STRAVA_CLIENT_ID, STRAVA_CLIENT_SECRET), never in the app or the repository. This server stores
// nothing and logs nothing: it forwards one request to Strava and returns only what the app needs.
// Uploads do not pass through here; the app sends them to Strava with the rider's own access token.

const STRAVA_TOKEN_URL = "https://www.strava.com/oauth/token";
const STRAVA_REVOKE_URL = "https://www.strava.com/oauth/revoke";
const FORM_TYPE = "application/x-www-form-urlencoded";
const MAX_BODY_BYTES = 2048;
// Codes and tokens are short hex strings; anything else is refused before reaching Strava.
const VALUE_PATTERN = /^[A-Za-z0-9._~-]{1,512}$/;

const ROUTES = {
  "/token": (form, env) => tokenRequest(env, { grant_type: "authorization_code", code: form.get("code") }),
  "/refresh": (form, env) =>
    tokenRequest(env, { grant_type: "refresh_token", refresh_token: form.get("refresh_token") }),
  "/revoke": (form, env) => revoke(env, form.get("token")),
};

export default {
  async fetch(request, env) {
    const route = ROUTES[new URL(request.url).pathname];
    if (!route) return reply(404, { error: "not_found" });
    if (request.method !== "POST") return reply(405, { error: "method" });
    if (!env.STRAVA_CLIENT_ID || !env.STRAVA_CLIENT_SECRET) return reply(500, { error: "not_configured" });
    const form = await readForm(request);
    if (!form) return reply(400, { error: "bad_request" });
    try {
      return await route(form, env);
    } catch {
      // Strava unreachable: the app keeps the ride queued and retries.
      return reply(502, { error: "upstream" });
    }
  },
};

async function readForm(request) {
  if (!(request.headers.get("content-type") || "").startsWith(FORM_TYPE)) return null;
  const length = Number(request.headers.get("content-length") || 0);
  if (length > MAX_BODY_BYTES) return null;
  const text = await request.text();
  return text.length > MAX_BODY_BYTES ? null : new URLSearchParams(text);
}

async function tokenRequest(env, params) {
  const value = params.code ?? params.refresh_token;
  if (!valid(value)) return reply(400, { error: "bad_request" });
  const response = await fetch(STRAVA_TOKEN_URL, {
    method: "POST",
    headers: { "content-type": FORM_TYPE },
    body: new URLSearchParams({
      client_id: env.STRAVA_CLIENT_ID,
      client_secret: env.STRAVA_CLIENT_SECRET,
      ...params,
    }),
  });
  if (!response.ok) return reply(upstreamStatus(response.status), { error: "strava" });
  const data = await response.json();
  // Only the tokens and the display name: the app needs nothing else from the athlete profile.
  return reply(200, {
    access_token: data.access_token,
    refresh_token: data.refresh_token,
    expires_at: data.expires_at,
    ...(data.athlete
      ? { athlete: { firstname: data.athlete.firstname ?? "", lastname: data.athlete.lastname ?? "" } }
      : {}),
  });
}

async function revoke(env, token) {
  if (!valid(token)) return reply(400, { error: "bad_request" });
  const basic = btoa(`${env.STRAVA_CLIENT_ID}:${env.STRAVA_CLIENT_SECRET}`);
  const response = await fetch(STRAVA_REVOKE_URL, {
    method: "POST",
    headers: { "content-type": FORM_TYPE, authorization: `Basic ${basic}` },
    body: new URLSearchParams({ token, token_type_hint: "refresh_token" }),
  });
  return response.ok ? reply(200, {}) : reply(upstreamStatus(response.status), { error: "strava" });
}

/** Strava's refusal of a code or token is 401 for the app; limits and outages keep their meaning. */
function upstreamStatus(status) {
  if (status === 429) return 429;
  if (status >= 500) return 502;
  return 401;
}

function valid(value) {
  return typeof value === "string" && VALUE_PATTERN.test(value);
}

function reply(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store" },
  });
}
