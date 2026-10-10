import { describe, it, expect } from "vitest";
import { buildSettlementGraph, buildSettlementInput, type SettlementInput } from "./settlement-graph";

const input = (overrides: Partial<SettlementInput> = {}): SettlementInput => ({
  itemsRung: [
    { id: "drinks", label: "Drinks", value: 11050 },
    { id: "food", label: "Food", value: 6630 },
    { id: "other", label: "Other", value: 740 },
  ],
  deletedOrders: { count: 14, value: 612 },
  tenders: [
    { id: "tyro", label: "Tyro", value: 11900 },
    { id: "cash", label: "Cash", value: 2310 },
    { id: "lspayments", label: "LS Payments", value: 3580 },
    { id: "mryum", label: "Mr Yum, me&u", value: 588 },
  ],
  ...overrides,
});

describe("buildSettlementGraph", () => {
  it("lays out four titled columns with the deleted-orders node under sales", () => {
    const g = buildSettlementGraph(input());
    expect(g.columns.map((c) => c.map((n) => n.id))).toEqual([
      ["items:drinks", "items:food", "items:other"],
      ["sales", "deleted"],
      ["tender:tyro", "tender:cash", "tender:lspayments", "tender:mryum"],
      ["settled"],
    ]);
    expect(g.columnTitles).toEqual(["Items rung", "Sales", "Tenders", "Settled"]);
  });

  it("derives gross sales, settled payments, and the difference from the inputs", () => {
    const g = buildSettlementGraph(input());
    const sales = g.columns[1][0];
    expect(sales.data.subtitle).toBe("$18,420 gross");
    const settled = g.columns[3][0];
    expect(settled.data.subtitle).toBe("$18,378");
    expect(settled.data.detail).toBe("Difference vs sales: −$42");
  });

  it("carries the transaction value on each money-flowing edge", () => {
    const g = buildSettlementGraph(input());
    const edge = (s: string, t: string) => g.edges.find((e) => e.source === s && e.target === t);
    expect(edge("items:drinks", "sales")?.value).toBe(11050);
    expect(edge("items:other", "sales")?.value).toBe(740);
    expect(edge("sales", "tender:tyro")?.value).toBe(11900);
    expect(edge("tender:cash", "settled")?.value).toBe(2310);
  });

  it("marks the deleted-orders edge as a dashed annotation carrying no value", () => {
    const g = buildSettlementGraph(input());
    const deleted = g.edges.find((e) => e.source === "sales" && e.target === "deleted");
    expect(deleted).toBeDefined();
    expect(deleted?.tone).toBe("missing");
    expect(deleted?.value).toBeUndefined();
  });

  it("shows deleted orders as a count plus value and flags an unbalanced settlement", () => {
    const g = buildSettlementGraph(input());
    expect(g.columns[1][1].data.subtitle).toBe("14 orders · $612");
    expect(g.columns[3][0].data.tone).toBe("warn");
  });

  it("marks a balanced settlement as ok", () => {
    const g = buildSettlementGraph(
      input({
        itemsRung: [{ id: "food", label: "Food", value: 100 }],
        deletedOrders: { count: 0, value: 0 },
        tenders: [{ id: "cash", label: "Cash", value: 100 }],
      }),
    );
    expect(g.columns[3][0].data.tone).toBe("ok");
    expect(g.columns[3][0].data.detail).toBe("Difference vs sales: $0");
  });

  it("signs a positive difference with a plus", () => {
    const g = buildSettlementGraph(
      input({
        itemsRung: [{ id: "food", label: "Food", value: 100 }],
        deletedOrders: { count: 0, value: 0 },
        tenders: [{ id: "cash", label: "Cash", value: 150 }],
      }),
    );
    expect(g.columns[3][0].data.detail).toBe("Difference vs sales: +$50");
  });
});

describe("buildSettlementInput", () => {
  it("aggregates mix rows into period totals, largest first, and sums deleted orders", () => {
    const settlement = buildSettlementInput({
      saleItemMix: [
        { tradingDate: "2026-10-04", categoryName: "Beer", quantity: 3, amount: 35, hasConflict: false },
        { tradingDate: "2026-10-05", categoryName: "Beer", quantity: 2, amount: 20, hasConflict: false },
        { tradingDate: "2026-10-05", categoryName: "Food", quantity: 2, amount: 54.5, hasConflict: false },
      ],
      paymentMix: [
        { tradingDate: "2026-10-04", paymentTypeName: "Tyro", amount: 40, tip: 2, count: 1, hasConflict: false },
        { tradingDate: "2026-10-04", paymentTypeName: "Cash", amount: 10, tip: 0, count: 1, hasConflict: false },
      ],
      deletedSaleTotals: [
        { tradingDate: "2026-10-04", count: 1, totalIncTax: 120, totalTax: 10.91, hasConflict: false },
        { tradingDate: "2026-10-05", count: 2, totalIncTax: 30, totalTax: 2.73, hasConflict: false },
      ],
    });

    expect(settlement.itemsRung).toEqual([
      { id: "Beer", label: "Beer", value: 55 },
      { id: "Food", label: "Food", value: 54.5 },
    ]);
    expect(settlement.tenders).toEqual([
      { id: "Tyro", label: "Tyro", value: 40 },
      { id: "Cash", label: "Cash", value: 10 },
    ]);
    expect(settlement.deletedOrders).toEqual({ count: 3, value: 150 });
  });
});
