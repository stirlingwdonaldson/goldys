import { Banknote, CircleDollarSign, ReceiptText, ShoppingBasket, Trash2 } from "lucide-react";
import type { ColumnGraph, FlowTone } from "@/components/flow/types";
import { formatCurrency, formatNumber, toNumber, type Numeric } from "@/lib/format";
import type { DeletedSaleDay, PaymentMix, SaleItemMix } from "@/lib/api/types";

/** One aggregate in the settlement: a category of items rung, or a tender type. */
export interface SettlementItem {
  id: string;
  label: string;
  value: number | null;
}

export interface SettlementInput {
  /** Items rung in the period, grouped by category (e.g. Drinks, Food, Other). */
  itemsRung: SettlementItem[];
  /** Deleted orders are separate observations, never a deduction from completed sales. */
  deletedOrders: { count: number; value: number | null };
  /** Tenders received, grouped by payment type (e.g. Tyro, Cash, LS Payments). */
  tenders: SettlementItem[];
  hasConflict?: boolean;
  hasCoverageGap?: boolean;
}

/** The raw, date-scoped API rows the settlement is aggregated from. */
export interface SettlementSources {
  saleItemMix: SaleItemMix[];
  paymentMix: PaymentMix[];
  deletedSaleTotals: DeletedSaleDay[];
}

/** Keep the source's four decimal places while summing; round only the displayed totals. */
function sumAmounts(values: Numeric[]): number | null {
  if (values.length === 0) return null;
  let units = 0;
  for (const value of values) {
    const amount = toNumber(value);
    if (amount === null) return null;
    units += Math.round(amount * 10_000);
    if (!Number.isSafeInteger(units)) return null;
  }
  return units / 10_000;
}

/** AUD comparisons use cents, with half cents rounded away from zero on either sign. */
function cents(value: number | null): number | null {
  if (value === null) return null;
  const units = Math.round(value * 10_000);
  const rounded = Math.round(Math.abs(units) / 100);
  return rounded === 0 ? 0 : Math.sign(units) * rounded;
}

/** Groups per-day mix rows without treating an absent amount as zero. */
function groupTotals<T>(rows: T[], name: (row: T) => string, value: (row: T) => Numeric): SettlementItem[] {
  const groups = new Map<string, Numeric[]>();
  for (const row of rows) {
    const key = name(row);
    const values = groups.get(key) ?? [];
    values.push(value(row));
    groups.set(key, values);
  }
  return [...groups.entries()]
    .map(([label, values]) => ({ id: label, label, value: sumAmounts(values) }))
    .sort((a, b) => (b.value ?? -Infinity) - (a.value ?? -Infinity));
}

/** Aggregates the date-scoped API rows into the graph's input shape. */
export function buildSettlementInput(sources: SettlementSources): SettlementInput {
  const saleDates = new Set(sources.saleItemMix.map((row) => row.tradingDate));
  const paymentDates = new Set(sources.paymentMix.map((row) => row.tradingDate));
  return {
    itemsRung: groupTotals(sources.saleItemMix, (r) => r.categoryName, (r) => r.amount),
    tenders: groupTotals(sources.paymentMix, (r) => r.paymentTypeName, (r) => r.amount),
    deletedOrders: {
      count: sources.deletedSaleTotals.reduce((count, day) => count + day.count, 0),
      value: sumAmounts(sources.deletedSaleTotals.map((day) => day.totalIncTax)),
    },
    hasConflict: [...sources.saleItemMix, ...sources.paymentMix, ...sources.deletedSaleTotals]
      .some((row) => row.hasConflict),
    hasCoverageGap: saleDates.size !== paymentDates.size || [...saleDates].some((date) => !paymentDates.has(date)),
  };
}

function formatDifference(value: number | null): string {
  if (value === null) return "Unavailable";
  if (value > 0) return `+${formatCurrency(value)}`;
  if (value < 0) return `−${formatCurrency(-value)}`;
  return formatCurrency(0);
}

function amountTone(value: number | null): FlowTone {
  return value === null ? "missing" : "neutral";
}

function comparisonTone(difference: number | null, qualified: boolean): FlowTone {
  if (difference === null) return "missing";
  return difference !== 0 || qualified ? "warn" : "ok";
}

function moneyEdge(source: string, target: string, value: number | null): ColumnGraph["edges"][number] {
  if (value === null) return { source, target, tone: "missing" };
  return { source, target, value };
}

/** Compare observed merchandise and POS tenders; this is not bank settlement evidence. */
export function buildSettlementGraph(input: SettlementInput): ColumnGraph {
  const salesCents = cents(sumAmounts(input.itemsRung.map((item) => item.value)));
  const paymentCents = cents(sumAmounts(input.tenders.map((item) => item.value)));
  const salesRecorded = salesCents === null ? null : salesCents / 100;
  const posPayments = paymentCents === null ? null : paymentCents / 100;
  const difference = salesCents === null || paymentCents === null ? null : (paymentCents - salesCents) / 100;

  const items = input.itemsRung.map((item) => ({
    id: `items:${item.id}`,
    data: {
      title: item.label,
      subtitle: formatCurrency(item.value),
      icon: ShoppingBasket,
      tone: amountTone(item.value),
    },
  }));

  const sales = [
    {
      id: "sales",
      data: {
        title: "Merchandise sales",
        subtitle: `${formatCurrency(salesRecorded)} incl. GST`,
        icon: ReceiptText,
        tone: amountTone(salesRecorded),
        emphasis: true,
      },
    },
    {
      id: "deleted",
      data: {
        title: "Deleted orders",
        subtitle: `${formatNumber(input.deletedOrders.count)} orders · ${formatCurrency(input.deletedOrders.value)}`,
        icon: Trash2,
        tone: (input.deletedOrders.value === null ? "missing" : input.deletedOrders.count > 0 ? "warn" : "neutral") as FlowTone,
      },
    },
  ];

  const tenders = input.tenders.map((tender) => ({
    id: `tender:${tender.id}`,
    data: {
      title: tender.label,
      subtitle: formatCurrency(tender.value),
      icon: Banknote,
      tone: amountTone(tender.value),
    },
  }));

  const payments = [
    {
      id: "settled",
      data: {
        title: "POS payments",
        subtitle: formatCurrency(posPayments),
        detail: `Difference vs sales: ${formatDifference(difference)}`,
        icon: CircleDollarSign,
        tone: comparisonTone(difference, Boolean(input.hasConflict || input.hasCoverageGap)),
        emphasis: true,
      },
    },
  ];

  const edges: ColumnGraph["edges"] = [];
  for (const item of input.itemsRung) {
    edges.push(moneyEdge(`items:${item.id}`, "sales", item.value));
  }
  for (const tender of input.tenders) {
    edges.push(moneyEdge("sales", `tender:${tender.id}`, tender.value));
  }
  for (const tender of input.tenders) {
    edges.push(moneyEdge(`tender:${tender.id}`, "settled", tender.value));
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
    columnTitles: ["Merchandise", "Sales", "Tenders", "POS payments"],
  };
}
