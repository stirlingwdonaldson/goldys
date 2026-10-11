import type { FlowTone } from "@/components/flow/types";
import { NETWORK_KINDS, type NetworkGraph, type NetworkKind, type NetworkNode } from "@/components/network/types";
import type { NetworkGraphDto } from "@/lib/api/types";

/** Where each kind sits in the data flow. Tone and impact only travel to a higher rank. */
const RANK: Partial<Record<NetworkKind, number>> = {
  source: 0,
  domain: 1,
  metric: 2,
  "derived-metric": 3,
  dashboard: 4,
};

/** Worst first. A node shows the worst tone among everything upstream of it. */
const SEVERITY: FlowTone[] = ["fail", "missing", "warn", "info", "ok", "neutral"];
const TONES = new Set<string>(SEVERITY);

const worse = (a: FlowTone, b: FlowTone): FlowTone =>
  SEVERITY.indexOf(a) <= SEVERITY.indexOf(b) ? a : b;

export interface PlatformMap {
  graph: NetworkGraph;
  /** Every node downstream of each node (permissions excluded), for "what does this affect?". */
  impact: Map<string, string[]>;
}

/**
 * Turns the platform map response into a network graph and computes the blast radius: the backend
 * only knows the tone of each source, so each domain, metric, derived metric and dashboard takes the
 * worst tone of the sources upstream of it. Permission nodes are not data and never inherit a tone.
 *
 * Edges are undirected on the wire, so direction comes from kind rank. Two derived metrics linked
 * to each other (a derived metric built from another) are ordered by the edge itself: the
 * catalogue sends `derived → input`.
 */
export function buildPlatformMap(dto: NetworkGraphDto): PlatformMap {
  const known = new Set<string>(NETWORK_KINDS);
  const nodes: NetworkNode[] = dto.nodes
    .filter((n) => known.has(n.kind))
    .map((n) => ({
      id: n.id,
      kind: n.kind as NetworkKind,
      label: n.label,
      subtitle: n.subtitle ?? undefined,
      tone: TONES.has(n.tone) ? (n.tone as FlowTone) : "neutral",
      size: n.size,
      group: n.group ?? n.kind,
      href: n.href ?? undefined,
    }));
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const edges = dto.edges
    .filter((e) => byId.has(e.source) && byId.has(e.target))
    .map((e) => ({ source: e.source, target: e.target, weight: e.weight, label: e.label ?? undefined }));

  // upstream id → downstream ids, only between ranked (data-flow) kinds.
  const downstream = new Map<string, string[]>();
  for (const e of edges) {
    const a = byId.get(e.source)!;
    const b = byId.get(e.target)!;
    const rankA = RANK[a.kind];
    const rankB = RANK[b.kind];
    if (rankA === undefined || rankB === undefined) continue;
    // Equal ranks fall to the else branch: `derived → input` means the input is upstream.
    const [up, down] = rankA < rankB ? [a, b] : [b, a];
    downstream.set(up.id, [...(downstream.get(up.id) ?? []), down.id]);
  }

  // Inherited tone: start from the sources and push down until nothing changes. Bounded by the
  // node count, so a cycle in bad data can't loop forever.
  const inherited = new Map<string, FlowTone>();
  for (const n of nodes) if (n.kind === "source") inherited.set(n.id, n.tone);
  for (let pass = 0; pass < nodes.length; pass++) {
    let changed = false;
    for (const [up, downs] of downstream) {
      const tone = inherited.get(up);
      if (!tone) continue;
      for (const down of downs) {
        const current = inherited.get(down);
        const next = current ? worse(current, tone) : tone;
        if (next !== current) {
          inherited.set(down, next);
          changed = true;
        }
      }
    }
    if (!changed) break;
  }

  const graph: NetworkGraph = {
    nodes: nodes.map((n) => (n.kind === "source" ? n : { ...n, tone: inherited.get(n.id) ?? "neutral" })),
    edges,
  };

  const impact = new Map<string, string[]>();
  for (const n of nodes) {
    const seen = new Set<string>();
    const queue = [...(downstream.get(n.id) ?? [])];
    while (queue.length > 0) {
      const id = queue.shift()!;
      if (seen.has(id)) continue;
      seen.add(id);
      queue.push(...(downstream.get(id) ?? []));
    }
    impact.set(n.id, [...seen]);
  }

  return { graph, impact };
}

const PLURAL: Partial<Record<NetworkKind, [string, string]>> = {
  domain: ["domain", "domains"],
  metric: ["metric", "metrics"],
  "derived-metric": ["derived metric", "derived metrics"],
  dashboard: ["dashboard", "dashboards"],
};

const PROBLEM: Partial<Record<FlowTone, string>> = {
  fail: "is failing",
  missing: "has no data",
  warn: "is stale",
};

/** "Cooking the Books is failing → 3 domains, 5 metrics, … affected", or null when healthy. */
export function impactSummary(node: NetworkNode, map: PlatformMap): string | null {
  const problem = PROBLEM[node.tone];
  if (!problem) return null;
  const affected = map.impact.get(node.id) ?? [];
  if (affected.length === 0) return `${node.label} ${problem}`;
  const kinds = new Map(map.graph.nodes.map((n) => [n.id, n.kind]));
  const counts = new Map<NetworkKind, number>();
  for (const id of affected) {
    const kind = kinds.get(id);
    if (kind) counts.set(kind, (counts.get(kind) ?? 0) + 1);
  }
  const parts = (Object.keys(PLURAL) as NetworkKind[])
    .filter((kind) => counts.has(kind))
    .map((kind) => {
      const count = counts.get(kind)!;
      const [one, many] = PLURAL[kind]!;
      return `${count} ${count === 1 ? one : many}`;
    });
  return `${node.label} ${problem} → ${parts.join(", ")} affected`;
}
