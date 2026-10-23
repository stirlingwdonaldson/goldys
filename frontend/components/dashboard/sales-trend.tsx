"use client";

import { DataQualifications } from "@/components/data-display/data-qualifications";
import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import type { TimeSeriesWidget } from "@/components/widgets/types";
import type { SalesTrendPoint, MetricResultProvenance } from "@/lib/api";
import { toNumber } from "@/lib/format";

/** Resolved daily sales, rendered through the shared widget runtime. */
export function SalesTrend({ points, notices, provenance }: {
  points: SalesTrendPoint[];
  notices?: string[];
  provenance?: MetricResultProvenance;
}) {
  const widget: TimeSeriesWidget = {
    schemaVersion: 2,
    id: "sales-trend",
    type: "time-series",
    title: "Net sales by day · ex GST",
    description: provenance
      ? `${provenance.range.from} to ${provenance.range.to}. Gaps are unavailable amounts; see coverage details.`
      : "Gaps are unavailable amounts; completeness is unverified.",
    series: [
      {
        key: "total",
        label: "Net sales (ex GST)",
        points: points.map((p) => ({ x: p.date, y: toNumber(p.total) })),
      },
    ],
    yFormat: "currency",
  };
  return (
    <div className="space-y-3">
      <WidgetRenderer widget={widget} />
      <DataQualifications notices={notices} />
    </div>
  );
}
