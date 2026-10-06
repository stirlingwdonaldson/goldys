import { describe, it, expect } from "vitest";
import { parseWidgetSpec, parseWidgetSpecs } from "./parse";

describe("parseWidgetSpec", () => {
  it("parses a valid time-series widget", () => {
    const w = parseWidgetSpec({
      schemaVersion: 2,
      id: "w1",
      type: "time-series",
      title: "Daily sales",
      series: [{ key: "grossSales", label: "Gross sales", points: [{ x: "2026-09-13", y: 27650.66 }] }],
    });
    expect(w).not.toBeNull();
    if (!w || w.type !== "time-series") return;
    expect(w.series).toHaveLength(1);
    expect(w.series[0].points[0].y).toBe(27650.66);
  });

  it("rejects a wrong schema version", () => {
    expect(parseWidgetSpec({ schemaVersion: 1, type: "stat", id: "w", title: "t" })).toBeNull();
  });

  it("rejects an unknown widget type", () => {
    expect(parseWidgetSpec({ schemaVersion: 2, type: "pie", id: "w", title: "t" })).toBeNull();
  });

  it("rejects a missing id or title", () => {
    expect(parseWidgetSpec({ schemaVersion: 2, type: "stat", title: "t" })).toBeNull();
    expect(parseWidgetSpec({ schemaVersion: 2, type: "stat", id: "w" })).toBeNull();
  });

  it("coerces a non-numeric stat value to null", () => {
    const w = parseWidgetSpec({ schemaVersion: 2, type: "stat", id: "w", title: "t", value: "not a number" });
    expect(w).not.toBeNull();
    if (!w || w.type !== "stat") return;
    expect(w.value).toBeNull();
  });
});

describe("parseWidgetSpecs", () => {
  it("drops malformed widgets and keeps valid ones", () => {
    const list = parseWidgetSpecs([
      { schemaVersion: 2, type: "stat", id: "a", title: "A" },
      { schemaVersion: 2, type: "bogus", id: "b", title: "B" },
      42,
    ]);
    expect(list).toHaveLength(1);
    expect(list[0].id).toBe("a");
  });
});
