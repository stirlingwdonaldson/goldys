"use client";

import { useMemo } from "react";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { Skeleton } from "@/components/ui/skeleton";
import type { Api } from "@/lib/api";
import { useApiData } from "@/lib/use-api-data";
import {
  buildLineageGraph,
  LINEAGE_DOMAIN_META,
  type LineageCounts,
  type LineageDomain,
} from "./lineage-graph";

/** Resolve to the value, or null if the call fails — one unreadable count must not blank the graph. */
async function orNull<T>(p: Promise<T>): Promise<T | null> {
  try {
    return await p;
  } catch {
    return null;
  }
}

/**
 * Reads the three layer counts with `size=1` paged calls (only `total` is read, so each
 * count is one cheap request), tolerating any failure as `null`.
 */
async function loadCounts(api: Api, domain: LineageDomain): Promise<LineageCounts> {
  const meta = LINEAGE_DOMAIN_META[domain];
  const [raw, canonical, resolved] = await Promise.all([
    orNull(api.listRawRecords({}, 0, 1)),
    orNull(api.listCanonicalRows(meta.canonicalId, 0, 1)),
    orNull(api.listResolvedRows(meta.resolvedDomainId, 0, 1)),
  ]);
  return {
    raw: raw?.total ?? null,
    canonical: canonical?.total ?? null,
    resolved: resolved?.total ?? null,
  };
}

interface LineageGraphViewProps {
  domain: LineageDomain;
  /** Called when the metric node is clicked (its `drill` id is the tab id). */
  onSwitchTab?: (tabId: string) => void;
  /** Injected by tests; defaults to the demo/live singleton. */
  apiOverride?: Api;
}

/**
 * The data lineage for one sales-detail domain: the Lightspeed webhook through the raw
 * ledger, canonical entity, resolved day, to the metric. Clicking the metric node drills
 * back to the tab that shows it.
 */
export function LineageGraphView({ domain, onSwitchTab, apiOverride }: LineageGraphViewProps) {
  const counts = useApiData((api) => loadCounts(api, domain), [domain], apiOverride);
  const graph = useMemo(
    () => buildLineageGraph(domain, counts.data ?? { raw: null, canonical: null, resolved: null }),
    [domain, counts.data],
  );

  if (counts.loading) return <Skeleton className="h-[220px] w-full" />;

  const meta = LINEAGE_DOMAIN_META[domain];
  return (
    <FlowCanvas
      graph={graph}
      onDrill={onSwitchTab}
      ariaLabel={`Data lineage for ${meta.canonicalTitle.toLowerCase()}: from the Lightspeed webhook to the ${meta.metricId} metric`}
      height={220}
    />
  );
}
