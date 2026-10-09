"use client";

import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import type { TimeSeriesWidget } from "@/components/widgets/types";
import type { SalesTrendPoint } from "@/lib/api";

function toNumber(v: number | string | null): number | null {
  if (v == null) return null;
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : null;
}

/** Resolved daily sales, rendered through the shared widget runtime. */
export function SalesTrend({ points }: { points: SalesTrendPoint[] }) {
  const widget: TimeSeriesWidget = {
    schemaVersion: 2,
    id: "sales-trend",
    type: "time-series",
    title: "Gross sales by day",
    description: "Last 14 days, reconciled. Gaps are days still awaiting a decision.",
    series: [
      {
        key: "total",
        label: "Gross sales",
        points: points.map((p) => ({ x: p.date, y: toNumber(p.total) })),
      },
    ],
    yFormat: "currency",
  };
  return <WidgetRenderer widget={widget} />;
}
