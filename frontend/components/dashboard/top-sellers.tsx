"use client";

import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import type { RankedListWidget } from "@/components/widgets/types";
import type { TopSeller } from "@/lib/api";
import { formatCurrency } from "@/lib/format";

function num(v: number | string | null): number | null {
  if (v === null || v === undefined) return null;
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : null;
}

function fmt(v: number | string | null): string {
  const n = num(v);
  return n === null ? "—" : String(Number(n.toFixed(2)));
}

function money(v: number | string | null): string {
  const n = num(v);
  return n === null ? "—" : formatCurrency(n);
}

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
      primary: fmt(t.quantitySold),
      secondary: money(t.amount),
      badge: t.hasConflict ? "unresolved" : null,
    })),
  };
  return <WidgetRenderer widget={widget} />;
}
