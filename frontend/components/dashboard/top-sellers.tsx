"use client";

import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import type { RankedListWidget } from "@/components/widgets/types";
import type { TopSeller } from "@/lib/api";
import { formatCurrency, formatNumber } from "@/lib/format";

/** Ranked best-sellers, rendered through the shared widget runtime. */
export function TopSellers({ items }: { items: TopSeller[] }) {
  const widget: RankedListWidget = {
    schemaVersion: 2,
    id: "top-sellers",
    type: "ranked-list",
    title: "Top sellers · last 30 days",
    description: "Best-selling items by revenue.",
    items: items.map((t) => ({
      label: t.name,
      primary: formatNumber(t.quantitySold, 2),
      secondary: formatCurrency(t.amount),
      badge: t.hasConflict ? "unresolved" : null,
    })),
  };
  return <WidgetRenderer widget={widget} />;
}
