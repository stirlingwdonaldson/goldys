import type { LineGraphNode } from "@/lib/api/types";

/**
 * Pure helpers behind the invoice detail view: display names, the lines-vs-header total check,
 * and the unit-cost comparison against the supplier's previous invoice. Kept free of React so
 * the rules are unit-testable.
 */

function normalise(s: string): string {
  return s.trim().replace(/\s+/g, " ").toLowerCase();
}

/** The product name key with its first letter capitalised ("royal blue potatoes" → "Royal blue potatoes"). */
export function displayProductName(productNameKey: string): string {
  const name = productNameKey.trim();
  return name ? name[0].toUpperCase() + name.slice(1) : name;
}

/**
 * The stock code worth showing under the product name, or null when it only repeats the name.
 * Cooking the Books often uses the upper-cased product name as the stock code.
 */
export function distinctStockCode(line: Pick<LineGraphNode, "productNameKey" | "stockCode">): string | null {
  if (!line.stockCode) return null;
  return normalise(line.stockCode) === normalise(line.productNameKey) ? null : line.stockCode;
}

/** Key used to match the same item across two invoices from one supplier. */
export function lineMatchKey(line: Pick<LineGraphNode, "productNameKey" | "stockCode">): string {
  return line.stockCode ? `code:${normalise(line.stockCode)}` : `name:${normalise(line.productNameKey)}`;
}

export interface TotalsCheck {
  linesTotal: number;
  /** headerExTax − linesTotal; positive means the lines come up short. */
  difference: number;
  matches: boolean;
}

/**
 * Compares the sum of ex-tax line totals to the invoice's ex-tax header total. Null when there's
 * nothing to compare (no lines, or no ex-tax header total). Each line total is rounded to the cent
 * on the source document, so allow a cent per line of drift, with a 5c floor.
 */
export function checkTotals(lines: LineGraphNode[], headerExTax: number | null): TotalsCheck | null {
  if (lines.length === 0 || headerExTax == null) return null;
  const linesTotal = lines.reduce((sum, l) => sum + l.lineTotal, 0);
  const difference = headerExTax - linesTotal;
  const tolerance = Math.max(0.05, lines.length * 0.01);
  return { linesTotal, difference, matches: Math.abs(difference) <= tolerance };
}

export interface PriceChange {
  previousUnitCost: number;
  /** (current − previous) / previous, e.g. 0.12 for a 12% rise. */
  ratio: number;
}

/** Changes smaller than this are rounding noise, not a price movement. */
const MIN_CHANGE = 0.005;

/**
 * Unit-cost movement per current line versus the previous invoice's matching line, keyed by
 * {@link lineMatchKey}. Lines with no match, no change, or a zero previous cost are omitted.
 * When an item appears more than once on the previous invoice, the first occurrence wins.
 */
export function priceChanges(
  current: LineGraphNode[],
  previous: LineGraphNode[],
): Map<string, PriceChange> {
  const before = new Map<string, number>();
  for (const l of previous) {
    const key = lineMatchKey(l);
    if (!before.has(key)) before.set(key, l.unitCost);
  }
  const changes = new Map<string, PriceChange>();
  for (const l of current) {
    const key = lineMatchKey(l);
    const previousUnitCost = before.get(key);
    if (previousUnitCost == null || previousUnitCost === 0) continue;
    const ratio = (l.unitCost - previousUnitCost) / previousUnitCost;
    if (Math.abs(ratio) >= MIN_CHANGE) changes.set(key, { previousUnitCost, ratio });
  }
  return changes;
}

/** The ISO date one calendar day before `isoDate` (YYYY-MM-DD). */
export function dayBefore(isoDate: string): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().slice(0, 10);
}

/** "in 5 days", "today", "3 days ago" for an ISO due date relative to `today` (local calendar). */
export function relativeDue(isoDate: string, today: Date = new Date()): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const due = Date.UTC(y, m - 1, d);
  const now = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
  const days = Math.round((due - now) / 86_400_000);
  if (days === 0) return "today";
  if (days === 1) return "tomorrow";
  if (days === -1) return "yesterday";
  return days > 0 ? `in ${days} days` : `${-days} days ago`;
}
