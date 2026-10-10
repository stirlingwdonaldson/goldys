import { Boxes, CheckCircle2, Database, Gauge, Plug } from "lucide-react";
import type { ColumnGraph, FlowTone, StepNodeData } from "@/components/flow/types";

export type LineageDomain = "payment" | "deleted_sale" | "sale_item";

/** Row counts per layer; `null` means the count couldn't be read (one unreadable count must not blank the graph). */
export interface LineageCounts {
  raw: number | null;
  canonical: number | null;
  resolved: number | null;
}

interface LineageDomainMeta {
  /** The Lightspeed webhook that delivers this domain's records. */
  webhookTitle: string;
  /** The resolved canonical entity title, in the venue's words. */
  canonicalTitle: string;
  canonicalId: string;
  /** The resolved day projection title, in the venue's words. */
  resolvedTitle: string;
  resolvedDomainId: string;
  /** The semantic metric id this domain feeds. */
  metricId: string;
  /** The sales-detail tab (`?tab=`) that shows this domain's table. */
  tabId: string;
}

export const LINEAGE_DOMAIN_META: Record<LineageDomain, LineageDomainMeta> = {
  payment: {
    webhookTitle: "Lightspeed all-payments",
    canonicalTitle: "Payments",
    canonicalId: "payment",
    resolvedTitle: "Payment day",
    resolvedDomainId: "resolved_payment_day",
    metricId: "payments.amount",
    tabId: "payments",
  },
  deleted_sale: {
    webhookTitle: "Lightspeed all-deleted-orders",
    canonicalTitle: "Deleted orders",
    canonicalId: "deleted_sale",
    resolvedTitle: "Deleted-sale day",
    resolvedDomainId: "resolved_deleted_sale_day",
    metricId: "deleted_sales.amount",
    tabId: "deleted-orders",
  },
  sale_item: {
    webhookTitle: "Lightspeed sales-details",
    canonicalTitle: "Sale items",
    canonicalId: "sale_item",
    resolvedTitle: "Sale-item day",
    resolvedDomainId: "resolved_sale_item_day",
    metricId: "sale_items.amount",
    tabId: "sale-items",
  },
};

const TAB_TO_DOMAIN: Record<string, LineageDomain> = {
  payments: "payment",
  "deleted-orders": "deleted_sale",
  "sale-items": "sale_item",
};

/** The domain behind a sales-detail tab id. Unknown ids fall back to `payment`. */
export function lineageDomainForTab(tabId: string): LineageDomain {
  return TAB_TO_DOMAIN[tabId] ?? "payment";
}

function count(n: number | null, one: string, many: string): string {
  if (n == null) return "Count unavailable";
  return `${n.toLocaleString()} ${n === 1 ? one : many}`;
}

/**
 * The per-domain data lineage as a left-to-right graph: the Lightspeed webhook → the raw
 * ledger → the canonical entity → the resolved day → the metric. The metric node carries a
 * `drill` id (the tab) so the canvas can navigate back to the table that shows it.
 */
export function buildLineageGraph(domain: LineageDomain, counts: LineageCounts): ColumnGraph {
  const meta = LINEAGE_DOMAIN_META[domain];

  const columns: { id: string; data: StepNodeData }[][] = [
    [
      {
        id: "source",
        data: {
          title: meta.webhookTitle,
          icon: Plug,
          tone: "neutral",
          href: "/logs",
        },
      },
    ],
    [
      {
        id: "raw",
        data: {
          title: "Raw ledger",
          subtitle: count(counts.raw, "record", "records"),
          icon: Database,
          tone: (counts.raw === 0 ? "missing" : "neutral") as FlowTone,
          href: "/data?layer=raw",
        },
      },
    ],
    [
      {
        id: "canonical",
        data: {
          title: meta.canonicalTitle,
          subtitle: count(counts.canonical, "row", "rows"),
          icon: Boxes,
          tone: "neutral",
          href: `/data?layer=canonical&entity=${meta.canonicalId}`,
        },
      },
    ],
    [
      {
        id: "resolved",
        data: {
          title: meta.resolvedTitle,
          subtitle: count(counts.resolved, "row", "rows"),
          icon: CheckCircle2,
          tone: "neutral",
          href: `/data?layer=resolved&entity=${meta.resolvedDomainId}`,
        },
      },
    ],
    [
      {
        id: "metric",
        data: {
          title: meta.metricId,
          icon: Gauge,
          tone: "info",
          drill: meta.tabId,
        },
      },
    ],
  ];

  const edges: ColumnGraph["edges"] = [
    { source: "source", target: "raw" },
    { source: "raw", target: "canonical" },
    { source: "canonical", target: "resolved" },
    { source: "resolved", target: "metric" },
  ];

  return { columns, edges };
}
