import { describe, it, expect } from "vitest";
import type { DeletedSaleDay } from "@/lib/api/types";
import { buildDeletedSalesTrend } from "./deleted-sales-trend";

describe("buildDeletedSalesTrend", () => {
  it("builds count and value-inc-tax series from the resolved totals", () => {
    const totals: DeletedSaleDay[] = [
      { tradingDate: "2026-10-04", count: 1, totalIncTax: 120.0, totalTax: 10.91, hasConflict: false },
      { tradingDate: "2026-10-05", count: 2, totalIncTax: "65.50", totalTax: 5.95, hasConflict: false },
    ];

    const widget = buildDeletedSalesTrend(totals);

    expect(widget.schemaVersion).toBe(2);
    expect(widget.type).toBe("time-series");
    expect(widget.yFormat).toBe("currency");
    expect(widget.series.map((s) => s.label)).toEqual(["Deleted orders", "Value inc tax"]);

    const count = widget.series.find((s) => s.key === "count");
    expect(count?.points).toEqual([
      { x: "2026-10-04", y: 1 },
      { x: "2026-10-05", y: 2 },
    ]);

    // A numeric string value is coerced to a number point.
    const value = widget.series.find((s) => s.key === "totalIncTax");
    expect(value?.points).toEqual([
      { x: "2026-10-04", y: 120 },
      { x: "2026-10-05", y: 65.5 },
    ]);
  });

  it("returns an empty series array for empty totals", () => {
    const widget = buildDeletedSalesTrend([]);
    expect(widget.series).toEqual([]);
    expect(widget.type).toBe("time-series");
  });
});
