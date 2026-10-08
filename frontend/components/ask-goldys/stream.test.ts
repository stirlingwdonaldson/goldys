import { describe, it, expect, vi, afterEach } from "vitest";
import { parseSseBlock, parseDraft, parseAnswer, streamChat } from "./stream";

describe("parseSseBlock", () => {
  it("parses a named event with JSON data", () => {
    const block = 'event: text\ndata: {"delta":"hello"}\n\n';
    expect(parseSseBlock(block)).toEqual({ event: "text", data: '{"delta":"hello"}' });
  });

  it("defaults the event name to message", () => {
    expect(parseSseBlock('data: {"a":1}\n\n')).toEqual({ event: "message", data: '{"a":1}' });
  });

  it("joins multi-line data", () => {
    expect(parseSseBlock('event: answer\ndata: {"a":1}\ndata: {"b":2}\n\n')).toEqual({
      event: "answer",
      data: '{"a":1}\n{"b":2}',
    });
  });
});

describe("parseDraft", () => {
  it("round-trips a well-formed draft", () => {
    const draft = {
      title: "Weekly sales",
      description: "Gross sales by day",
      filters: {
        dateRange: { from: "2026-10-01", to: "2026-10-07", calendar: "TRADING" },
        comparison: "PREVIOUS_WEEK",
        dimensions: ["PRODUCT"],
      },
      widgets: [
        {
          id: "w1",
          renderType: "time-series",
          queries: [
            {
              metric: "sales.gross",
              range: { from: "2026-10-01", to: "2026-10-07", calendar: "CALENDAR" },
              grain: "DAY",
              dimensions: ["DEPARTMENT"],
              comparison: null,
            },
          ],
          layout: { w: 6, h: 2 },
        },
      ],
    };
    expect(parseDraft(draft)).toEqual({ ...draft, dashboardId: null });
  });

  it("returns null when the title is missing", () => {
    expect(parseDraft({ description: "no title", filters: {}, widgets: [] })).toBeNull();
    expect(parseDraft("not an object")).toBeNull();
    expect(parseDraft(null)).toBeNull();
  });

  it("drops malformed widgets and keeps valid ones", () => {
    const parsed = parseDraft({
      title: "Weekly sales",
      widgets: [
        { renderType: "time-series" }, // missing id -> dropped
        { id: "w2", renderType: "stat", queries: [], layout: { w: 6, h: 1 } },
      ],
    });
    expect(parsed?.widgets).toEqual([
      { id: "w2", renderType: "stat", queries: [], layout: { w: 6, h: 1 } },
    ]);
  });

  it("degrades missing filters to null filters", () => {
    const parsed = parseDraft({ title: "Weekly sales", widgets: [] });
    expect(parsed?.filters).toEqual({ dateRange: null, comparison: null, dimensions: [] });
  });

  it("parses an optional dashboardId and degrades it to null when absent", () => {
    expect(parseDraft({ title: "Weekly sales", widgets: [], dashboardId: "d1" })?.dashboardId).toBe(
      "d1",
    );
    expect(parseDraft({ title: "Weekly sales", widgets: [] })?.dashboardId).toBeNull();
  });
});

describe("parseAnswer", () => {
  it("parses trace provenance, dropping malformed provenance entries", () => {
    const answer = parseAnswer({
      widgets: [],
      trace: [
        {
          tool: "get_sales_by_period",
          description: "Resolved daily sales totals.",
          provenance: [
            {
              metric: "sales.gross",
              definitionVersion: "3",
              range: { from: "2026-10-01", to: "2026-10-07", calendar: "CALENDAR" },
              grain: "DAY",
              sourceDomain: "resolved_daily_sales",
              dataFreshness: "2026-10-07T09:00:00Z",
              missingPeriods: ["2026-10-05"],
              calculationVersion: "1",
            },
            "not-an-object",
          ],
        },
      ],
      asOf: "2026-10-07T10:00:00Z",
      notices: [],
    });

    expect(answer.trace).toEqual([
      {
        tool: "get_sales_by_period",
        description: "Resolved daily sales totals.",
        provenance: [
          {
            metric: "sales.gross",
            definitionVersion: "3",
            range: { from: "2026-10-01", to: "2026-10-07", calendar: "CALENDAR" },
            grain: "DAY",
            sourceDomain: "resolved_daily_sales",
            dataFreshness: "2026-10-07T09:00:00Z",
            missingPeriods: ["2026-10-05"],
            calculationVersion: "1",
          },
        ],
      },
    ]);
  });

  it("falls back to empty provenance for malformed provenance", () => {
    const answer = parseAnswer({
      trace: [{ tool: "get_metric", description: "x", provenance: "nope" }],
      asOf: "",
      notices: [],
    });
    expect(answer.trace[0].provenance).toEqual([]);
  });

  it("drops a trace entry missing tool or description", () => {
    const answer = parseAnswer({
      trace: [
        { tool: "get_metric", description: "x", provenance: [] },
        { description: "no tool", provenance: [] },
      ],
      asOf: "",
      notices: [],
    });
    expect(answer.trace).toEqual([
      { tool: "get_metric", description: "x", provenance: [] },
    ]);
  });
});

describe("streamChat", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("posts {threadId, message} and parses provenance from the answer event", async () => {
    const answerPayload = {
      widgets: [],
      trace: [
        {
          tool: "get_sales_by_period",
          description: "Resolved daily sales totals.",
          provenance: [
            {
              metric: "sales.gross",
              definitionVersion: "3",
              range: { from: "2026-10-01", to: "2026-10-07", calendar: "CALENDAR" },
              grain: "DAY",
              sourceDomain: "resolved_daily_sales",
              dataFreshness: "2026-10-07T09:00:00Z",
              missingPeriods: ["2026-10-05"],
              calculationVersion: "1",
            },
          ],
        },
      ],
      asOf: "2026-10-07T10:00:00Z",
      notices: [],
    };

    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(
          new TextEncoder().encode(`event: answer\ndata: ${JSON.stringify(answerPayload)}\n\n`),
        );
        controller.close();
      },
    });
    const fetchMock = vi.fn().mockResolvedValue(new Response(stream, { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const onText = vi.fn();
    const onAnswer = vi.fn();
    const onError = vi.fn();

    await streamChat("thread-1", "What were sales last week?", { onText, onAnswer, onError });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/conversational/chat");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({
      threadId: "thread-1",
      message: "What were sales last week?",
    });
    expect(init.body).not.toContain("messages");

    expect(onError).not.toHaveBeenCalled();
    expect(onAnswer).toHaveBeenCalledTimes(1);
    expect(onAnswer).toHaveBeenCalledWith(
      expect.objectContaining({
        trace: [
          {
            tool: "get_sales_by_period",
            description: "Resolved daily sales totals.",
            provenance: [
              expect.objectContaining({
                metric: "sales.gross",
                missingPeriods: ["2026-10-05"],
              }),
            ],
          },
        ],
      }),
    );
  });
});
