import { describe, it, expect } from "vitest";
import type { SaleItemMix } from "@/lib/api/types";
import { buildSaleItemMixWidget } from "./sale-item-mix-chart";

describe("buildSaleItemMixWidget", () => {
  it("builds one stacked currency series per distinct category, in first-seen order", () => {
    const mix: SaleItemMix[] = [
      { tradingDate: "2026-10-04", categoryName: "Beer", quantity: 3, amount: 35.0, hasConflict: false },
      { tradingDate: "2026-10-04", categoryName: "Food", quantity: 2, amount: "54.50", hasConflict: false },
      { tradingDate: "2026-10-05", categoryName: "Beer", quantity: 1, amount: 12.0, hasConflict: false },
    ];

    const widget = buildSaleItemMixWidget(mix);

    expect(widget.schemaVersion).toBe(2);
    expect(widget.type).toBe("bar-chart");
    expect(widget.stacked).toBe(true);
    expect(widget.yFormat).toBe("currency");
    expect(widget.series.map((s) => s.label)).toEqual(["Beer", "Food"]);

    const beer = widget.series.find((s) => s.label === "Beer");
    expect(beer?.points).toEqual([
      { x: "2026-10-04", y: 35.0 },
      { x: "2026-10-05", y: 12.0 },
    ]);

    // A numeric string amount is coerced to a number point.
    const food = widget.series.find((s) => s.label === "Food");
    expect(food?.points).toEqual([{ x: "2026-10-04", y: 54.5 }]);
  });

  it("returns an empty series array for an empty mix", () => {
    const widget = buildSaleItemMixWidget([]);
    expect(widget.series).toEqual([]);
    expect(widget.type).toBe("bar-chart");
    expect(widget.stacked).toBe(true);
  });
});
