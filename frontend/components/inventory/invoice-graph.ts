import { Inbox, Package, Receipt, Truck } from "lucide-react";
import type { ColumnGraph, FlowTone, StepNodeData } from "@/components/flow/types";
import type { InvoiceGraphNode, LineGraphNode, SupplierGraphNode } from "@/lib/api/types";

export const SUPPLIER_TOP_N = 8;
export const INVOICE_TOP_N = 8;
export const LINE_TOP_N = 10;
export const UNKNOWN_SUPPLIER = "Unknown";

export function supplierDrillId(name: string): string {
  return `supplier:${name}`;
}

export function invoiceDrillId(number: string): string {
  return `invoice:${number}`;
}

export interface InvoiceGraphInput {
  suppliers: SupplierGraphNode[];
  invoices: InvoiceGraphNode[] | null;
  lines: LineGraphNode[] | null;
  focusedSupplier: string | null;
  focusedInvoice: string | null;
}

function money(v: number | null | undefined): string {
  return v == null || !Number.isFinite(Number(v)) ? "—" : `$${Number(v).toFixed(2)}`;
}

function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}

/** Top-N, but never a rollup of one: a single leftover is promoted to a normal node. */
function split<T>(items: T[], n: number): { shown: T[]; rest: T[] } {
  if (items.length <= n + 1) {
    return { shown: items, rest: [] };
  }
  return { shown: items.slice(0, n), rest: items.slice(n) };
}

export function buildInvoiceGraph(input: InvoiceGraphInput): ColumnGraph {
  const real = input.suppliers
    .filter((s) => s.name !== UNKNOWN_SUPPLIER)
    .slice()
    .sort((a, b) => (b.totalSpend ?? -1) - (a.totalSpend ?? -1));
  const unknown = input.suppliers.find((s) => s.name === UNKNOWN_SUPPLIER);

  const { shown: topSuppliers, rest: restSuppliers } = split(real, SUPPLIER_TOP_N);

  const supplierNodes: { id: string; data: StepNodeData }[] = topSuppliers.map((s) => ({
    id: `sup:${s.name}`,
    data: {
      title: s.name,
      subtitle: `${money(s.totalSpend)} · ${plural(s.invoiceCount, "invoice", "invoices")}`,
      icon: Truck,
      tone: (s.invoiceCount > 0 && (s.totalSpend == null || s.totalSpend === 0)
        ? "missing"
        : "neutral") as FlowTone,
      drill: supplierDrillId(s.name),
    },
  }));

  if (restSuppliers.length > 0) {
    supplierNodes.push({
      id: "sup:__other__",
      data: {
        title: `Other ${plural(restSuppliers.length, "supplier", "suppliers")}`,
        subtitle: money(restSuppliers.reduce((sum, s) => sum + (s.totalSpend ?? 0), 0)),
        icon: Truck,
        tone: "neutral",
      },
    });
  }

  if (unknown) {
    supplierNodes.push({
      id: "sup:unknown",
      data: {
        title: "Unknown supplier",
        subtitle: money(unknown.totalSpend),
        detail: "Lines with no matching invoice",
        icon: Truck,
        tone: "warn",
      },
    });
  }

  if (supplierNodes.length === 0) {
    return {
      columns: [[{
        id: "empty",
        data: {
          title: "No invoices in range",
          subtitle: "Nothing ingested for this period",
          icon: Inbox,
          tone: "missing",
        },
      }]],
      edges: [],
    };
  }

  const columns: { id: string; data: StepNodeData }[][] = [supplierNodes];
  const edges: ColumnGraph["edges"] = [];

  if (input.focusedSupplier && input.invoices) {
    const sorted = input.invoices
      .slice()
      .sort((a, b) => b.invoiceDate.localeCompare(a.invoiceDate));
    const { shown: topInvoices, rest: restInvoices } = split(sorted, INVOICE_TOP_N);

    const invoiceNodes: { id: string; data: StepNodeData }[] = topInvoices.map((inv) => ({
      id: `inv:${inv.invoiceNumber}`,
      data: {
        title: inv.invoiceNumber,
        subtitle: `${inv.invoiceDate} · ${money(inv.totalAmount)}`,
        detail: inv.purchaseNumber ?? inv.pdfFilename ?? undefined,
        icon: Receipt,
        tone: "neutral",
        drill: invoiceDrillId(inv.invoiceNumber),
      },
    }));

    if (restInvoices.length > 0) {
      invoiceNodes.push({
        id: "inv:__other__",
        data: {
          title: `Other ${plural(restInvoices.length, "invoice", "invoices")}`,
          subtitle: money(restInvoices.reduce((sum, i) => sum + (i.totalAmount ?? 0), 0)),
          icon: Receipt,
          tone: "neutral",
        },
      });
    }

    columns.push(invoiceNodes);
    for (const n of invoiceNodes) {
      edges.push({ source: `sup:${input.focusedSupplier}`, target: n.id });
    }
  }

  if (input.focusedInvoice && input.lines) {
    const deduped = dedupeLines(input.lines);
    const { shown: topLines, rest: restLines } = split(deduped, LINE_TOP_N);

    const lineNodes: { id: string; data: StepNodeData }[] = topLines.map((l) => ({
      id: `line:${input.focusedInvoice}:${l.productNameKey ?? l.stockCode ?? "(unnamed)"}`,
      data: {
        title: l.productNameKey ?? l.stockCode ?? "(unnamed)",
        subtitle: `${l.quantity} × ${money(l.unitCost)}`,
        detail: l.uom ?? undefined,
        icon: Package,
        tone: "neutral",
      },
    }));

    if (restLines.length > 0) {
      lineNodes.push({
        id: "line:__other__",
        data: {
          title: `Other ${plural(restLines.length, "line", "lines")}`,
          subtitle: money(restLines.reduce((sum, l) => sum + l.lineTotal, 0)),
          icon: Package,
          tone: "neutral",
        },
      });
    }

    columns.push(lineNodes);
    for (const n of lineNodes) {
      edges.push({ source: `inv:${input.focusedInvoice}`, target: n.id });
    }
  }

  return { columns, edges };
}

/** Sums duplicate product keys so a split line can't double-count, then ranks by line total. */
function dedupeLines(lines: LineGraphNode[]): LineGraphNode[] {
  const byKey = new Map<string, LineGraphNode>();
  for (const l of lines) {
    const key = l.productNameKey ?? l.stockCode ?? "(unnamed)";
    const existing = byKey.get(key);
    if (existing) {
      byKey.set(key, {
        ...existing,
        quantity: existing.quantity + l.quantity,
        lineTotal: existing.lineTotal + l.lineTotal,
      });
    } else {
      byKey.set(key, l);
    }
  }
  return [...byKey.values()].sort((a, b) => b.lineTotal - a.lineTotal);
}
