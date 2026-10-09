import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchApi } from "./client";
import { ApiError } from "./errors";

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
