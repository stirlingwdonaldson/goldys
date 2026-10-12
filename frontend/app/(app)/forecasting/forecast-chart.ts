import type { ForecastSeriesPoint } from "@/lib/api/types";

/** One chart row: the median, the lower decile, and the band's width to stack on it. */
export interface ForecastChartPoint {
  date: string;
  /** `null` gaps the median line (Recharts skips null y-values). */
  median: number | null;
  /** `null` gaps the band baseline. */
  p10: number | null;
  /** `p90 − p10`, stacked on top of `p10` so the band spans exactly p10→p90; null when either decile is missing. */
  range: number | null;
}

/**
 * Recharts stacks same-`stackId` areas cumulatively, so drawing a `p90` area on top of a
 * `p10` baseline would cover p10→p10+p90 (twice the true band). We instead stack the band's
 * *width* (`p90 − p10`) on the transparent `p10` baseline: p10 + (p90 − p10) = p90 exactly.
 */
export function toChartPoints(series: ForecastSeriesPoint[]): ForecastChartPoint[] {
  return series.map((p) => ({
    date: p.date,
    median: p.median,
    p10: p.p10,
    range: p.p90 === null || p.p10 === null ? null : p.p90 - p.p10,
  }));
}
