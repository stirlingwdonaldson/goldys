import type { NetworkEdge, NetworkGraph, NetworkKind, NetworkNode } from "./types";

/**
 * The local graph around one node, as Obsidian draws it: every node within `depth` hops
 * (edges are undirected), and only the edges between kept nodes. An unknown id gives an empty
 * graph rather than throwing, because the focused node can disappear when filters or data change.
 */
export function neighbourhood(graph: NetworkGraph, focusId: string, depth: number): NetworkGraph {
  if (!graph.nodes.some((n) => n.id === focusId)) return { nodes: [], edges: [] };
  const adjacent = adjacency(graph.edges);
  const kept = new Set([focusId]);
  let frontier = [focusId];
  for (let hop = 0; hop < depth; hop++) {
    const next: string[] = [];
    for (const id of frontier) {
      for (const other of adjacent.get(id) ?? []) {
        if (!kept.has(other)) {
          kept.add(other);
          next.push(other);
        }
      }
    }
    frontier = next;
  }
  return {
    nodes: graph.nodes.filter((n) => kept.has(n.id)),
    edges: graph.edges.filter((e) => kept.has(e.source) && kept.has(e.target)),
  };
}

/** A node's direct neighbours with the connecting edge, strongest edge first. */
export function neighboursOf(
  graph: NetworkGraph,
  id: string,
): { node: NetworkNode; edge: NetworkEdge }[] {
  const byId = new Map(graph.nodes.map((n) => [n.id, n]));
  const result: { node: NetworkNode; edge: NetworkEdge }[] = [];
  for (const edge of graph.edges) {
    const otherId = edge.source === id ? edge.target : edge.target === id ? edge.source : null;
    const other = otherId ? byId.get(otherId) : undefined;
    if (other) result.push({ node: other, edge });
  }
  return result.sort((a, b) => b.edge.weight - a.edge.weight || a.node.label.localeCompare(b.node.label));
}

/** Keeps only nodes of the visible kinds, and drops edges left dangling. */
export function filterKinds(graph: NetworkGraph, visible: ReadonlySet<NetworkKind>): NetworkGraph {
  const nodes = graph.nodes.filter((n) => visible.has(n.kind));
  const kept = new Set(nodes.map((n) => n.id));
  return { nodes, edges: graph.edges.filter((e) => kept.has(e.source) && kept.has(e.target)) };
}

/**
 * Park–Miller LCG for d3-force's `randomSource`. d3 only uses randomness to separate nodes that
 * start on top of each other, but seeding it keeps a layout reproducible for the same data.
 */
export function seededRandom(seed: number): () => number {
  const modulus = 2147483647;
  let state = Math.abs(Math.trunc(seed)) % modulus || 1;
  return () => {
    state = (state * 48271) % modulus;
    return (state - 1) / (modulus - 1);
  };
}

/**
 * The key a settled layout is cached under: the graph's purpose plus a hash of its sorted node ids,
 * so cached positions are reused only for the same node set.
 */
export function positionCacheKey(graphKey: string, graph: NetworkGraph): string {
  const joined = graph.nodes
    .map((n) => n.id)
    .sort()
    .join("|");
  // FNV-1a: short and stable; collisions only cost a fresh layout.
  let hash = 0x811c9dc5;
  for (let i = 0; i < joined.length; i++) {
    hash ^= joined.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return `network-layout:${graphKey}:${(hash >>> 0).toString(36)}`;
}

function adjacency(edges: NetworkEdge[]): Map<string, string[]> {
  const adjacent = new Map<string, string[]>();
  const link = (from: string, to: string) => {
    const list = adjacent.get(from);
    if (list) list.push(to);
    else adjacent.set(from, [to]);
  };
  for (const e of edges) {
    link(e.source, e.target);
    link(e.target, e.source);
  }
  return adjacent;
}
