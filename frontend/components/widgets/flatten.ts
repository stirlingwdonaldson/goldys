import type { Series } from "./types";

export interface FlatRow {
  x: string;
  [key: string]: string | number | null;
}

/** Merge multiple series (keyed by x) into Recharts' tidy row shape. */
export function flattenSeries(series: Series[]): FlatRow[] {
  const rows = new Map<string, FlatRow>();
  for (const s of series) {
    for (const p of s.points) {
      const row = rows.get(p.x) ?? { x: p.x };
      row[s.key] = p.y;
      rows.set(p.x, row);
    }
  }
  return [...rows.values()];
}
