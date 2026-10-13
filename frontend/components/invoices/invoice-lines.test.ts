import { describe, it, expect } from "vitest";
import type { LineGraphNode } from "@/lib/api/types";
import {
  checkTotals,
  dayBefore,
  displayProductName,
  distinctStockCode,
  priceChanges,
  relativeDue,
} from "./invoice-lines";

function line(partial: Partial<LineGraphNode>): LineGraphNode {
  return {
    productNameKey: "basil x bunch",
    stockCode: "BASIL X BUNCH",
    quantity: 1,
    unitCost: 1,
    lineTotal: 1,
    uom: null,
    ...partial,
  };
}

describe("displayProductName", () => {
  it("capitalises the first letter only", () => {
    expect(displayProductName("royal blue potatoes x kilo")).toBe("Royal blue potatoes x kilo");
  });
});

describe("distinctStockCode", () => {
  it("hides a stock code that only repeats the product name", () => {
    expect(distinctStockCode(line({ stockCode: "BASIL  X BUNCH " }))).toBeNull();
  });

  it("keeps a stock code that adds information", () => {
    expect(distinctStockCode(line({ stockCode: "CB-1" }))).toBe("CB-1");
  });

  it("is null when there is no stock code", () => {
    expect(distinctStockCode(line({ stockCode: null }))).toBeNull();
  });
});

describe("checkTotals", () => {
  it("accepts a cent of rounding per line", () => {
    const lines = [line({ lineTotal: 54.5 }), line({ lineTotal: 10.01 }), line({ lineTotal: 11.96 })];
    expect(checkTotals(lines, 76.5)?.matches).toBe(true);
  });

  it("reports how far short the lines come", () => {
    const result = checkTotals([line({ lineTotal: 90 }), line({ lineTotal: 50 })], 150);
    expect(result).toEqual({ linesTotal: 140, difference: 10, matches: false });
  });

  it("has nothing to compare without lines or a header total", () => {
    expect(checkTotals([], 10)).toBeNull();
    expect(checkTotals([line({})], null)).toBeNull();
  });
});

describe("priceChanges", () => {
  it("matches lines by stock code and reports the movement", () => {
    const changes = priceChanges(
      [line({ stockCode: "CB-1", unitCost: 12.5 }), line({ stockCode: "BM-2", unitCost: 9 })],
      [line({ stockCode: "CB-1", unitCost: 10 }), line({ stockCode: "BM-2", unitCost: 9 })],
    );
    expect(changes.get("code:cb-1")).toEqual({ previousUnitCost: 10, ratio: 0.25 });
    // Unchanged prices aren't reported.
    expect(changes.has("code:bm-2")).toBe(false);
  });

  it("falls back to the product name when there is no stock code", () => {
    const changes = priceChanges(
      [line({ stockCode: null, productNameKey: "lemons", unitCost: 4 })],
      [line({ stockCode: null, productNameKey: "Lemons", unitCost: 5 })],
    );
    expect(changes.get("name:lemons")?.ratio).toBeCloseTo(-0.2);
  });

  it("ignores rounding noise and zero previous costs", () => {
    const changes = priceChanges(
      [line({ stockCode: "A", unitCost: 10.02 }), line({ stockCode: "B", unitCost: 3 })],
      [line({ stockCode: "A", unitCost: 10 }), line({ stockCode: "B", unitCost: 0 })],
    );
    expect(changes.size).toBe(0);
  });
});

describe("dayBefore", () => {
  it("crosses month and year boundaries", () => {
    expect(dayBefore("2026-10-01")).toBe("2026-09-30");
    expect(dayBefore("2026-01-01")).toBe("2025-12-31");
  });
});

describe("relativeDue", () => {
  const today = new Date(2026, 9, 13);
  it("describes the due date relative to today", () => {
    expect(relativeDue("2026-10-13", today)).toBe("today");
    expect(relativeDue("2026-10-14", today)).toBe("tomorrow");
    expect(relativeDue("2026-10-18", today)).toBe("in 5 days");
    expect(relativeDue("2026-10-10", today)).toBe("3 days ago");
  });
});
