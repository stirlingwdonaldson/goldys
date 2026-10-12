import { describe, it, expect } from "vitest";
import { toChartPoints } from "./forecast-chart";
import type { ForecastSeriesPoint } from "@/lib/api/types";

describe("toChartPoints", () => {
  it("computes range as p90 minus p10 and passes date/median/p10 through", () => {
    const series: ForecastSeriesPoint[] = [
      { date: "2026-10-06", median: 10000, p10: 9400, p90: 10600 },
      { date: "2026-10-07", median: 10500, p10: 9900, p90: 11100 },
    ];

    const points = toChartPoints(series);

    expect(points).toEqual([
      { date: "2026-10-06", median: 10000, p10: 9400, range: 1200 },
      { date: "2026-10-07", median: 10500, p10: 9900, range: 1200 },
    ]);

    // The invariant the band depends on: stacking range on p10 reaches exactly p90.
    for (const p of points) {
      const source = series.find((s) => s.date === p.date)!;
      expect(p.range).toBe(source.p90 - source.p10);
      expect(p.p10 + p.range).toBe(source.p90);
    }
  });
});
