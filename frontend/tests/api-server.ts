import { afterAll, afterEach, beforeAll, beforeEach, vi } from "vitest";
import { setupServer } from "msw/node";

export const server = setupServer();

/** Opt-in HTTP tests, with browser-relative URL resolution for Node's fetch. */
export function setupApiServer(): void {
  let interceptedFetch: typeof fetch;
  beforeAll(() => {
    server.listen({ onUnhandledFrame: "error" });
    interceptedFetch = globalThis.fetch;
  });
  beforeEach(() => {
    vi.stubGlobal("fetch", (input: RequestInfo | URL, init?: RequestInit) => {
      const resolved = typeof input === "string" && input.startsWith("/")
        ? new URL(input, typeof window === "undefined" ? "http://localhost:3000" : window.location.origin) : input;
      return interceptedFetch(resolved, init);
    });
  });
  afterEach(() => {
    server.resetHandlers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    if (typeof document !== "undefined") {
      for (const cookie of document.cookie.split(";")) {
        document.cookie = `${cookie.split("=")[0].trim()}=; Max-Age=0; path=/`;
      }
    }
  });
  afterAll(() => server.close());
}
