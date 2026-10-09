import { describe, it, expect } from "vitest";
import type { InvoiceGraphNode, LineGraphNode, SupplierGraphNode } from "@/lib/api/types";
import {
  buildInvoiceGraph,
  SUPPLIER_TOP_N,
  supplierDrillId,
  invoiceDrillId,
  type InvoiceGraphInput,
} from "./invoice-graph";

const supplier = (name: string, invoiceCount: number, totalSpend: number | null): SupplierGraphNode => ({
  name,
  invoiceCount,
  totalSpend,
});

const invoice = (number: string, date: string, totalAmount: number): InvoiceGraphNode => ({
  invoiceNumber: number,
  invoiceDate: date,
  totalAmount,
  purchaseNumber: null,
  pdfFilename: null,
});

const line = (key: string, lineTotal: number, uom: string | null = null): LineGraphNode => ({
  productNameKey: key,
  stockCode: null,
  quantity: 1,
  unitCost: lineTotal,
  lineTotal,
  uom,
});

const input = (overrides: Partial<InvoiceGraphInput> = {}): InvoiceGraphInput => ({
  suppliers: [],
  invoices: null,
  lines: null,
  focusedSupplier: null,
  focusedInvoice: null,
  ...overrides,
});

describe("buildInvoiceGraph", () => {
  it("renders the top-N suppliers plus an Other rollup", () => {
    const suppliers = Array.from({ length: SUPPLIER_TOP_N + 2 }, (_, i) =>
      supplier(`Supplier ${i}`, 1, 100 - i),
    );
    const g = buildInvoiceGraph(input({ suppliers }));
    expect(g.columns).toHaveLength(1);
    const col0 = g.columns[0];
    expect(col0).toHaveLength(SUPPLIER_TOP_N + 1);
    expect(col0[0].data.title).toBe("Supplier 0");
    expect(col0[SUPPLIER_TOP_N].data.title).toBe("Other 2 suppliers");
  });

  it("shows the Unknown supplier as a warn, non-drillable node that is never folded into Other", () => {
    const suppliers = [
      supplier("A. Foods", 1, 100),
      supplier("Unknown", 0, 12.4),
    ];
    const g = buildInvoiceGraph(input({ suppliers }));
    const col0 = g.columns[0];
    const unknown = col0.find((n) => n.id === "sup:unknown");
    expect(unknown).toBeDefined();
    expect(unknown!.data.tone).toBe("warn");
    expect(unknown!.data.drill).toBeUndefined();
    expect(col0.some((n) => n.id === "sup:__other__")).toBe(false);
  });

  it("returns a single missing node for an empty range", () => {
    const g = buildInvoiceGraph(input({ suppliers: [] }));
    expect(g.columns).toHaveLength(1);
    expect(g.columns[0][0].id).toBe("empty");
    expect(g.columns[0][0].data.tone).toBe("missing");
    expect(g.columns[0][0].data.title).toBe("No invoices in range");
  });

  it("adds the invoices column and edges when a supplier is focused", () => {
    const g = buildInvoiceGraph(
      input({
        suppliers: [supplier("A. Foods", 2, 150)],
        focusedSupplier: "A. Foods",
        invoices: [invoice("INV-2", "2026-09-28", 50), invoice("INV-1", "2026-09-01", 100)],
      }),
    );
    expect(g.columns).toHaveLength(2);
    const inv = g.columns[1];
    expect(inv[0].data.title).toBe("INV-2"); // newest first
    expect(inv[0].data.drill).toBe(invoiceDrillId("INV-2"));
    expect(g.edges.some((e) => e.source === "sup:A. Foods" && e.target === "inv:INV-2")).toBe(true);
  });

  it("dedupes identical product keys and orders lines by line total", () => {
    const lines = [
      line("chicken breast", 50),
      line("chicken breast", 30), // duplicates the key above -> summed to 80
      line("beef mince", 90),
    ];
    const g = buildInvoiceGraph(
      input({ suppliers: [supplier("A. Foods", 1, 100)], focusedSupplier: "A. Foods",
              invoices: [invoice("INV-1", "2026-09-01", 100)], focusedInvoice: "INV-1", lines }),
    );
    expect(g.columns).toHaveLength(3);
    const linesColumn = g.columns[2];
    // beef mince (90) ranks above the deduped chicken breast (80)
    expect(linesColumn[0].data.title).toBe("beef mince");
    expect(linesColumn[1].data.title).toBe("chicken breast");
  });

  it("never renders an Other rollup for a single excess item (promote instead)", () => {
    const suppliers = Array.from({ length: SUPPLIER_TOP_N + 1 }, (_, i) =>
      supplier(`Supplier ${i}`, 1, 100 - i),
    );
    const g = buildInvoiceGraph(input({ suppliers }));
    const col0 = g.columns[0];
    // 9 suppliers: top-8 + the 9th promoted as a normal node (no "Other 1 suppliers" rollup)
    expect(col0.some((n) => n.data.title.startsWith("Other 1"))).toBe(false);
    expect(col0).toHaveLength(SUPPLIER_TOP_N + 1);
  });
});
