// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const DSN = "http://publickey@sentry.example.test/7";

function envelope(dsn: string): Request {
  const body = `${JSON.stringify({ dsn, sent_at: "2026-10-08T00:00:00Z" })}\n{"type":"event"}\n{}`;
  return new Request("https://platform.example.test/monitoring", { method: "POST", body });
}

describe("POST /monitoring (Sentry tunnel)", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockResolvedValue(new Response(null, { status: 200 }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
    fetchMock.mockReset();
  });

  it("drops events silently when no DSN is configured", async () => {
    vi.stubEnv("SENTRY_DSN", "");
    const response = await POST(envelope(DSN));
    expect(response.status).toBe(204);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("forwards envelopes for this deployment's project to the DSN host", async () => {
    vi.stubEnv("SENTRY_DSN", DSN);
    const response = await POST(envelope(DSN));
    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://sentry.example.test/api/7/envelope/",
      expect.objectContaining({ method: "POST" }),
    );
    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
    expect(headers["X-Sentry-Auth"]).toContain("sentry_key=publickey");
    expect(response.headers.get("X-Sentry-Tunnel")).toBe("upstream");
  });

  it("honours an internal forwarding target", async () => {
    vi.stubEnv("SENTRY_DSN", DSN);
    vi.stubEnv("SENTRY_TUNNEL_TARGET", "http://192.168.4.41:9000/");
    await POST(envelope(DSN));
    expect(fetchMock).toHaveBeenCalledWith(
      "http://192.168.4.41:9000/api/7/envelope/",
      expect.anything(),
    );
  });

  it("refuses envelopes for any other project, key or host (no open proxy)", async () => {
    vi.stubEnv("SENTRY_DSN", DSN);
    for (const other of [
      "http://publickey@sentry.example.test/8",
      "http://otherkey@sentry.example.test/7",
      "http://publickey@evil.example.test/7",
    ]) {
      const response = await POST(envelope(other));
      expect(response.status).toBe(403);
      expect(response.headers.get("X-Sentry-Tunnel")).toBe("dsn-mismatch");
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects bodies that are not envelopes", async () => {
    vi.stubEnv("SENTRY_DSN", DSN);
    const response = await POST(
      new Request("https://platform.example.test/monitoring", { method: "POST", body: "nope" }),
    );
    expect(response.status).toBe(400);
  });
});
