import { toNumber } from "@/lib/format";
import type { SaleItemMix } from "@/lib/api/types";
import type { BarChartWidget, SeriesPoint } from "@/components/widgets/types";

/**
 * Turns the resolved sale-item mix into a stacked bar chart: one series per distinct
 * category, x = trading date, y = amount. Points keep the API's order and series keep
 * first-seen order, so the chart is stable across re-renders. The category names are
 * the venue's own labels; nothing here renames them.
 *
 * Chart values are read only from the resolved `getSaleItemMix`, never recomputed from
 * the (canonical) sale-items list.
 */
export function buildSaleItemMixWidget(mix: SaleItemMix[]): BarChartWidget {
  const order: string[] = [];
  const pointsByCategory = new Map<string, SeriesPoint[]>();

  for (const entry of mix) {
    const name = entry.categoryName;
    let points = pointsByCategory.get(name);
    if (!points) {
      points = [];
      pointsByCategory.set(name, points);
      order.push(name);
    }
    points.push({ x: entry.tradingDate, y: toNumber(entry.amount) });
  }

  return {
    schemaVersion: 2,
    id: "sale-item-mix",
    type: "bar-chart",
    title: "Sale item mix",
    description: "Resolved sale-item amounts per day, by category.",
    series: order.map((name) => ({
      key: name,
      label: name,
      points: pointsByCategory.get(name) ?? [],
    })),
    stacked: true,
    yFormat: "currency",
  };
}
