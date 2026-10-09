"use client";

import { useMemo } from "react";
import type { Api, ConnectorStatus, EntityDescriptor } from "@/lib/api/types";
import { useApiData } from "@/lib/use-api-data";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { Skeleton } from "@/components/ui/skeleton";
import { buildPipelineGraph, type CountedLayer, type PipelineInput } from "./pipeline-graph";

/** Resolve to the value, or null if the call fails — one unreadable count must not blank the map. */
async function orNull<T>(p: Promise<T>): Promise<T | null> {
  try {
    return await p;
  } catch {
    return null;
  }
}

async function counted(
  layers: EntityDescriptor[],
  total: (id: string) => Promise<{ total: number }>,
): Promise<CountedLayer[]> {
  return Promise.all(
    layers.map(async (l) => ({ ...l, count: (await orNull(total(l.id)))?.total ?? null })),
  );
}

/**
 * Gathers everything the pipeline map shows. Counts come from the explorer's paged endpoints
 * with `size=1` — only `total` is read, so each count is one cheap request.
 */
async function loadPipeline(api: Api, connectors: ConnectorStatus[]): Promise<PipelineInput> {
  const [canonicalList, resolvedList, rawAll, rules, ...rawPerSource] = await Promise.all([
    orNull(api.listCanonicalEntities()),
    orNull(api.listResolvedDomains()),
    orNull(api.listRawRecords({}, 0, 1)),
    orNull(api.listResolutionRules()),
    ...connectors.map((c) => orNull(api.listRawRecords({ source: c.source }, 0, 1))),
  ]);
  const [canonical, resolved] = await Promise.all([
    counted(canonicalList ?? [], (id) => api.listCanonicalRows(id, 0, 1)),
    counted(resolvedList ?? [], (id) => api.listResolvedRows(id, 0, 1)),
  ]);
  return {
    connectors,
    rawCountBySource: Object.fromEntries(
      connectors.map((c, i) => [c.source, rawPerSource[i]?.total ?? null]),
    ),
    rawTotal: rawAll?.total ?? null,
    canonical,
    resolved,
    ruleCount: rules?.length ?? null,
  };
}

/**
 * The data pipeline as an n8n-style node graph. Takes the already-loaded connector list from
 * the Data health screen so the two views never disagree about a source's status.
 */
export function PipelineMap({ connectors }: { connectors: ConnectorStatus[] }) {
  const input = useApiData((api) => loadPipeline(api, connectors), [connectors]);
  const graph = useMemo(() => (input.data ? buildPipelineGraph(input.data) : null), [input.data]);
  const tallest = graph ? Math.max(...graph.columns.map((c) => c.length)) : 0;

  if (input.loading) return <Skeleton className="h-[360px] w-full" />;
  if (!graph) return null;
  return (
    <FlowCanvas
      graph={graph}
      ariaLabel="Data pipeline: sources, raw ledger, canonical entities, resolution, resolved domains"
      height={Math.min(480, Math.max(260, tallest * 72 + 60))}
    />
  );
}
