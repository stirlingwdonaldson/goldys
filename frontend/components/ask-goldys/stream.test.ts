import { describe, it, expect, vi, afterEach } from "vitest";
import { parseSseBlock, parseDraft, parseAnswer, streamChat } from "./stream";

const answer = { widgets: [], trace: [], asOf: "2026-10-23T00:00:00Z", notices: [] };
const filters = { dateRange: null, comparison: null, dimensions: [] };
const widget = { id: "w", renderType: "stat", queries: [], layout: { w: 6, h: 1 } };

afterEach(() => vi.unstubAllGlobals());

describe("reporting contracts", () => {
  it("parses LF/CRLF blocks and multiline data", () => {
    for (const newline of ["\n", "\r\n"]) expect(parseSseBlock(`event: answer${newline}data: a${newline}data: b`)).toEqual({ event: "answer", data: "a\nb" });
  });
  it("preserves a valid draft and rejects a damaged widget or unsupported query grain", () => {
    expect(parseDraft({ title: "Weekly", filters, widgets: [widget] })).toEqual({ title: "Weekly", filters, widgets: [widget], description: null, dashboardId: null });
    expect(parseDraft({ title: "Weekly", widgets: [widget, { renderType: "stat" }] })).toBeNull();
    expect(parseDraft({ title: "Weekly", widgets: [{ ...widget, layout: { w: 0, h: 1 } }] })).toBeNull();
    expect(parseDraft({ widgets: [] })).toBeNull();
  });
  it("retains TOTAL provenance and explicit unavailable freshness", () => {
    const provenance = { metric: "sales.net", definitionVersion: "1", range: { from: "2026-10-01", to: "2026-10-07", calendar: "CALENDAR" }, grain: "TOTAL", sourceDomain: "resolved_daily_sales", dataFreshness: null, missingPeriods: [], calculationVersion: "1", trust: null };
    expect(parseAnswer({ ...answer, trace: [{ tool: "get_sales_by_period", description: "Sales", provenance: [provenance] }] }).trace[0].provenance).toEqual([provenance]);
  });
  it("rejects malformed provenance instead of removing the evidence", () => {
    expect(() => parseAnswer({ ...answer, trace: [{ tool: "sales", description: "Sales", provenance: [42] }] })).toThrow("invalid reporting data");
    expect(() => parseAnswer({ ...answer, draft: { title: "Draft", widgets: [{}] } })).toThrow();
  });
});

describe("streamChat", () => {
  it("handles a CRLF boundary split across chunks and releases the reader", async () => {
    const cancel = vi.fn();
    const encoder = new TextEncoder();
    const stream = new ReadableStream({ start(controller) {
      for (const part of ['event: text\r', '\ndata: {"delta":"Hi"}\r\n\r', `\nevent: answer\r\ndata: ${JSON.stringify(answer)}\r\n\r\n`]) controller.enqueue(encoder.encode(part));
      controller.close();
    }, cancel });
    const response = new Response(stream, { headers: { "Content-Type": "text/event-stream;charset=UTF-8" } });
    const fetch = vi.fn<typeof globalThis.fetch>().mockResolvedValue(response);
    vi.stubGlobal("fetch", fetch);
    const handlers = { onText: vi.fn(), onAnswer: vi.fn(), onError: vi.fn() };
    await streamChat("thread", "question", handlers);
    expect(JSON.parse(fetch.mock.calls[0]?.[1]?.body as string)).toEqual({ threadId: "thread", message: "question" });
    expect(handlers.onText).toHaveBeenCalledWith("Hi");
    expect(handlers.onAnswer).toHaveBeenCalledWith(answer);
    expect(response.body?.locked).toBe(false);
  });
  it("rejects HTML and malformed answer frames without a false empty success", async () => {
    const handlers = { onText: vi.fn(), onAnswer: vi.fn(), onError: vi.fn() };
    vi.stubGlobal("fetch", vi.fn(async () => new Response("<html>", { headers: { "Content-Type": "text/html" } })));
    await expect(streamChat("t", "q", handlers)).rejects.toThrow("did not return a chat stream");
    vi.stubGlobal("fetch", vi.fn(async () => new Response('event: answer\ndata: {"widgets":[{}]}\n\n', { headers: { "Content-Type": "text/event-stream" } })));
    await expect(streamChat("t", "q", handlers)).rejects.toThrow("could not be read");
    expect(handlers.onAnswer).not.toHaveBeenCalled();
  });
  it("passes cancellation to the transport", async () => {
    const controller = new AbortController();
    const fetch = vi.fn(async (_url: unknown, init: RequestInit) => { expect(init.signal).toBe(controller.signal); throw new DOMException("Aborted", "AbortError"); });
    vi.stubGlobal("fetch", fetch);
    await expect(streamChat("t", "q", { signal: controller.signal, onText: vi.fn(), onAnswer: vi.fn(), onError: vi.fn() })).rejects.toMatchObject({ name: "AbortError" });
  });
});
