import { describe, it, expect } from "vitest";
import type { PaymentMix } from "@/lib/api/types";
import { buildPaymentMixWidget } from "./payment-mix-chart";

describe("buildPaymentMixWidget", () => {
  it("builds one stacked currency series per distinct payment type, in first-seen order", () => {
    const mix: PaymentMix[] = [
      { tradingDate: "2026-10-04", paymentTypeName: "Tyro", amount: 45.5, tip: 2, count: 1, hasConflict: false },
      { tradingDate: "2026-10-04", paymentTypeName: "Cash", amount: "12.00", tip: 0, count: 1, hasConflict: false },
      { tradingDate: "2026-10-05", paymentTypeName: "Tyro", amount: 78.9, tip: 3.5, count: 1, hasConflict: false },
    ];

    const widget = buildPaymentMixWidget(mix);

    expect(widget.schemaVersion).toBe(2);
    expect(widget.type).toBe("bar-chart");
    expect(widget.stacked).toBe(true);
    expect(widget.yFormat).toBe("currency");
    expect(widget.series.map((s) => s.label)).toEqual(["Tyro", "Cash"]);

    const tyro = widget.series.find((s) => s.label === "Tyro");
    expect(tyro?.points).toEqual([
      { x: "2026-10-04", y: 45.5 },
      { x: "2026-10-05", y: 78.9 },
    ]);

    // A numeric string amount is coerced to a number point.
    const cash = widget.series.find((s) => s.label === "Cash");
    expect(cash?.points).toEqual([{ x: "2026-10-04", y: 12 }]);
  });

  it("returns an empty series array for an empty mix", () => {
    const widget = buildPaymentMixWidget([]);
    expect(widget.series).toEqual([]);
    expect(widget.type).toBe("bar-chart");
  });
});
