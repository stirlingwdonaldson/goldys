import { Banknote, CircleDollarSign, ReceiptText, ShoppingBasket, Trash2 } from "lucide-react";
import type { ColumnGraph, FlowTone } from "@/components/flow/types";
import { formatCurrency, formatNumber, toNumber, type Numeric } from "@/lib/format";
import type { DeletedSaleDay, PaymentMix, SaleItemMix } from "@/lib/api/types";

/** One aggregate in the settlement: a category of items rung, or a tender type. */
export interface SettlementItem {
  id: string;
  label: string;
  value: number;
}

export interface SettlementInput {
  /** Items rung in the period, grouped by category (e.g. Drinks, Food, Other). */
  itemsRung: SettlementItem[];
  /** Deleted orders in the period — money that left sales but never became a tender. */
  deletedOrders: { count: number; value: number };
  /** Tenders received, grouped by payment type (e.g. Tyro, Cash, LS Payments). */
  tenders: SettlementItem[];
}

/** The raw, date-scoped API rows the settlement is aggregated from. */
export interface SettlementSources {
  saleItemMix: SaleItemMix[];
  paymentMix: PaymentMix[];
  deletedSaleTotals: DeletedSaleDay[];
}

function sum(items: SettlementItem[]): number {
  return items.reduce((total, item) => total + item.value, 0);
}

/** Groups per-day mix rows into one period total per name, largest first. */
function groupTotals<T>(rows: T[], name: (row: T) => string, value: (row: T) => Numeric): SettlementItem[] {
  const totals = new Map<string, number>();
  for (const row of rows) {
    const key = name(row);
    totals.set(key, (totals.get(key) ?? 0) + (toNumber(value(row)) ?? 0));
  }
  return [...totals.entries()]
    .map(([label, total]) => ({ id: label, label, value: total }))
    .sort((a, b) => b.value - a.value);
}

/** Aggregates the date-scoped API rows into the graph's input shape. */
export function buildSettlementInput(sources: SettlementSources): SettlementInput {
  return {
    itemsRung: groupTotals(sources.saleItemMix, (r) => r.categoryName, (r) => r.amount),
    tenders: groupTotals(sources.paymentMix, (r) => r.paymentTypeName, (r) => r.amount),
    deletedOrders: sources.deletedSaleTotals.reduce(
      (acc, day) => ({
        count: acc.count + day.count,
        value: acc.value + (toNumber(day.totalIncTax) ?? 0),
      }),
      { count: 0, value: 0 },
    ),
  };
}

/** A signed dollar amount for the difference line: "−$42", "+$50", or "$0". */
function formatDifference(value: number): string {
  if (value > 0) return `+${formatCurrency(value, { cents: false })}`;
  if (value < 0) return `−${formatCurrency(-value, { cents: false })}`;
  return formatCurrency(0, { cents: false });
}

/**
 * The daily settlement as a four-column graph:
 *
 *   Items rung → Sales recorded → Tenders → Settled (Payments)
 *                 ↘ Deleted orders (dashed, below the main flow)
 *
 * Gross sales, settled payments, and the difference are all derived from the inputs rather
 * than passed in, so the diagram can never draw two numbers that disagree. The deleted-orders
 * node sits in the Sales column, joined by a dashed annotation edge, because those dollars
 * left sales without becoming a tender.
 */
export function buildSettlementGraph(input: SettlementInput): ColumnGraph {
  const salesRecorded = sum(input.itemsRung);
  const settledPayments = sum(input.tenders);
  const difference = settledPayments - salesRecorded;

  const items = input.itemsRung.map((item) => ({
    id: `items:${item.id}`,
    data: {
      title: item.label,
      subtitle: formatCurrency(item.value, { cents: false }),
      icon: ShoppingBasket,
      tone: "neutral" as FlowTone,
    },
  }));

  const sales = [
    {
      id: "sales",
      data: {
        title: "Sales recorded",
        subtitle: `${formatCurrency(salesRecorded, { cents: false })} gross`,
        icon: ReceiptText,
        tone: "neutral" as FlowTone,
        emphasis: true,
      },
    },
    {
      id: "deleted",
      data: {
        title: "Deleted orders",
        subtitle: `${formatNumber(input.deletedOrders.count)} orders · ${formatCurrency(input.deletedOrders.value, { cents: false })}`,
        icon: Trash2,
        tone: "warn" as FlowTone,
      },
    },
  ];

  const tenders = input.tenders.map((tender) => ({
    id: `tender:${tender.id}`,
    data: {
      title: tender.label,
      subtitle: formatCurrency(tender.value, { cents: false }),
      icon: Banknote,
      tone: "neutral" as FlowTone,
    },
  }));

  const payments = [
    {
      id: "settled",
      data: {
        title: "Payments",
        subtitle: formatCurrency(settledPayments, { cents: false }),
        detail: `Difference vs sales: ${formatDifference(difference)}`,
        icon: CircleDollarSign,
        tone: (difference === 0 ? "ok" : "warn") as FlowTone,
        emphasis: true,
      },
    },
  ];

  const edges: ColumnGraph["edges"] = [];
  for (const item of input.itemsRung) {
    edges.push({ source: `items:${item.id}`, target: "sales", value: item.value });
  }
  for (const tender of input.tenders) {
    edges.push({ source: "sales", target: `tender:${tender.id}`, value: tender.value });
  }
  for (const tender of input.tenders) {
    edges.push({ source: `tender:${tender.id}`, target: "settled", value: tender.value });
  }
  // Deleted orders are an annotation, not flowing money: a dashed edge, no value to scale.
  edges.push({
    source: "sales",
    target: "deleted",
    tone: "missing",
    label: `${formatNumber(input.deletedOrders.count)} orders`,
  });

  return {
    columns: [items, sales, tenders, payments],
    edges,
    columnTitles: ["Items rung", "Sales", "Tenders", "Settled"],
  };
}
