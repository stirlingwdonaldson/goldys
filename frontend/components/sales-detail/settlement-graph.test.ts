import balancedDay from "../../../docs/audits/sales-payment-2026-11-12/balanced-day.json";
import { describe, it, expect } from "vitest";
import { buildSettlementGraph, buildSettlementInput, type SettlementSources, type SettlementInput } from "./settlement-graph";

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
    expect(g.columnTitles).toEqual(["Merchandise", "Sales", "Tenders", "POS payments"]);
  });

  it("derives gross sales, settled payments, and the difference from the inputs", () => {
    const g = buildSettlementGraph(input());
    const sales = g.columns[1][0];
    expect(sales.data.subtitle).toBe("$18,420.00 incl. GST");
    const settled = g.columns[3][0];
    expect(settled.data.subtitle).toBe("$18,378.00");
    expect(settled.data.detail).toBe("Difference vs sales: −$42.00");
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
    expect(g.columns[1][1].data.subtitle).toBe("14 orders · $612.00");
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
    expect(g.columns[3][0].data.detail).toBe("Difference vs sales: $0.00");
  });

  it("signs a positive difference with a plus", () => {
    const g = buildSettlementGraph(
      input({
        itemsRung: [{ id: "food", label: "Food", value: 100 }],
        deletedOrders: { count: 0, value: 0 },
        tenders: [{ id: "cash", label: "Cash", value: 150 }],
      }),
    );
    expect(g.columns[3][0].data.detail).toBe("Difference vs sales: +$50.00");
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


describe("sales/payment comparison regressions", () => {
  it("balances the actual 11 November aggregate fixture without a floating-point warning", () => {
    const g = buildSettlementGraph(buildSettlementInput(balancedDay as unknown as SettlementSources));
    expect(g.columns[1][0].data.subtitle).toBe("$13,075.07 incl. GST");
    expect(g.columns[3][0].data.detail).toBe("Difference vs sales: $0.00");
    expect(g.columns[3][0].data.tone).toBe("ok");
    expect(g.columns[3][0].data.title).toBe("POS payments");
  });

  it.each([0.01, -0.01])("keeps a real %s difference visible", (difference) => {
    const g = buildSettlementGraph(input({
      itemsRung: [{ id: "food", label: "Food", value: 100 }],
      tenders: [{ id: "cash", label: "Cash", value: 100 + difference }],
    }));
    expect(g.columns[3][0].data.detail).toBe(`Difference vs sales: ${difference > 0 ? "+" : "−"}$0.01`);
    expect(g.columns[3][0].data.tone).toBe("warn");
  });

  it.each([1, -1])("rounds half-cent totals consistently for sign %s", (sign) => {
    const g = buildSettlementGraph(input({
      itemsRung: [{ id: "food", label: "Food", value: sign * 1.005 }],
      tenders: [{ id: "cash", label: "Cash", value: sign * 1.01 }],
    }));
    expect(g.columns[3][0].data.tone).toBe("ok");
    expect(g.columns[3][0].data.detail).toBe("Difference vs sales: $0.00");
  });

  it("includes gift cards, manual payments, refunds and already-adjusted merchandise once", () => {
    const g = buildSettlementGraph(input({
      itemsRung: [{ id: "food", label: "Food", value: 80 }, { id: "refund", label: "Refund", value: -10 }],
      tenders: [
        { id: "gift", label: "Gift Cards", value: 30 },
        { id: "manual", label: "MANUAL Lightspeed Payments", value: 50 },
        { id: "refund", label: "Me&U Refunds", value: -10 },
      ],
      deletedOrders: { count: 2, value: 532 },
    }));
    expect(g.columns[1][0].data.subtitle).toBe("$70.00 incl. GST");
    expect(g.columns[3][0].data.subtitle).toBe("$70.00");
    expect(g.columns[3][0].data.tone).toBe("ok");
  });

  it("preserves the screenshot's real $378 gap", () => {
    const g = buildSettlementGraph(input({
      itemsRung: [{ id: "all", label: "Merchandise", value: 674088.8 }],
      tenders: [{ id: "all", label: "POS payments", value: 673710.8 }],
    }));
    expect(g.columns[3][0].data.detail).toBe("Difference vs sales: −$378.00");
    expect(g.columns[3][0].data.tone).toBe("warn");
  });

  it.each([{ itemsRung: [] }, { itemsRung: [{ id: "food", label: "Food", value: null }] }])("withholds a difference when sales are missing: %j", ({ itemsRung }) => {
    const g = buildSettlementGraph(input({ itemsRung }));
    expect(g.columns[1][0].data.subtitle).toBe("— incl. GST");
    expect(g.columns[3][0].data.detail).toBe("Difference vs sales: Unavailable");
    expect(g.columns[3][0].data.tone).toBe("missing");
    expect(g.edges.filter((edge) => edge.source.startsWith("items:"))).not.toContainEqual(expect.objectContaining({ value: 0 }));
  });

  it("withholds missing payment totals rather than substituting zero", () => {
    const g = buildSettlementGraph(input({ tenders: [] }));
    expect(g.columns[3][0].data.subtitle).toBe("—");
    expect(g.columns[3][0].data.tone).toBe("missing");
  });

  it.each(["hasConflict", "hasCoverageGap"] as const)("does not mark equal totals healthy with %s", (qualification) => {
    const g = buildSettlementGraph(input({
      itemsRung: [{ id: "food", label: "Food", value: 10 }],
      tenders: [{ id: "cash", label: "Cash", value: 10 }],
      [qualification]: true,
    }));
    expect(g.columns[3][0].data.tone).toBe("warn");
  });

  it("keeps a category's missing daily amount missing and flags different source dates", () => {
    const settlement = buildSettlementInput({
      saleItemMix: [
        { tradingDate: "2026-11-01", categoryName: "Food", quantity: 1, amount: 12, hasConflict: false },
        { tradingDate: "2026-11-02", categoryName: "Food", quantity: 1, amount: null as unknown as number, hasConflict: true },
      ],
      paymentMix: [{ tradingDate: "2026-11-01", paymentTypeName: "Cash", amount: 12, tip: 0, count: 1, hasConflict: false }],
      deletedSaleTotals: [],
    });
    expect(settlement.itemsRung[0].value).toBeNull();
    expect(settlement.hasCoverageGap).toBe(true);
    expect(settlement.hasConflict).toBe(true);
    expect(settlement.deletedOrders.value).toBeNull();
  });

  it("sums sub-cent source amounts before rounding period totals", () => {
    const g = buildSettlementGraph(input({
      itemsRung: [{ id: "food", label: "Food", value: 0.004 }, { id: "beer", label: "Beer", value: 0.004 }],
      tenders: [{ id: "cash", label: "Cash", value: 0.01 }],
    }));
    expect(g.columns[1][0].data.subtitle).toBe("$0.01 incl. GST");
    expect(g.columns[3][0].data.tone).toBe("ok");
  });
});
