import { describe, it, expect } from "vitest";
import { parseSseBlock, parseDraft } from "./stream";

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
    expect(parseDraft(draft)).toEqual(draft);
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
});
