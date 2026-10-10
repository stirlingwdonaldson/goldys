import { toNumber } from "@/lib/format";
import type { DeletedSaleDay } from "@/lib/api/types";
import type { Series, TimeSeriesWidget } from "@/components/widgets/types";

/**
 * Turns the resolved deleted-sale totals into a time-series chart: daily count and
 * daily value inc tax. Points keep the API's date order, so the chart is stable across
 * re-renders.
 *
 * Chart values are read only from the resolved `getDeletedSaleTotals`, never recomputed
 * from the (canonical) deleted-sales list.
 */
export function buildDeletedSalesTrend(totals: DeletedSaleDay[]): TimeSeriesWidget {
  const series: Series[] =
    totals.length === 0
      ? []
      : [
          {
            key: "count",
            label: "Deleted orders",
            points: totals.map((t) => ({ x: t.tradingDate, y: t.count })),
          },
          {
            key: "totalIncTax",
            label: "Value inc tax",
            points: totals.map((t) => ({ x: t.tradingDate, y: toNumber(t.totalIncTax) })),
          },
        ];

  return {
    schemaVersion: 2,
    id: "deleted-sales-trend",
    type: "time-series",
    title: "Deleted orders per day",
    description: "Resolved deleted orders: count and value inc tax per day.",
    series,
    yFormat: "currency",
  };
}
