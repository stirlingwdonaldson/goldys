import { toNumber } from "@/lib/format";
import type { DeletedSaleDay } from "@/lib/api/types";
import type { BarChartWidget, TimeSeriesWidget } from "@/components/widgets/types";

/**
 * Turns the resolved deleted-sale totals into two charts: how many orders were deleted
 * each day, and what they were worth inc tax. They are separate charts on purpose: a
 * count and a dollar value can't share one axis or one format (on a shared currency axis
 * a count of 3 read as "$3.00" and was dwarfed by the dollar series). Points keep the
 * API's date order, so the charts are stable across re-renders.
 *
 * Chart values are read only from the resolved `getDeletedSaleTotals`, never recomputed
 * from the (canonical) deleted-sales list.
 */
export function buildDeletedSalesTrend(totals: DeletedSaleDay[]): {
  count: BarChartWidget;
  value: TimeSeriesWidget;
} {
  const empty = totals.length === 0;
  return {
    count: {
      schemaVersion: 2,
      id: "deleted-sales-count",
      type: "bar-chart",
      title: "Deleted orders per day",
      description: "Number of resolved deleted orders each day.",
      series: empty
        ? []
        : [
            {
              key: "count",
              label: "Deleted orders",
              points: totals.map((t) => ({ x: t.tradingDate, y: t.count })),
            },
          ],
      yFormat: "number",
    },
    value: {
      schemaVersion: 2,
      id: "deleted-sales-value",
      type: "time-series",
      title: "Deleted value per day",
      description: "Value inc tax of the resolved deleted orders each day.",
      series: empty
        ? []
        : [
            {
              key: "totalIncTax",
              label: "Value inc tax",
              points: totals.map((t) => ({ x: t.tradingDate, y: toNumber(t.totalIncTax) })),
            },
          ],
      yFormat: "currency",
    },
  };
}
