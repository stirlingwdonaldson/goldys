import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchApi } from "./client";
import { ApiError } from "./errors";
import { http, HttpResponse } from "msw";
import { z } from "zod";
import { server, setupApiServer } from "@/tests/api-server";
import { liveApi } from "./live";

describe("API request construction", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
  });

  it.each([
    { name: "record", headers: { "X-Feature": "dashboard", Accept: "application/vnd.goldys+json" } },
    { name: "Headers", headers: new Headers({ "X-Feature": "dashboard", Accept: "application/vnd.goldys+json" }) },
    { name: "tuples", headers: [["X-Feature", "dashboard"], ["Accept", "application/vnd.goldys+json"]] },
  ] satisfies { name: string; headers: HeadersInit }[])("retains $name headers", async ({ headers }) => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ ok: true }));
    vi.stubGlobal("fetch", fetch);
    await fetchApi("/api/fixture", { headers });
    const sent = new Headers(fetch.mock.calls[0][1].headers);
    expect(sent.get("x-feature")).toBe("dashboard");
    expect(sent.get("accept")).toBe("application/vnd.goldys+json");
  });

  it.each(["POST", "PUT", "PATCH", "DELETE"])("adds decoded CSRF to %s and forwards signal/credentials", async method => {
    document.cookie = "XSRF-TOKEN=fixture%2Bcsrf; path=/";
    const controller = new AbortController();
    const fetch = vi.fn().mockResolvedValue(Response.json({ ok: true }));
    vi.stubGlobal("fetch", fetch);
    await fetchApi("/api/fixture", { method, signal: controller.signal, credentials: "omit" });
    expect(new Headers(fetch.mock.calls[0][1].headers).get("x-xsrf-token")).toBe("fixture+csrf");
    expect(fetch.mock.calls[0][1]).toMatchObject({ signal: controller.signal, credentials: "same-origin" });
  });

  it.each(["GET", "HEAD"])("does not decode or attach CSRF on %s", async method => {
    document.cookie = "XSRF-TOKEN=%invalid; path=/";
    const fetch = vi.fn().mockResolvedValue(Response.json({ ok: true }));
    vi.stubGlobal("fetch", fetch);
    await fetchApi("/api/fixture", { method });
    expect(new Headers(fetch.mock.calls[0][1].headers).has("x-xsrf-token")).toBe(false);
  });

  it("leaves FormData Content-Type and missing CSRF to the browser", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ ok: true }));
    vi.stubGlobal("fetch", fetch);
    await fetchApi("/api/fixture", { method: "POST", body: new FormData() });
    const headers = new Headers(fetch.mock.calls[0][1].headers);
    expect(headers.has("content-type")).toBe(false);
    expect(headers.has("x-xsrf-token")).toBe(false);
    expect(headers.get("accept")).toBe("application/json");
  });

  it("propagates fetch cancellation unchanged", async () => {
    const aborted = new DOMException("Cancelled", "AbortError");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(aborted));
    await expect(fetchApi("/api/fixture")).rejects.toBe(aborted);
  });

  it("keeps network cause but exposes a safe message and no fabricated status", async () => {
    const cause = new TypeError("fixture-internal-detail");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(cause));
    const error = await fetchApi("/api/fixture").catch(e => e);
    expect(error).toBeInstanceOf(ApiError);
    if (!(error instanceof ApiError)) throw error;
    expect(error).toMatchObject({ code: "NETWORK_ERROR", kind: "network", cause });
    expect(error.status).toBeUndefined();
    expect(error.message).not.toContain("fixture-internal-detail");
  });

  it.each(["cookie", "header"])("classifies invalid %s as configuration, not network", async invalid => {
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    if (invalid === "cookie") document.cookie = "XSRF-TOKEN=%invalid; path=/";
    const error = await fetchApi("/api/fixture", {
      method: "POST", headers: invalid === "header" ? { "invalid header": "value" } : undefined,
    }).catch(e => e);
    expect(error).toBeInstanceOf(ApiError);
    if (!(error instanceof ApiError)) throw error;
    expect(error).toMatchObject({ code: "REQUEST_CONFIGURATION_ERROR", kind: "unexpected", cause: expect.any(Error) });
    expect(error.message).not.toContain("%invalid");
    expect(fetch).not.toHaveBeenCalled();
  });
});

describe("API response boundary", () => {
  setupApiServer();
  const endpoint = "http://localhost:3000/api/fixture";
  const correlation = { "X-Correlation-ID": "req-header" };

  it("preserves code/status/header correlation with malformed optional metadata", async () => {
    server.use(http.get(endpoint, () => HttpResponse.json({ code: "OVERRIDE_CONFLICT", message: "Conflict", fields: ["invalid"], correlationId: 42 }, { status: 409, headers: correlation })));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({
      code: "OVERRIDE_CONFLICT", status: 409, correlationId: "req-header", fields: {},
      diagnostics: expect.arrayContaining([{ path: ["fields"], code: "invalid_type" }]),
    });
  });

  it.each([200, 204])("accepts empty %i only for explicitly void responses", async status => {
    server.use(http.delete(endpoint, () => new HttpResponse(null, { status })));
    await expect(fetchApi<void>("/api/fixture", { method: "DELETE" }, { responseType: "void" })).resolves.toBeUndefined();
  });
  it.each([200, 204])("rejects empty %i for required JSON", async status => {
    server.use(http.get(endpoint, () => new HttpResponse(null, { status, headers: correlation })));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({ code: "UNPARSEABLE_RESPONSE", status, correlationId: "req-header", kind: "protocol" });
  });
  it("permits an explicit optional-JSON 204 but not empty 200", async () => {
    server.use(http.get(endpoint, () => new HttpResponse(null, { status: 204 })));
    await expect(fetchApi("/api/fixture", undefined, { allowNoContent: true })).resolves.toBeUndefined();
    server.use(http.get(endpoint, () => new HttpResponse(null, { status: 200 })));
    await expect(fetchApi("/api/fixture", undefined, { allowNoContent: true })).rejects.toMatchObject({ status: 200, code: "UNPARSEABLE_RESPONSE" });
  });
  it.each(["application/json", "application/problem+json", "application/vnd.goldys+json"])("decodes 201 %s", async type => {
    server.use(http.get(endpoint, () => new HttpResponse('{"ok":true}', { status: 201, headers: { "Content-Type": type } })));
    await expect(fetchApi("/api/fixture")).resolves.toEqual({ ok: true });
  });
  it("accepts nonempty JSON acknowledgement for void", async () => {
    server.use(http.post(endpoint, () => HttpResponse.json({ accepted: true })));
    await expect(fetchApi<void>("/api/fixture", { method: "POST" }, { responseType: "void" })).resolves.toBeUndefined();
  });
  it.each(["json", "void"] as const)("rejects HTML even with JSON-looking contents in %s mode", async responseType => {
    server.use(http.get(endpoint, () => new HttpResponse('{"ok":true}', { headers: { "Content-Type": "text/html", ...correlation } })));
    const options = responseType === "void" ? { responseType: "void" as const } : undefined;
    const result = options ? fetchApi<void>("/api/fixture", undefined, options) : fetchApi("/api/fixture");
    await expect(result).rejects.toMatchObject({ code: "UNEXPECTED_CONTENT_TYPE", status: 200, correlationId: "req-header" });
  });
  it("allows JSON without Content-Type for existing proxies", async () => {
    const response = new Response('{"ok":true}');
    response.headers.delete("Content-Type");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));
    await expect(fetchApi("/api/fixture")).resolves.toEqual({ ok: true });
  });
  it.each([200, 400, 500])("preserves %i and parse cause for malformed JSON", async status => {
    server.use(http.get(endpoint, () => new HttpResponse("{", { status, headers: { ...correlation, "Content-Type": "application/json" } })));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({ code: "UNPARSEABLE_RESPONSE", status, correlationId: "req-header", cause: expect.any(SyntaxError) });
  });
  it.each([400, 401, 403, 409, 503])("retains valid envelope at HTTP %i", async status => {
    server.use(http.get(endpoint, () => HttpResponse.json({ code: "FIXTURE_CODE", message: "fixture-secret-do-not-display", fields: { title: "Required" }, correlationId: "req-envelope" }, { status, headers: correlation })));
    const error = await fetchApi("/api/fixture").catch(e => e);
    expect(error).toBeInstanceOf(ApiError);
    if (!(error instanceof ApiError)) throw error;
    expect(error).toMatchObject({ code: "FIXTURE_CODE", status, fields: { title: "Required" }, correlationId: "req-envelope" });
    expect(error.message).not.toContain("fixture-secret-do-not-display");
  });
  it("does not expose a 500 backend message even for a known code", async () => {
    server.use(http.get(endpoint, () => HttpResponse.json({ code: "VALIDATION_FAILED", message: "fixture-secret-do-not-display" }, { status: 500 })));
    const error = await fetchApi("/api/fixture").catch(e => e);
    expect(error).toBeInstanceOf(ApiError);
    if (!(error instanceof ApiError)) throw error;
    expect(error.message).not.toContain("fixture-secret-do-not-display");
    expect(error.status).toBe(500);
  });
  it("treats nullable metadata as absent and falls back to the header", async () => {
    server.use(http.get(endpoint, () => HttpResponse.json({ code: "NOT_PERMITTED", message: "Denied", fields: null, correlationId: null }, { status: 403, headers: correlation })));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({ status: 403, code: "NOT_PERMITTED", fields: {}, diagnostics: [], correlationId: "req-header" });
  });
  it.each([null, [], {}, { code: "ONLY_CODE" }])("diagnoses malformed error envelope %#", async body => {
    server.use(http.get(endpoint, () => HttpResponse.json(body, { status: 502, headers: correlation })));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({ status: 502, code: "UNPARSEABLE_RESPONSE", correlationId: "req-header" });
  });
  it.each([
    ["http://localhost:3000/login", "AUTH_REQUIRED"],
    ["http://localhost:3000/oauth2/authorization/fixture", "AUTH_REQUIRED"],
    ["http://localhost:3000/login/oauth2/code/fixture", "AUTH_REQUIRED"],
    ["http://localhost:3000/unexpected", "UNEXPECTED_REDIRECT"],
    ["https://external.invalid/login", "UNEXPECTED_REDIRECT"],
  ])("diagnoses followed redirect to %s", async (url, code) => {
    const response = Response.json({ looksValid: true }, { headers: correlation });
    Object.defineProperties(response, { redirected: { value: true }, url: { value: url } });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({ code, status: 200, correlationId: "req-header" });
  });
  it("does not invent an HTTP status for opaque redirects", async () => {
    const response = new Response(null);
    Object.defineProperties(response, { type: { value: "opaqueredirect" }, status: { value: 0 } });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({ code: "UNEXPECTED_REDIRECT", status: undefined });
  });
  it("propagates cancellation during body consumption", async () => {
    const aborted = new DOMException("Cancelled", "AbortError");
    const response = Response.json({ ok: true });
    vi.spyOn(response, "text").mockRejectedValue(aborted);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));
    await expect(fetchApi("/api/fixture")).rejects.toBe(aborted);
  });
  it("retains body-read failures and available status", async () => {
    const cause = new TypeError("fixture read failure");
    const response = Response.json({ ok: true }, { headers: correlation });
    vi.spyOn(response, "text").mockRejectedValue(cause);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({ status: 200, cause, correlationId: "req-header", kind: "protocol" });
  });
  it("returns structured schema diagnostics without coercion", async () => {
    server.use(http.get(endpoint, () => HttpResponse.json({ amount: "not-a-number" }, { headers: correlation })));
    await expect(fetchApi("/api/fixture", undefined, { schema: z.object({ amount: z.number() }) })).rejects.toMatchObject({
      code: "INVALID_RESPONSE", status: 200, kind: "integrity", correlationId: "req-header",
      diagnostics: [{ path: ["amount"], code: "invalid_type" }],
    });
  });
  it("normalizes exceptions thrown by a schema decoder with HTTP metadata", async () => {
    const cause = new Error("fixture decoder internals");
    const schema = z.string().transform(() => { throw cause; });
    server.use(http.get(endpoint, () => HttpResponse.json("fixture", { headers: correlation })));
    await expect(fetchApi("/api/fixture", undefined, { schema })).rejects.toMatchObject({
      code: "UNEXPECTED_STATUS", status: 200, correlationId: "req-header", cause, kind: "unexpected",
    });
  });
  it.each(["toString", "__proto__"])("treats inherited error code %s as unknown", async code => {
    server.use(http.get(endpoint, () => HttpResponse.json({ code, message: "fixture" }, { status: 400 })));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({
      code, message: "The request could not be completed. Try again later.",
    });
  });
  it("fails unhandled MSW requests instead of contacting a server", async () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    await expect(fetchApi("/api/unhandled-fixture")).rejects.toMatchObject({ code: "NETWORK_ERROR" });
    expect(consoleError).toHaveBeenCalled();
  });

  it.each([200, 204])("supports live void adapters with empty %i", async status => {
    server.use(
      http.delete("http://localhost:3000/api/reconciliation/rules/r1", () => new HttpResponse(null, { status })),
      http.delete("http://localhost:3000/api/dashboards/d1", () => new HttpResponse(null, { status })),
      http.delete("http://localhost:3000/api/conversational/threads/t1", () => new HttpResponse(null, { status })),
    );
    await expect(liveApi.deleteResolutionRule("r1")).resolves.toBeUndefined();
    await expect(liveApi.deleteDashboard("d1")).resolves.toBeUndefined();
    await expect(liveApi.deleteThread("t1")).resolves.toBeUndefined();
  });
  it("distinguishes reservation no-content from failure", async () => {
    const url = "http://localhost:3000/api/reservations/summary";
    server.use(http.get(url, () => new HttpResponse(null, { status: 204 })));
    await expect(liveApi.getReservationSummary("2026-10-09")).resolves.toBeUndefined();
    server.use(http.get(url, () => HttpResponse.json({ date: "2026-10-09", covers: 0 })));
    await expect(liveApi.getReservationSummary("2026-10-09")).resolves.toMatchObject({ covers: 0 });
    server.use(http.get(url, () => new HttpResponse(null, { status: 500 })));
    await expect(liveApi.getReservationSummary("2026-10-09")).rejects.toMatchObject({ status: 500 });
  });
});
