/**
 * Shared en-AU formatting so a figure reads the same on every screen.
 *
 * Every formatter accepts the loose shapes the API returns (numbers, numeric
 * strings, null) and renders an em dash for anything missing or non-numeric.
 * A missing value is never shown as "0" (docs/design/design-system.md: never hide absence).
 */

export type Numeric = number | string | null | undefined;

const LOCALE = "en-AU";
export const EMPTY = "—";

/** Parses a number or numeric string; null when absent or not a finite number. */
export function toNumber(v: Numeric): number | null {
  if (v == null || v === "") return null;
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : null;
}

/** "$10,865.72"; `cents: false` gives "$10,866". */
export function formatCurrency(v: Numeric, options: { cents?: boolean } = {}): string {
  const n = toNumber(v);
  if (n == null) return EMPTY;
  const cents = options.cents ?? true;
  return n.toLocaleString(LOCALE, {
    style: "currency",
    currency: "AUD",
    currencyDisplay: "narrowSymbol",
    minimumFractionDigits: cents ? 2 : 0,
    maximumFractionDigits: cents ? 2 : 0,
  });
}

/** "1,284"; up to `maxDecimals` (default 0) fraction digits. */
export function formatNumber(v: Numeric, maxDecimals = 0): string {
  const n = toNumber(v);
  return n == null ? EMPTY : n.toLocaleString(LOCALE, { maximumFractionDigits: maxDecimals });
}

/** A ratio (0.141) as "14.1%". */
export function formatPercent(ratio: Numeric): string {
  const n = toNumber(ratio);
  return n == null ? EMPTY : `${(n * 100).toFixed(1)}%`;
}

/** "1,231.5 h". */
export function formatHours(v: Numeric): string {
  const n = toNumber(v);
  return n == null ? EMPTY : `${formatNumber(n, 1)} h`;
}

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/** "Sun 5 Oct" for an ISO date (YYYY-MM-DD), read as a local calendar day. Non-dates pass through. */
export function formatDay(isoDate: string): string {
  const m = ISO_DATE.exec(isoDate);
  if (!m) return isoDate;
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])).toLocaleDateString(LOCALE, {
    weekday: "short",
    day: "numeric",
    month: "short",
  });
}

/** "4 Oct" for an ISO date; non-dates pass through. Used for compact axes. */
export function formatShortDay(value: unknown): string {
  const s = String(value);
  const m = ISO_DATE.exec(s);
  if (!m) return s;
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])).toLocaleDateString(LOCALE, {
    day: "numeric",
    month: "short",
  });
}

/** "Sun 5 Oct, 3:02 am" for an ISO instant. */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(LOCALE, {
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "numeric",
    minute: "2-digit",
  });
}

/** "6:15 am" when the instant is today, otherwise "20 Sep, 7:05 pm". */
export function formatRecentTime(iso: string, now: Date = new Date()): string {
  const d = new Date(iso);
  const time = d.toLocaleTimeString(LOCALE, { hour: "numeric", minute: "2-digit" });
  if (d.toDateString() === now.toDateString()) return time;
  return `${d.toLocaleDateString(LOCALE, { day: "numeric", month: "short" })}, ${time}`;
}

/** "gross_sales" → "Gross sales". */
export function humanizeKey(key: string): string {
  const words = key.replace(/[_-]+/g, " ").trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** A compact "7 min ago" style relative time for an ISO-8601 instant. */
export function formatAgo(iso: string, now: Date = new Date()): string {
  const then = new Date(iso).getTime();
  const minutes = Math.max(0, Math.floor((now.getTime() - then) / 60_000));
  if (minutes < 1) return "just now";
  if (minutes === 1) return "1 min ago";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours === 1) return "1 hour ago";
  if (hours < 24) return `${hours} hours ago`;
  const days = Math.floor(hours / 24);
  if (days === 1) return "1 day ago";
  return `${days} days ago`;
}
