import { describe, it, expect } from "vitest";
import type { DeletedSaleDay } from "@/lib/api/types";
import { buildDeletedSalesTrend } from "./deleted-sales-trend";

describe("buildDeletedSalesTrend", () => {
  const totals: DeletedSaleDay[] = [
    { tradingDate: "2026-10-04", count: 1, totalIncTax: 120.0, totalTax: 10.91, hasConflict: false },
    { tradingDate: "2026-10-05", count: 2, totalIncTax: "65.50", totalTax: 5.95, hasConflict: false },
  ];

  it("plots the daily count as a plain number, not currency", () => {
    const { count } = buildDeletedSalesTrend(totals);

    expect(count.schemaVersion).toBe(2);
    expect(count.type).toBe("bar-chart");
    expect(count.yFormat).toBe("number");
    expect(count.series).toHaveLength(1);
    expect(count.series[0].points).toEqual([
      { x: "2026-10-04", y: 1 },
      { x: "2026-10-05", y: 2 },
    ]);
  });

  it("plots the daily value inc tax on its own currency chart", () => {
    const { value } = buildDeletedSalesTrend(totals);

    expect(value.type).toBe("time-series");
    expect(value.yFormat).toBe("currency");
    expect(value.series).toHaveLength(1);
    // A numeric string value is coerced to a number point.
    expect(value.series[0].points).toEqual([
      { x: "2026-10-04", y: 120 },
      { x: "2026-10-05", y: 65.5 },
    ]);
  });

  it("returns empty series for empty totals", () => {
    const { count, value } = buildDeletedSalesTrend([]);
    expect(count.series).toEqual([]);
    expect(value.series).toEqual([]);
  });
});
