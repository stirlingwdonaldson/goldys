"use client";

import { useMemo } from "react";
import { AlertTriangle } from "lucide-react";
import { NetworkExplorer } from "@/components/network/network-explorer";
import type { NetworkGroupSet, NetworkKind, NetworkNode } from "@/components/network/types";
import { InlineError } from "@/components/states/inline-error";
import { PermissionDenied } from "@/components/states/permission-denied";
import { Skeleton } from "@/components/ui/skeleton";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";
import { buildPlatformMap, impactSummary, type PlatformMap } from "./platform-map-graph";

export const PLATFORM_KINDS: { kind: NetworkKind; label: string }[] = [
  { kind: "source", label: "Sources" },
  { kind: "domain", label: "Domains" },
  { kind: "metric", label: "Metrics" },
  { kind: "derived-metric", label: "Derived metrics" },
  { kind: "dashboard", label: "Dashboards" },
  { kind: "permission", label: "Permissions" },
];

/**
 * Colour groups. "Health" is the default: only problems get colour and everything healthy stays
 * grey, so a failing source and its blast radius stand out. "Kind" tells node types apart.
 */
export const PLATFORM_GROUP_SETS: NetworkGroupSet[] = [
  {
    id: "health",
    label: "Health",
    groups: [
      { id: "fail", label: "Failing", colourVar: "--destructive", matches: (n) => n.tone === "fail" },
      { id: "missing", label: "No data", colourVar: "--status-missing", matches: (n) => n.tone === "missing" },
      { id: "warn", label: "Stale", colourVar: "--status-conflict", matches: (n) => n.tone === "warn" },
    ],
  },
  {
    id: "kind",
    label: "Kind",
    groups: [
      { id: "source", label: "Sources", colourVar: "--chart-5", matches: (n) => n.kind === "source" },
      { id: "domain", label: "Domains", colourVar: "--chart-4", matches: (n) => n.kind === "domain" },
      { id: "metric", label: "Metrics", colourVar: "--chart-1", matches: (n) => n.kind === "metric" },
      { id: "derived", label: "Derived metrics", colourVar: "--chart-3", matches: (n) => n.kind === "derived-metric" },
      { id: "dashboard", label: "Dashboards", colourVar: "--brand", matches: (n) => n.kind === "dashboard" },
    ],
  },
];

interface PlatformMapViewProps {
  /** e.g. `metric:sales.gross`, from the URL or the provenance sheet. */
  initialFocusId?: string;
  /** Open as the focused node's local graph rather than the whole map. */
  initialLocal?: boolean;
  height?: number;
  /** Collapse the settings card to its icon (e.g. inside the provenance sheet). */
  compact?: boolean;
}

/**
 * The platform dependency map: sources → domains → metrics → derived metrics → dashboards, with each
 * node coloured by the health of the sources upstream of it. Focusing a failing node says what it
 * affects.
 */
export function PlatformMapView({ initialFocusId, initialLocal, height, compact }: PlatformMapViewProps) {
  const queryApi = useApi();
  const result = useQuery({ queryKey: ["getPlatformMap"], queryFn: ({ signal }) => queryApi.getPlatformMap({ signal }) });
  const map = useMemo(() => (result.data ? buildPlatformMap(result.data) : null), [result.data]);

  if (result.isPending) return <Skeleton className="w-full" style={{ height: height ?? 560 }} />;
  if (result.isLoadingError) {
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
      groupSets={PLATFORM_GROUP_SETS}
      defaultSettingsOpen={!compact}
      initialFocusId={initialFocusId}
      initialLocal={initialLocal}
      height={height}
      focusPanelExtra={(node) => <ImpactPanel node={node} map={map} />}
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
