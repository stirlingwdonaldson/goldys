import { describe, it, expect } from "vitest";
import type { DeletedSaleRow, PaymentRow, SaleItemRow } from "@/lib/api/types";
import { buildRelationshipGraph } from "./relationship-graph";

const payment = (name: string, amount: number | string): PaymentRow => ({
  tradingDate: "2026-10-01",
  saleNumber: "SALE-1",
  paymentTypeName: name,
  paymentTypeCode: null,
  paymentSourceType: null,
  lspayPaymentMode: null,
  clearingAccount: null,
  amount,
  tip: 0,
  tendered: amount,
  surcharge: 0,
  paymentCount: 1,
  tipCount: 0,
  reconciled: "yes",
  registerCode: null,
  registerName: null,
  staffName: null,
  staffCode: null,
  siteId: null,
  customerName: null,
});

const item = (name: string, qty: number | null, price: number | string | null): SaleItemRow => ({
  tradingDate: "2026-10-01",
  saleNumber: "SALE-1",
  receiptLineId: `line-${name}`,
  itemName: name,
  productNumber: null,
  sku: null,
  categoryName: null,
  quantitySold: qty,
  amount: null,
  soldPriceIncTax: price,
  totalTax: null,
  costIncTax: null,
  orderType: null,
  saleType: null,
  staffName: null,
  registerName: null,
  tableNumber: null,
});

const deletedOrder = (note: string | null, total: number | string): DeletedSaleRow => ({
  tradingDate: "2026-10-01",
  saleNumber: "SALE-1",
  orderType: null,
  note,
  totalIncTax: total,
  totalExTax: 0,
  totalTax: 0,
  totalCost: null,
  openedRegisterName: null,
  deletedRegisterName: null,
  staffName: null,
  deletedByStaffName: null,
  tableNumber: null,
  siteId: null,
  customerName: null,
});

describe("buildRelationshipGraph", () => {
  it("hubs the sale with a node per payment, item, and deleted order", () => {
    const g = buildRelationshipGraph(
      "SALE-1",
      [payment("Cash", 40), payment("Tyro", 60)],
      [item("Burger", 2, 18.5), item("Chips", 1, 6), item("Drink", 1, 4.5)],
      deletedOrder("Wrong table", 120),
    );
    expect(g.columns).toHaveLength(2);
    expect(g.columns[0]).toHaveLength(1);
    expect(g.columns[1]).toHaveLength(6); // 2 tenders + 3 items + 1 deleted order
    expect(g.columns.flat()).toHaveLength(7); // 1 hub + 6 children

    const hub = g.columns[0][0];
    expect(hub.data.title).toBe("Sale SALE-1");
    expect(hub.data.tone).toBe("info");

    // Every child hangs off the hub.
    const childIds = g.columns[1].map((n) => n.id);
    expect(g.edges).toHaveLength(childIds.length);
    for (const e of g.edges) {
      expect(e.source).toBe("sale");
      expect(childIds).toContain(e.target);
    }
  });

  it("includes a deleted-order node with the note as its subtitle", () => {
    const g = buildRelationshipGraph(
      "SALE-1",
      [payment("Cash", 40)],
      [],
      deletedOrder("Voided – duplicate", 120),
    );
    const deleted = g.columns[1].find((n) => n.id === "deleted");
    expect(deleted).toBeDefined();
    expect(deleted!.data.tone).toBe("fail");
    expect(deleted!.data.title).toBe("Deleted order");
    expect(deleted!.data.subtitle).toBe("Voided – duplicate");
  });

  it("falls back to the total when the deleted order has no note", () => {
    const g = buildRelationshipGraph(
      "SALE-1",
      [payment("Cash", 40)],
      [],
      deletedOrder(null, 120),
    );
    const deleted = g.columns[1].find((n) => n.id === "deleted");
    expect(deleted!.data.subtitle).toBe("$120.00");
  });

  it("omits the deleted-order node when no deleted order exists", () => {
    const g = buildRelationshipGraph(
      "SALE-1",
      [payment("Cash", 40)],
      [item("Burger", 1, 18.5)],
      null,
    );
    expect(g.columns[1].some((n) => n.id === "deleted")).toBe(false);
  });

  it("returns an empty graph when there are no payments or items", () => {
    const g = buildRelationshipGraph("SALE-1", [], [], null);
    expect(g.columns).toEqual([]);
    expect(g.edges).toEqual([]);
  });

  it("formats payment and item subtitles with currency", () => {
    const g = buildRelationshipGraph(
      "SALE-1",
      [payment("Tyro", 60)],
      [item("Burger", 2, 18.5)],
      null,
    );
    const pay = g.columns[1].find((n) => n.id === "pay:0");
    const it = g.columns[1].find((n) => n.id === "item:0");
    expect(pay!.data.title).toBe("Tyro");
    expect(pay!.data.subtitle).toBe("$60.00");
    expect(it!.data.title).toBe("Burger");
    expect(it!.data.subtitle).toBe("2 × $18.50");
  });
});
