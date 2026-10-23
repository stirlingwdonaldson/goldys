import { describe, it, expect } from "vitest";
import { parseWidgetSpec, parseWidgetSpecs } from "./parse";

describe("widget contracts", () => {
  it("preserves zero, explicit null and empty text", () => {
    expect(parseWidgetSpec({ schemaVersion: 2, type: "stat", id: "w", title: "", value: 0 })).toMatchObject({ value: 0, title: "" });
    expect(parseWidgetSpec({ schemaVersion: 2, type: "stat", id: "w", title: "t", value: null })).toMatchObject({ value: null });
  });
  it("rejects unsupported versions/types and malformed nested values", () => {
    expect(parseWidgetSpec({ schemaVersion: 1, type: "stat", id: "w", title: "t", value: 1 })).toBeNull();
    expect(parseWidgetSpec({ schemaVersion: 2, type: "pie", id: "w", title: "t" })).toBeNull();
    expect(parseWidgetSpec({ schemaVersion: 2, type: "stat", id: "w", title: "t", value: "unknown" })).toBeNull();
    expect(parseWidgetSpec({ schemaVersion: 2, type: "time-series", id: "w", title: "t", series: [{ key: "s", label: "S", points: [{ x: "date", y: "unknown" }] }] })).toBeNull();
  });
  it("rejects the list instead of silently losing malformed widgets", () => {
    expect(() => parseWidgetSpecs([{ schemaVersion: 2, type: "stat", id: "w", title: "t", value: 1 }, { type: "bogus" }])).toThrow("invalid widget");
    expect(parseWidgetSpecs([])).toEqual([]);
  });
});
