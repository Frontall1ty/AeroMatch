// Run with: npm test  (Node built-in runner with type stripping; no new deps)
import { afterEach, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import { apiFetch, apiV1, resetCsrfToken, ApiRequestError } from "./client.ts";

type Call = { url: string; init?: RequestInit };
const realFetch = globalThis.fetch;
let calls: Call[];
let queue: Response[];

const json = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), { status });
const csrf = (token: string) =>
  json(200, { headerName: "X-XSRF-TOKEN", token });

beforeEach(() => {
  calls = [];
  queue = [];
  resetCsrfToken();
  globalThis.fetch = (async (url: string, init?: RequestInit) => {
    calls.push({ url, init });
    const next = queue.shift();
    if (!next) throw new Error("unexpected fetch " + url);
    return next;
  }) as typeof fetch;
});
afterEach(() => {
  globalThis.fetch = realFetch;
});

const header = (c: Call, name: string) =>
  new Headers(c.init?.headers).get(name);

test("apiV1 builds relative URLs", () => {
  assert.equal(apiV1("/quotes"), "/api/v1/quotes");
  assert.equal(apiV1("quotes"), "/api/v1/quotes");
});

test("GET sends no CSRF header and uses same-origin credentials", async () => {
  queue.push(json(200, { ok: true }));
  assert.deepEqual(await apiFetch("/me"), { ok: true });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "/api/v1/me");
  assert.equal(calls[0].init?.credentials, "same-origin");
  assert.equal(header(calls[0], "X-XSRF-TOKEN"), null);
});

test("POST fetches CSRF token first, attaches it, and caches it", async () => {
  queue.push(csrf("t1"), json(204), json(204));
  await apiFetch("/auth/logout", { method: "POST" });
  await apiFetch("/auth/logout", { method: "POST" });
  assert.deepEqual(
    calls.map((c) => c.url),
    ["/api/v1/auth/csrf", "/api/v1/auth/logout", "/api/v1/auth/logout"],
  );
  assert.equal(header(calls[1], "X-XSRF-TOKEN"), "t1");
  assert.equal(header(calls[2], "X-XSRF-TOKEN"), "t1");
});

test("403 CSRF_INVALID refreshes the token and retries exactly once", async () => {
  queue.push(
    csrf("old"),
    json(403, { errorCode: "CSRF_INVALID", message: "bad" }),
    csrf("new"),
    json(200, { done: true }),
  );
  assert.deepEqual(await apiFetch("/x", { method: "POST" }), { done: true });
  assert.equal(header(calls[3], "X-XSRF-TOKEN"), "new");
  assert.equal(calls.length, 4);
});

test("second CSRF_INVALID is surfaced as ApiRequestError (no loop)", async () => {
  queue.push(
    csrf("a"),
    json(403, { errorCode: "CSRF_INVALID", message: "bad" }),
    csrf("b"),
    json(403, { errorCode: "CSRF_INVALID", message: "bad again" }),
  );
  await assert.rejects(
    apiFetch("/x", { method: "POST" }),
    (e: unknown) =>
      e instanceof ApiRequestError &&
      e.status === 403 &&
      e.error.errorCode === "CSRF_INVALID",
  );
  assert.equal(calls.length, 4);
});

test("FORBIDDEN 403 is not retried; error body is parsed", async () => {
  queue.push(
    csrf("a"),
    json(403, { errorCode: "FORBIDDEN", message: "nope" }),
  );
  await assert.rejects(
    apiFetch("/x", { method: "DELETE" }),
    (e: unknown) =>
      e instanceof ApiRequestError &&
      e.error.errorCode === "FORBIDDEN" &&
      e.message === "nope",
  );
  assert.equal(calls.length, 2);
});

test("401 maps to ApiRequestError; non-JSON error body falls back", async () => {
  queue.push(json(401, { errorCode: "SESSION_EXPIRED", message: "expired" }));
  await assert.rejects(
    apiFetch("/me"),
    (e: unknown) =>
      e instanceof ApiRequestError && e.error.errorCode === "SESSION_EXPIRED",
  );
  queue.push(new Response("<html>", { status: 502 }));
  await assert.rejects(
    apiFetch("/me"),
    (e: unknown) =>
      e instanceof ApiRequestError && e.error.errorCode === "UNKNOWN_ERROR",
  );
});
