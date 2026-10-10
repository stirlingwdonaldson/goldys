import { toNumber } from "@/lib/format";
import type { PaymentMix } from "@/lib/api/types";
import type { BarChartWidget, SeriesPoint } from "@/components/widgets/types";

/**
 * Turns the resolved payment mix into a stacked bar chart: one series per distinct
 * payment type, x = trading date, y = amount. Points keep the API's order and series
 * keep first-seen order, so the chart is stable across re-renders. The type names are
 * the venue's own tender labels; nothing here renames them.
 *
 * Chart values are read only from the resolved `getPaymentMix`, never recomputed from
 * the (canonical) payments list.
 */
export function buildPaymentMixWidget(mix: PaymentMix[]): BarChartWidget {
  const order: string[] = [];
  const pointsByType = new Map<string, SeriesPoint[]>();

  for (const entry of mix) {
    const name = entry.paymentTypeName;
    let points = pointsByType.get(name);
    if (!points) {
      points = [];
      pointsByType.set(name, points);
      order.push(name);
    }
    points.push({ x: entry.tradingDate, y: toNumber(entry.amount) });
  }

  return {
    schemaVersion: 2,
    id: "payment-mix",
    type: "bar-chart",
    title: "Payment mix",
    description: "Resolved tender amounts per day.",
    series: order.map((name) => ({ key: name, label: name, points: pointsByType.get(name) ?? [] })),
    stacked: true,
    yFormat: "currency",
  };
}
