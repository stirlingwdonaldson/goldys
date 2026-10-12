import type { FlowTone } from "@/components/flow/types";

/**
 * What a node in a force-directed network stands for. The platform map uses the first six;
 * the menu affinity map uses `product`.
 */
export type NetworkKind =
  | "source"
  | "domain"
  | "metric"
  | "derived-metric"
  | "dashboard"
  | "permission"
  | "product";

export const NETWORK_KINDS: readonly NetworkKind[] = [
  "source",
  "domain",
  "metric",
  "derived-metric",
  "dashboard",
  "permission",
  "product",
];

export interface NetworkNode {
  /** Stable and namespaced by kind, e.g. `metric:sales.gross`. */
  id: string;
  kind: NetworkKind;
  label: string;
  /** One short line for the tooltip, focus panel and list. */
  subtitle?: string;
  /** Shares the flow graphs' status vocabulary so both families use the same colours. */
  tone: FlowTone;
  /** Relative size, 1..10; the canvas maps it to a radius. */
  size: number;
  /** Colour group: a product category, or the kind for the platform map. */
  group?: string;
  /** Where the focus panel's "Open" link goes. */
  href?: string;
}

export interface NetworkEdge {
  source: string;
  target: string;
  /** 0..1; drives stroke width and link strength. */
  weight: number;
  label?: string;
  tone?: FlowTone;
}

/** An undirected graph: edge direction carries no meaning. */
export interface NetworkGraph {
  nodes: NetworkNode[];
  edges: NetworkEdge[];
}

/**
 * A colour group, as in Obsidian: nodes matching it take its colour; the first matching group
 * wins and unmatched nodes stay grey. `colourVar` is a design-token CSS variable, e.g.
 * `--destructive`.
 */
export interface NetworkGroup {
  id: string;
  label: string;
  colourVar: string;
  matches: (node: NetworkNode) => boolean;
}

/** One way of colouring a graph ("Health", "Kind"); the viewer picks between sets. */
export interface NetworkGroupSet {
  id: string;
  label: string;
  groups: NetworkGroup[];
}
