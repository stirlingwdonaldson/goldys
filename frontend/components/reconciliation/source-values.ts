import type { SourceValue } from "@/lib/api";

/** Parses "$14,980.50" or "14980.5" to a number; null when the value isn't numeric. */
export function parseAmount(value: string | null): number | null {
  if (value == null) return null;
  const cleaned = value.replace(/[$,\s]/g, "");
  if (cleaned === "") return null;
  const n = Number(cleaned);
  return Number.isFinite(n) ? n : null;
}

/**
 * The gap between two numeric source values, so the size of a disagreement is
 * visible without mental arithmetic (heuristic 6). Null for non-numeric values,
 * a missing source, or more than two sources.
 */
export function difference(sources: SourceValue[]): number | null {
  if (sources.length !== 2) return null;
  const [a, b] = sources.map((s) => parseAmount(s.value));
  if (a == null || b == null) return null;
  return Math.abs(a - b);
}
