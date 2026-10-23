import type { DeletedSaleRow, PaymentRow, SaleItemRow } from "@/lib/api/types";
import { formatCurrency, formatPercent, toNumber } from "@/lib/format";

/**
 * A single sale read as a receipt with a verdict: what was rung up, how it was paid,
 * and whether the two agree. Built from the same three lists the old relationship graph
 * used (tenders, line items, the deleted order); this is a pure function so the
 * judgements can be unit-tested without rendering.
 */

/** The headline verdict for the sale, most severe first. */
export type SaleVerdict =
  /** A deleted order exists for this sale number: it was voided at the POS. */
  | "voided"
  /** Tenders and line items both present and within a cent of each other. */
  | "balanced"
  /** Tenders and line items both present but they differ by more than a cent. */
  | "mismatch"
  /** Line items but no tenders on record. */
  | "unpaid"
  /** Tenders but no line items on record. */
  | "no-items"
  /** More tenders or lines than one page fetch; totals would understate the sale. */
  | "partial";

export type CheckTone = "success" | "conflict" | "missing" | "failed" | "info";

/** One plain-language observation about the sale, shown as a checklist row. */
export interface SaleCheck {
  id: string;
  tone: CheckTone;
  text: string;
}

export interface SummaryLine {
  key: string;
  name: string;
  category: string | null;
  quantity: number | null;
  unitPrice: number | null;
  total: number | null;
}

export interface SummaryTender {
  key: string;
  name: string;
  amount: number | null;
  tip: number | null;
  surcharge: number | null;
  mode: string | null;
}

export interface SaleSummary {
  verdict: SaleVerdict;
  /** One sentence a manager can read instead of the tables. */
  headline: string;
  /** Who/where/when, deduplicated across the three sources; only the values present. */
  context: { label: string; value: string }[];
  itemsTotal: number | null;
  paidTotal: number | null;
  tipTotal: number;
  surchargeTotal: number;
  /** Items total minus line costs; null unless every line carries a cost. */
  grossProfit: number | null;
  /** Gross profit as a share of the items total. */
  margin: number | null;
  lines: SummaryLine[];
  tenders: SummaryTender[];
  deletedOrder: DeletedSaleRow | null;
  checks: SaleCheck[];
}

/**
 * Lightspeed reports money to the cent but per-line tax rounding can leave a cent of
 * drift between the line totals and the tendered amount. This is display arithmetic for
 * the verdict only, not a reconciliation rule (those live in the backend rule tables).
 */
const CENT = 0.01 + 1e-9;

function sum(values: (number | null)[]): number {
  return values.reduce<number>((acc, v) => acc + (v ?? 0), 0);
}

/** Use the reported line total; the source sold-price measure is not consistently per unit. */
function lineTotal(item: SaleItemRow): number | null {
  const amount = toNumber(item.amount);
  if (amount != null) return amount;
  return null;
}

function firstPresent(values: (string | null | undefined)[]): string | null {
  for (const v of values) if (v != null && v.trim() !== "") return v;
  return null;
}

function plural(n: number, one: string, many = `${one}s`): string {
  return `${n} ${n === 1 ? one : many}`;
}

/** "Tyro and Cash", "Tyro, Visa and Cash". */
function joinNames(names: string[]): string {
  if (names.length <= 1) return names.join("");
  return `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;
}

export function summariseSale(
  payments: PaymentRow[],
  items: SaleItemRow[],
  deletedOrder: DeletedSaleRow | null,
  truncatedPayments = 0,
  truncatedItems = 0,
): SaleSummary {
  // Biggest lines first: the receipt reads as "where the money went".
  const lines: SummaryLine[] = items
    .map((it) => ({
      key: it.receiptLineId,
      name: it.itemName ?? "(unnamed item)",
      category: it.categoryName,
      quantity: it.quantitySold,
      unitPrice: null,
      total: lineTotal(it),
    }))
    .sort((a, b) => (b.total ?? -Infinity) - (a.total ?? -Infinity));

  const tenders: SummaryTender[] = payments.map((p, i) => ({
    key: `${p.paymentTypeCode ?? p.paymentTypeName ?? "tender"}:${p.registerCode ?? ""}:${i}`,
    name: p.paymentTypeName ?? "(unnamed tender)",
    amount: toNumber(p.amount),
    tip: toNumber(p.tip),
    surcharge: toNumber(p.surcharge),
    mode: p.lspayPaymentMode,
  }));

  const hasItems = lines.length > 0 || truncatedItems > 0;
  const hasPayments = tenders.length > 0 || truncatedPayments > 0;
  // A truncated list means the sums would understate the sale, so they are withheld.
  const itemsTotal = lines.length > 0 && truncatedItems === 0 ? sum(lines.map((l) => l.total)) : null;
  const paidTotal = tenders.length > 0 && truncatedPayments === 0 ? sum(tenders.map((t) => t.amount)) : null;
  const tipTotal = sum(tenders.map((t) => t.tip));
  const surchargeTotal = sum(tenders.map((t) => t.surcharge));

  // POS cost is source evidence, not a recipe-backed profit calculation.
  const grossProfit = null;
  const margin = null;

  const difference = itemsTotal != null && paidTotal != null ? paidTotal - itemsTotal : null;

  let verdict: SaleVerdict;
  if (deletedOrder) verdict = "voided";
  else if (hasItems && !hasPayments) verdict = "unpaid";
  else if (hasPayments && !hasItems) verdict = "no-items";
  else if (truncatedPayments > 0 || truncatedItems > 0) verdict = "partial";
  else if (difference != null && Math.abs(difference) > CENT) verdict = "mismatch";
  else verdict = "balanced";

  const checks: SaleCheck[] = [];

  if (deletedOrder) {
    const who = deletedOrder.deletedByStaffName ?? deletedOrder.staffName;
    const reason = deletedOrder.note ? ` — “${deletedOrder.note}”` : "";
    checks.push({
      id: "voided",
      tone: "failed",
      text: `Voided${who ? ` by ${who}` : ""} with ${formatCurrency(deletedOrder.totalIncTax)} on the order${reason}.`,
    });
    if (hasPayments) {
      checks.push({
        id: "voided-paid",
        tone: "conflict",
        text: "Tenders are still recorded against this voided sale; check whether it was refunded or re-rung.",
      });
    }
  }

  if (difference != null) {
    if (Math.abs(difference) <= CENT) {
      checks.push({
        id: "balance",
        tone: "success",
        text: "Tenders match the line items.",
      });
    } else if (difference > 0) {
      checks.push({
        id: "balance",
        tone: "conflict",
        text: `Tenders are ${formatCurrency(difference)} more than the line items. Some lines may be missing from the sale-items report.`,
      });
    } else {
      checks.push({
        id: "balance",
        tone: "conflict",
        text: `Line items are ${formatCurrency(-difference)} more than the tenders. A discount, comp or missing tender would explain it.`,
      });
    }
  } else if (!deletedOrder && hasItems && !hasPayments) {
    checks.push({
      id: "balance",
      tone: "missing",
      text: "No tenders recorded for this sale yet.",
    });
  } else if (!deletedOrder && hasPayments && !hasItems) {
    checks.push({
      id: "balance",
      tone: "missing",
      text: "No line items recorded for this sale yet.",
    });
  }

  const tenderNames = [...new Set(tenders.map((t) => t.name))];
  if (tenders.length > 1) {
    checks.push({
      id: "split",
      tone: "info",
      text: `Split across ${plural(tenders.length, "tender")}: ${joinNames(tenderNames)}.`,
    });
  }
  if (tipTotal > 0) {
    const share = paidTotal ? ` (${formatPercent(tipTotal / paidTotal)} of the bill)` : "";
    checks.push({
      id: "tip",
      tone: "info",
      text: `${formatCurrency(tipTotal)} tip${share}.`,
    });
  }
  if (surchargeTotal > 0) {
    checks.push({
      id: "surcharge",
      tone: "info",
      text: `${formatCurrency(surchargeTotal)} card surcharge charged.`,
    });
  }

  for (const item of items) {
    const adjustment = item.adjustment;
    const amount = toNumber(adjustment?.amountIncTax);
    if (adjustment && amount != null && amount !== 0) {
      checks.push({
        id: `adjustment:${item.receiptLineId}`,
        tone: "info",
        text: `${item.itemName ?? "Item"}: ${[adjustment.promotion, adjustment.type, adjustment.reason, adjustment.level].filter(Boolean).join(" · ") || "Adjustment"} (${formatCurrency(amount)}, already included in the line total).`,
      });
    }
  }

  const unpriced = lines.filter((l) => l.total == null).length;
  if (unpriced > 0) {
    checks.push({
      id: "unpriced",
      tone: "missing",
      text: `${plural(unpriced, "line")} without a price.`,
    });
  }
  if (truncatedPayments > 0 || truncatedItems > 0) {
    const parts = [
      truncatedItems > 0 ? plural(truncatedItems, "more line item") : null,
      truncatedPayments > 0 ? plural(truncatedPayments, "more tender") : null,
    ].filter((p): p is string => p != null);
    checks.push({
      id: "truncated",
      tone: "missing",
      text: `Only part of this sale is shown (${parts.join(", ")} not loaded), so totals are withheld.`,
    });
  }

  const headline = buildHeadline(verdict, lines, tenderNames, itemsTotal, paidTotal, deletedOrder);

  const context = [
    {
      label: "Date",
      value: firstPresent([items[0]?.tradingDate, payments[0]?.tradingDate, deletedOrder?.tradingDate]),
    },
    {
      label: "Table",
      value: firstPresent([...items.map((i) => i.tableNumber), deletedOrder?.tableNumber]),
    },
    {
      label: "Order type",
      value: firstPresent([...items.map((i) => i.orderType), deletedOrder?.orderType]),
    },
    {
      label: "Register",
      value: firstPresent([
        ...payments.map((p) => p.registerName),
        ...items.map((i) => i.registerName),
        deletedOrder?.openedRegisterName,
      ]),
    },
    {
      label: "Staff",
      value: firstPresent([
        ...payments.map((p) => p.staffName),
        ...items.map((i) => i.staffName),
        deletedOrder?.staffName,
      ]),
    },
    {
      label: "Customer",
      value: firstPresent([...payments.map((p) => p.customerName), deletedOrder?.customerName]),
    },
  ].filter((c): c is { label: string; value: string } => c.value != null);

  return {
    verdict,
    headline,
    context,
    itemsTotal,
    paidTotal,
    tipTotal,
    surchargeTotal,
    grossProfit,
    margin,
    lines,
    tenders,
    deletedOrder,
    checks,
  };
}

function buildHeadline(
  verdict: SaleVerdict,
  lines: SummaryLine[],
  tenderNames: string[],
  itemsTotal: number | null,
  paidTotal: number | null,
  deletedOrder: DeletedSaleRow | null,
): string {
  const units = sum(lines.map((l) => l.quantity));
  const itemsPart = lines.length > 0 ? plural(units > 0 ? units : lines.length, "item") : null;
  const paidWith = tenderNames.length > 0 ? ` by ${joinNames(tenderNames)}` : "";
  switch (verdict) {
    case "voided":
      return `Voided order worth ${formatCurrency(deletedOrder?.totalIncTax ?? null)}.`;
    case "balanced":
      return `${itemsPart ?? "Sale"} for ${formatCurrency(paidTotal ?? itemsTotal)}, paid in full${paidWith}.`;
    case "mismatch":
      return `${itemsPart ?? "Sale"} for ${formatCurrency(itemsTotal)}, ${formatCurrency(paidTotal)} paid${paidWith}.`;
    case "unpaid":
      return `${itemsPart ?? "Sale"} for ${formatCurrency(itemsTotal)}, no payment on record.`;
    case "no-items":
      return `${formatCurrency(paidTotal)} paid${paidWith}, no line items on record.`;
    case "partial":
      return "Large sale: only the first page of lines and tenders is shown.";
  }
}
