// Run with: node --test server/strava-token-server/ (Node 18+, no dependencies).
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "./worker.js";

const env = { STRAVA_CLIENT_ID: "12345", STRAVA_CLIENT_SECRET: "secret" };
const calls = [];

function stravaReplies(status, body) {
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init });
    return new Response(JSON.stringify(body), { status });
  };
}

function post(path, form, headers = {}) {
  return new Request(`https://token.example${path}`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded", ...headers },
    body: new URLSearchParams(form).toString(),
  });
}

test("code exchange adds the secret and returns only tokens and name", async () => {
  calls.length = 0;
  stravaReplies(200, {
    access_token: "a1", refresh_token: "r1", expires_at: 99,
    athlete: { firstname: "Budi", lastname: "S", city: "Jakarta", weight: 70 },
  });
  const res = await worker.fetch(post("/token", { code: "abc123" }), env);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), {
    access_token: "a1", refresh_token: "r1", expires_at: 99, athlete: { firstname: "Budi", lastname: "S" },
  });
  const sent = new URLSearchParams(calls[0].init.body);
  assert.equal(calls[0].url, "https://www.strava.com/oauth/token");
  assert.equal(sent.get("client_secret"), "secret");
  assert.equal(sent.get("grant_type"), "authorization_code");
  assert.equal(sent.get("code"), "abc123");
});

test("refresh and revoke", async () => {
  calls.length = 0;
  stravaReplies(200, { access_token: "a2", refresh_token: "r2", expires_at: 100 });
  const refreshed = await worker.fetch(post("/refresh", { refresh_token: "r1" }), env);
  assert.equal((await refreshed.json()).refresh_token, "r2");
  assert.equal(new URLSearchParams(calls[0].init.body).get("grant_type"), "refresh_token");

  const revoked = await worker.fetch(post("/revoke", { token: "r2" }), env);
  assert.equal(revoked.status, 200);
  assert.equal(calls[1].url, "https://www.strava.com/oauth/revoke");
  assert.equal(calls[1].init.headers.authorization, `Basic ${btoa("12345:secret")}`);
});

test("Strava refusals, limits and outages map to what the app understands", async () => {
  stravaReplies(400, { message: "Bad Request" });
  assert.equal((await worker.fetch(post("/token", { code: "x" }), env)).status, 401);
  stravaReplies(429, {});
  assert.equal((await worker.fetch(post("/refresh", { refresh_token: "x" }), env)).status, 429);
  stravaReplies(503, {});
  assert.equal((await worker.fetch(post("/token", { code: "x" }), env)).status, 502);
  globalThis.fetch = async () => { throw new Error("down"); };
  assert.equal((await worker.fetch(post("/token", { code: "x" }), env)).status, 502);
});

test("bad requests never reach Strava", async () => {
  calls.length = 0;
  stravaReplies(200, {});
  const get = new Request("https://token.example/token");
  assert.equal((await worker.fetch(get, env)).status, 405);
  assert.equal((await worker.fetch(post("/other", { code: "x" }), env)).status, 404);
  assert.equal((await worker.fetch(post("/token", { code: "a b<script>" }), env)).status, 400);
  assert.equal((await worker.fetch(post("/token", {}), env)).status, 400);
  assert.equal((await worker.fetch(post("/token", { code: "x".repeat(3000) }), env)).status, 400);
  const json = new Request("https://token.example/token", { method: "POST", body: "{}", headers: { "content-type": "application/json" } });
  assert.equal((await worker.fetch(json, env)).status, 400);
  assert.equal((await worker.fetch(post("/token", { code: "x" }), {})).status, 500);
  assert.equal(calls.length, 0);
});
