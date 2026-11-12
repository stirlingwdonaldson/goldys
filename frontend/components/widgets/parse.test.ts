import { describe, it, expect, expectTypeOf } from "vitest";
import type { TableWidget } from "./types";
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

it("shares scalar table-cell types with runtime validation and retains missing values", () => {
  expectTypeOf<TableWidget["rows"][number][string]>().toEqualTypeOf<string | number | boolean | null>();
  const table: TableWidget = { schemaVersion: 2, id: "table", type: "table", title: "Values",
    columns: [{ key: "value", label: "Value" }], rows: [{ value: 0 }, { value: null }, { value: false }, { value: "" }],
  };
  expect(parseWidgetSpec(table)).toEqual(table);
  for (const value of [{ nested: 1 }, [1], Infinity, NaN]) {
    const invalid = { ...table, rows: [{ value }] };
    expect(parseWidgetSpec(invalid)).toBeNull();
    expect(() => parseWidgetSpecs([table, invalid])).toThrow("invalid widget");
  }
});
