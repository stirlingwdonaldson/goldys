"use client";

import { DataQualifications } from "@/components/data-display/data-qualifications";
import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import type { RankedListWidget } from "@/components/widgets/types";
import type { TopSeller, MetricCoverage } from "@/lib/api";
import { formatCurrency, formatNumber } from "@/lib/format";

/** Ranked best-sellers, rendered through the shared widget runtime. */
export function TopSellers({ items, notices, coverage }: {
  items: TopSeller[];
  notices?: string[];
  coverage?: MetricCoverage;
}) {
  const widget: RankedListWidget = {
    schemaVersion: 2,
    id: "top-sellers",
    type: "ranked-list",
    title: "Top sellers",
    description: `Ranked by known sales; full-period ranking unverified.${coverage ? ` ${coverage.from} to ${coverage.to}.` : ""}`,
    items: items.map((t) => ({
      label: t.name,
      primary: formatNumber(t.quantitySold, 2),
      secondary: formatCurrency(t.amount),
      badge: t.hasConflict ? "unresolved" : null,
    })),
  };
  return (
    <div className="space-y-3">
      <WidgetRenderer widget={widget} />
      <DataQualifications notices={notices} />
    </div>
  );
}
