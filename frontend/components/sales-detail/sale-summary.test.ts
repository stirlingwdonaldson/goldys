import { describe, it, expect } from "vitest";
import type { DeletedSaleRow, PaymentRow, SaleItemRow } from "@/lib/api/types";
import { summariseSale } from "./sale-summary";

const payment = (name: string, amount: number, extra: Partial<PaymentRow> = {}): PaymentRow => ({
  tradingDate: "2026-10-01",
  saleNumber: "SALE-1",
  paymentTypeName: name,
  paymentTypeCode: name,
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
  registerName: "Main Bar",
  staffName: "Alex",
  staffCode: null,
  siteId: null,
  customerName: null,
  ...extra,
});

const item = (
  name: string,
  qty: number | null,
  price: number | null,
  extra: Partial<SaleItemRow> = {},
): SaleItemRow => ({
  tradingDate: "2026-10-01",
  saleNumber: "SALE-1",
  receiptLineId: `line-${name}`,
  itemName: name,
  productNumber: null,
  sku: null,
  categoryName: "Beer",
  quantitySold: qty,
  amount: qty != null && price != null ? qty * price : null,
  soldPriceIncTax: price,
  totalTax: null,
  costIncTax: qty != null && price != null ? (qty * price) / 4 : null,
  orderType: "Dine-in",
  saleType: null,
  staffName: null,
  registerName: null,
  tableNumber: "8",
  ...extra,
});

const deleted = (extra: Partial<DeletedSaleRow> = {}): DeletedSaleRow => ({
  tradingDate: "2026-10-01",
  saleNumber: "SALE-1",
  orderType: null,
  note: "wrong table",
  totalIncTax: 120,
  totalExTax: 0,
  totalTax: 0,
  totalCost: null,
  openedRegisterName: null,
  deletedRegisterName: null,
  staffName: null,
  deletedByStaffName: "Sam",
  tableNumber: null,
  siteId: null,
  customerName: null,
  ...extra,
});

const ids = (s: ReturnType<typeof summariseSale>) => s.checks.map((c) => c.id);

describe("summariseSale", () => {
  it("calls a sale balanced when tenders match the line items within a cent", () => {
    const s = summariseSale([payment("Tyro", 35.01)], [item("pint", 2, 12), item("wine", 1, 11)], null);
    expect(s.verdict).toBe("balanced");
    expect(s.itemsTotal).toBe(35);
    expect(s.paidTotal).toBe(35.01);
    expect(s.headline).toBe("3 items for $35.01, paid in full by Tyro.");
    expect(s.checks.find((c) => c.id === "balance")?.tone).toBe("success");
  });

  it("sorts lines by total and computes margin from line costs", () => {
    const s = summariseSale([payment("Tyro", 35)], [item("wine", 1, 11), item("pint", 2, 12)], null);
    expect(s.lines.map((l) => l.name)).toEqual(["pint", "wine"]);
    expect(s.grossProfit).toBeCloseTo(26.25);
    expect(s.margin).toBeCloseTo(0.75);
  });

  it("flags a mismatch with the direction and size of the gap", () => {
    const over = summariseSale([payment("Tyro", 57.5)], [item("pint", 2, 12), item("wine", 1, 11)], null);
    expect(over.verdict).toBe("mismatch");
    expect(over.checks.find((c) => c.id === "balance")?.text).toContain("Tenders are $22.50 more");

    const under = summariseSale([payment("Cash", 20)], [item("pint", 2, 12)], null);
    expect(under.checks.find((c) => c.id === "balance")?.text).toContain("Line items are $4.00 more");
  });

  it("notes split tenders, tips and surcharges", () => {
    const s = summariseSale(
      [payment("Tyro", 20, { tip: 2 }), payment("Visa", 4, { surcharge: 0.45 })],
      [item("pint", 2, 12)],
      null,
    );
    expect(s.verdict).toBe("balanced");
    expect(s.tipTotal).toBe(2);
    expect(s.checks.find((c) => c.id === "split")?.text).toBe("Split across 2 tenders: Tyro and Visa.");
    expect(s.checks.find((c) => c.id === "tip")?.text).toBe("$2.00 tip (8% of the bill).");
    expect(ids(s)).toContain("surcharge");
  });

  it("puts a voided sale first and warns when tenders survive the void", () => {
    const s = summariseSale([payment("Cash", 10)], [], deleted());
    expect(s.verdict).toBe("voided");
    expect(s.checks[0].text).toBe("Voided by Sam with $120.00 on the order — “wrong table”.");
    expect(ids(s)).toContain("voided-paid");
  });

  it("reports missing tenders or lines rather than calling the sale balanced", () => {
    expect(summariseSale([], [item("pint", 1, 12)], null).verdict).toBe("unpaid");
    expect(summariseSale([payment("Tyro", 12)], [], null).verdict).toBe("no-items");
  });

  it("spots discounted lines and lines without a price or cost", () => {
    const s = summariseSale(
      [payment("Tyro", 20)],
      [item("pint", 2, 12, { amount: 20 }), item("mystery", null, null)],
      null,
    );
    expect(ids(s)).toEqual(expect.arrayContaining(["discount", "unpriced", "uncosted"]));
    expect(s.grossProfit).toBeNull();
  });

  it("withholds totals when a list was truncated", () => {
    const s = summariseSale([payment("Tyro", 12)], [item("pint", 1, 12)], null, 0, 3);
    expect(s.verdict).toBe("partial");
    expect(s.itemsTotal).toBeNull();
    expect(s.checks.find((c) => c.id === "truncated")?.text).toContain("3 more line items");
  });

  it("collects context from whichever source has it", () => {
    const s = summariseSale([payment("Tyro", 12)], [item("pint", 1, 12)], null);
    expect(s.context).toEqual([
      { label: "Date", value: "2026-10-01" },
      { label: "Table", value: "8" },
      { label: "Order type", value: "Dine-in" },
      { label: "Register", value: "Main Bar" },
      { label: "Staff", value: "Alex" },
    ]);
  });
});
