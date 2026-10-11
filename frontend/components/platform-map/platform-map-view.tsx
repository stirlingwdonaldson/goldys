"use client";

import { useCallback, useMemo } from "react";
import { AlertTriangle } from "lucide-react";
import { NetworkExplorer } from "@/components/network/network-explorer";
import type { NetworkKind, NetworkNode } from "@/components/network/types";
import { useCssColours } from "@/components/network/use-css-colours";
import { InlineError } from "@/components/states/inline-error";
import { PermissionDenied } from "@/components/states/permission-denied";
import { Skeleton } from "@/components/ui/skeleton";
import { useApiData } from "@/lib/use-api-data";
import { buildPlatformMap, impactSummary, type PlatformMap } from "./platform-map-graph";

export const PLATFORM_KINDS: { kind: NetworkKind; label: string }[] = [
  { kind: "source", label: "Sources" },
  { kind: "domain", label: "Domains" },
  { kind: "metric", label: "Metrics" },
  { kind: "derived-metric", label: "Derived metrics" },
  { kind: "dashboard", label: "Dashboards" },
  { kind: "permission", label: "Permissions" },
];

/** Fill per kind, from the chart palette; status shows as the node's ring, not its fill. */
const KIND_COLOUR_VARS = {
  source: "--chart-5",
  domain: "--chart-4",
  metric: "--chart-1",
  "derived-metric": "--chart-3",
  dashboard: "--brand",
  permission: "--muted-foreground",
  product: "--muted-foreground",
} satisfies Record<NetworkKind, string>;

interface PlatformMapViewProps {
  /** e.g. `metric:sales.gross`, from the URL or the provenance sheet. */
  initialFocusId?: string;
  /** Open as the focused node's local graph rather than the whole map. */
  initialLocal?: boolean;
  height?: number;
}

/**
 * The platform dependency map: sources → domains → metrics → derived metrics → dashboards, with each
 * node coloured by the health of the sources upstream of it. Focusing a failing node says what it
 * affects.
 */
export function PlatformMapView({ initialFocusId, initialLocal, height }: PlatformMapViewProps) {
  const result = useApiData((api) => api.getPlatformMap());
  const colours = useCssColours(KIND_COLOUR_VARS);
  const map = useMemo(() => (result.data ? buildPlatformMap(result.data) : null), [result.data]);
  const highlightFor = useCallback((node: NetworkNode) => map?.impact.get(node.id) ?? [], [map]);

  if (result.loading) return <Skeleton className="h-[480px] w-full" />;
  if (result.error) {
    if (result.error.code === "NOT_PERMITTED") return <PermissionDenied subject="the platform map" />;
    return <InlineError>Couldn&apos;t load the platform map. {result.error.message}</InlineError>;
  }
  if (!map) return null;

  return (
    <NetworkExplorer
      graph={map.graph}
      graphKey="platform-map"
      ariaLabel="Platform dependency map"
      kinds={PLATFORM_KINDS}
      hiddenByDefault={["permission"]}
      colourFor={(node) => colours[node.kind] ?? "#8a8a8a"}
      initialFocusId={initialFocusId}
      initialLocal={initialLocal}
      height={height}
      focusPanelExtra={(node) => <ImpactPanel node={node} map={map} />}
      highlightFor={highlightFor}
    />
  );
}

function ImpactPanel({ node, map }: { node: NetworkNode; map: PlatformMap }) {
  const summary = impactSummary(node, map);
  if (!summary) return null;
  const labels = new Map(map.graph.nodes.map((n) => [n.id, n.label]));
  const affected = (map.impact.get(node.id) ?? [])
    .map((id) => ({ id, label: labels.get(id) ?? id }))
    .sort((a, b) => a.label.localeCompare(b.label));
  return (
    <div className="rounded-md border border-status-conflict/40 bg-status-conflict-soft p-2.5 text-xs">
      <p className="flex items-start gap-1.5 font-medium">
        <AlertTriangle className="mt-px size-3.5 shrink-0 text-status-conflict" aria-hidden="true" />
        {summary}
      </p>
      {affected.length > 0 && (
        <ul className="mt-1.5 list-disc pl-5 text-muted-foreground">
          {affected.map(({ id, label }) => (
            <li key={id}>{label}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
