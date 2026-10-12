import { positionCacheKey, seededRandom } from "./graph-ops";
import type { NetworkGraph } from "./types";

export type Positions = Record<string, { x: number; y: number }>;

/**
 * Reads a cached layout. Storage is a per-viewer convenience: it can be missing, full or throw
 * (private windows, blocked site data), and the graph must render the same without it.
 */
export function readPositions(graphKey: string, graph: NetworkGraph): Positions | undefined {
  try {
    const raw = window.localStorage.getItem(positionCacheKey(graphKey, graph));
    if (!raw) return undefined;
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === "object" ? (parsed as Positions) : undefined;
  } catch {
    return undefined;
  }
}

export function writePositions(graphKey: string, graph: NetworkGraph, positions: Positions): void {
  try {
    window.localStorage.setItem(positionCacheKey(graphKey, graph), JSON.stringify(positions));
  } catch {
    // Storage unavailable or full: the next visit just lays the graph out again.
  }
}

/**
 * Where each node starts before the simulation runs: its cached position if there is one, else a
 * seeded spot on a disc. The force library doesn't expose d3's `randomSource`, and d3 only needs
 * randomness for nodes that start on top of each other, so distinct, seeded starting points make
 * the same data settle into the same picture.
 */
export function initialPositions(graph: NetworkGraph, cached: Positions | undefined): Positions {
  const random = seededRandom(graph.nodes.length + 1);
  const radius = 40 * Math.sqrt(Math.max(1, graph.nodes.length));
  const sorted = [...graph.nodes].sort((a, b) => a.id.localeCompare(b.id));
  const positions: Positions = {};
  for (const node of sorted) {
    const angle = random() * 2 * Math.PI;
    const distance = radius * Math.sqrt(random());
    positions[node.id] = cached?.[node.id] ?? {
      x: distance * Math.cos(angle),
      y: distance * Math.sin(angle),
    };
  }
  return positions;
}
