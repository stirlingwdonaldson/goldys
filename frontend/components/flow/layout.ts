import { MarkerType } from "@xyflow/react";
import { Box, type LucideIcon } from "lucide-react";
import type { ColumnGraph, FlowEdge, FlowNode, FlowTone, StepNodeData } from "./types";

export const NODE_WIDTH = 216;
export const NODE_HEIGHT = 64;
const COLUMN_GAP = 120;
const ROW_GAP = 20;
/** Vertical layout: gap between side-by-side nodes in a stage, and between stages. */
const VERTICAL_SIDE_GAP = 24;
const VERTICAL_STAGE_GAP = 56;
const HEADING_HEIGHT = 24;
const HEADING_GAP = 12;
const MIN_EDGE_WIDTH = 2;
const MAX_EDGE_WIDTH = 6;
/** Tree layout: gap between sibling nodes along the cross axis, and between parent/child levels. */
const TREE_SIBLING_GAP = 24;
const TREE_LEVEL_GAP = 48;
/** Dendrogram layout: gap between adjacent leaves, and the vertical span reserved for internal nodes. */
const DENDRO_LEAF_GAP = 24;
const DENDRO_HEIGHT_BAND = 200;

/** Left-to-right (the default) or top-to-bottom, for graphs with many stages. */
export type FlowDirection = "horizontal" | "vertical";

/** Edge stroke per tone. `neutral`/`ok` stay quiet grey so only problems draw the eye. */
const EDGE_STROKE: Record<FlowTone, string> = {
  ok: "hsl(var(--muted-foreground) / 0.45)",
  neutral: "hsl(var(--muted-foreground) / 0.45)",
  info: "hsl(var(--status-info))",
  warn: "hsl(var(--status-conflict))",
  fail: "hsl(var(--destructive))",
  missing: "hsl(var(--status-missing))",
};

/** Maps a value to a stroke width between the fixed min/max, linear in the value's range. */
function edgeWidth(value: number, min: number, max: number): number {
  if (max <= min) return (MIN_EDGE_WIDTH + MAX_EDGE_WIDTH) / 2;
  return MIN_EDGE_WIDTH + ((value - min) / (max - min)) * (MAX_EDGE_WIDTH - MIN_EDGE_WIDTH);
}

/**
 * Which direction shows a graph largest in a viewport of the given size: wide graphs
 * with many stages read better top-to-bottom, tall single stages better left-to-right.
 */
export function bestDirection(graph: ColumnGraph, viewport: { width: number; height: number }): FlowDirection {
  const stages = Math.max(1, graph.columns.length);
  const widest = Math.max(1, ...graph.columns.map((c) => c.length));
  const zoom = (w: number, h: number) => Math.min(viewport.width / w, viewport.height / h);
  const horizontal = zoom(
    stages * NODE_WIDTH + (stages - 1) * COLUMN_GAP,
    widest * NODE_HEIGHT + (widest - 1) * ROW_GAP,
  );
  const vertical = zoom(
    widest * NODE_WIDTH + (widest - 1) * VERTICAL_SIDE_GAP,
    stages * NODE_HEIGHT + (stages - 1) * VERTICAL_STAGE_GAP,
  );
  return vertical > horizontal ? "vertical" : "horizontal";
}

/**
 * Lays a {@link ColumnGraph} out in stages, n8n style: each column is a stage, nodes in a
 * column stack along the cross axis and are centred against the tallest column so fan-in/fan-out
 * edges stay symmetric. Left-to-right by default, top-to-bottom when `direction` is `"vertical"`.
 * Deterministic (no physics), so the same data always draws the same picture.
 *
 * When `columnTitles` is present, a quiet heading node is placed above each titled column and
 * the column's nodes shift down to make room. Edges that carry a `value` get a stroke width
 * proportional to that value (relative to the other valued edges); the rest keep the fixed
 * tone-based width.
 */
export function layoutColumns(
  graph: ColumnGraph,
  options: { columnGap?: number; direction?: FlowDirection } = {},
): { nodes: FlowNode[]; edges: FlowEdge[] } {
  const columnGap = options.columnGap ?? COLUMN_GAP;
  const vertical = options.direction === "vertical";
  const hasHeadings = graph.columnTitles?.some((t) => t != null) ?? false;
  const topOffset = hasHeadings ? HEADING_HEIGHT + HEADING_GAP : 0;

  const values = graph.edges
    .map((e) => e.value)
    .filter((v): v is number => typeof v === "number" && Number.isFinite(v) && v > 0);
  const minValue = values.length ? Math.min(...values) : 0;
  const maxValue = values.length ? Math.max(...values) : 0;

  const tallest = Math.max(1, ...graph.columns.map((c) => c.length));
  const lastColumn = graph.columns.length - 1;
  // Within a stage, nodes stack along the cross axis: down the page when horizontal,
  // across it when vertical. Each stage is centred against the widest one.
  const crossSize = vertical ? NODE_WIDTH : NODE_HEIGHT;
  const crossGap = vertical ? VERTICAL_SIDE_GAP : ROW_GAP;
  const stageStep = vertical ? NODE_HEIGHT + VERTICAL_STAGE_GAP : NODE_WIDTH + columnGap;
  const fullCross = tallest * crossSize + (tallest - 1) * crossGap;

  const nodes: FlowNode[] = [];
  graph.columns.forEach((column, col) => {
    const along = col * stageStep;
    const title = graph.columnTitles?.[col];
    if (title) {
      nodes.push({
        id: `heading:${col}`,
        type: "columnHeading",
        position: vertical ? { x: 0, y: along } : { x: along, y: 0 },
        data: { title },
        draggable: false,
        selectable: false,
        focusable: false,
        connectable: false,
        deletable: false,
      });
    }
    const extent = column.length * crossSize + Math.max(0, column.length - 1) * crossGap;
    const start = topOffset + (fullCross - extent) / 2;
    column.forEach((n, row) => {
      const across = start + row * (crossSize + crossGap);
      nodes.push({
        id: n.id,
        type: "step" as const,
        position: vertical ? { x: across, y: along } : { x: along, y: across },
        data: { hasInput: col > 0, hasOutput: col < lastColumn, vertical, ...n.data },
        draggable: true,
        connectable: false,
      });
    });
  });

  const edges: FlowEdge[] = graph.edges.map((e) => {
    const tone = e.tone ?? "neutral";
    const stroke = EDGE_STROKE[tone];
    const hasValue = typeof e.value === "number" && Number.isFinite(e.value) && e.value > 0;
    const width =
      hasValue && e.value != null
        ? edgeWidth(e.value, minValue, maxValue)
        : tone === "neutral" || tone === "ok"
          ? 1.5
          : 2;
    return {
      id: `${e.source}->${e.target}`,
      source: e.source,
      target: e.target,
      label: e.label,
      data: { tone },
      style: {
        stroke,
        strokeWidth: width,
        // Dashed = nothing is flowing yet (a placeholder, a never-run source, an empty layer).
        strokeDasharray: tone === "missing" ? "5 5" : undefined,
      },
      markerEnd: { type: MarkerType.ArrowClosed, width: 14, height: 14, color: stroke },
      labelStyle: { fontSize: 11, fill: "hsl(var(--muted-foreground))" },
      labelBgStyle: { fill: "hsl(var(--background))" },
      labelBgPadding: [6, 3] as [number, number],
      labelBgBorderRadius: 4,
    };
  });

  return { nodes, edges };
}

/** A node in a hierarchical tree: a group has category children, categories may have product leaves. */
export interface TreeNode {
  id: string;
  data: StepNodeData;
  children?: TreeNode[];
}

/** A node in a dendrogram's flat parent-link list. Leaves are `isLeaf`; internal nodes merge below the root. */
export interface DendrogramInputNode {
  id: string;
  parentId: string | null;
  isLeaf: boolean;
  label: string;
  /** Merge distance: higher merges sit higher (closer to the root) in the picture. */
  height: number;
  size: number;
}

export interface DendrogramOptions {
  /** Horizontal gap between adjacent leaves. */
  leafGap?: number;
  /** Vertical span (px) reserved for the internal nodes above the leaf row. */
  heightBand?: number;
  /** Icon shown on every node. */
  icon?: LucideIcon;
  /** Tone applied to every node and edge. */
  tone?: FlowTone;
}

/** Builds a child edge with the same tone-driven stroke and arrow as {@link layoutColumns}. */
function childEdge(source: string, target: string, tone: FlowTone): FlowEdge {
  const stroke = EDGE_STROKE[tone];
  return {
    id: `${source}->${target}`,
    source,
    target,
    data: { tone },
    style: {
      stroke,
      strokeWidth: tone === "neutral" || tone === "ok" ? 1.5 : 2,
      strokeDasharray: tone === "missing" ? "5 5" : undefined,
    },
    markerEnd: { type: MarkerType.ArrowClosed, width: 14, height: 14, color: stroke },
    labelStyle: { fontSize: 11, fill: "hsl(var(--muted-foreground))" },
    labelBgStyle: { fill: "hsl(var(--background))" },
    labelBgPadding: [6, 3] as [number, number],
    labelBgBorderRadius: 4,
  };
}

/**
 * Lays a hierarchy of {@link TreeNode}s out as a tree: children fan out along the cross axis in
 * array order, each parent centred over its children, levels stacked along the main axis.
 * Top-down by default; `direction: "horizontal"` reads left-to-right. Deterministic (no physics).
 *
 * A leaf occupies one slot; the tree's width is its number of leaves, so a flat (childless) root
 * sits at the origin with no parent edge. Positions use the {@link layoutColumns} convention
 * (top-left, `NODE_WIDTH`/`NODE_HEIGHT` boxes).
 */
export function layoutTree(
  roots: TreeNode[],
  options: { direction?: "horizontal" | "vertical" } = {},
): { nodes: FlowNode[]; edges: FlowEdge[] } {
  const vertical = options.direction !== "horizontal";
  const crossSize = vertical ? NODE_WIDTH : NODE_HEIGHT;
  const alongSize = vertical ? NODE_HEIGHT : NODE_WIDTH;

  const nodes: FlowNode[] = [];
  const edges: FlowEdge[] = [];
  const slot = { value: 0 };

  // Returns the centre of `n`'s subtree along the cross axis, placing nodes as it goes.
  const place = (n: TreeNode, depth: number, hasParent: boolean): number => {
    const kids = n.children ?? [];
    const isLeaf = kids.length === 0;
    let centre: number;
    if (isLeaf) {
      const cross = slot.value * (crossSize + TREE_SIBLING_GAP);
      slot.value += 1;
      centre = cross + crossSize / 2;
    } else {
      const childCentres = kids.map((c) => place(c, depth + 1, true));
      centre = (childCentres[0] + childCentres[childCentres.length - 1]) / 2;
      for (const c of kids) edges.push(childEdge(n.id, c.id, c.data.tone));
    }
    const along = depth * (alongSize + TREE_LEVEL_GAP);
    const cross = centre - crossSize / 2;
    nodes.push({
      id: n.id,
      type: "step" as const,
      position: vertical ? { x: cross, y: along } : { x: along, y: cross },
      data: { ...n.data, hasInput: hasParent, hasOutput: !isLeaf, vertical },
      draggable: true,
      connectable: false,
    });
    return centre;
  };

  for (const root of roots) {
    place(root, 0, false);
    // Leave one empty slot between sibling roots so their subtrees don't run together.
    slot.value += 1;
  }

  return { nodes, edges };
}

/**
 * Lays a flat parent-link list out as a dendrogram: leaves sit in a row along the x-axis in input
 * order, each internal node is centred between its children and raised above the leaf row by its
 * normalized `height` (root highest). Edges are straight lines from child to parent. Deterministic.
 */
export function layoutDendrogram(
  nodes: DendrogramInputNode[],
  options: DendrogramOptions = {},
): { nodes: FlowNode[]; edges: FlowEdge[] } {
  const leafGap = options.leafGap ?? DENDRO_LEAF_GAP;
  const heightBand = options.heightBand ?? DENDRO_HEIGHT_BAND;
  const icon = options.icon ?? Box;
  const tone = options.tone ?? "neutral";

  const leaves = nodes.filter((n) => n.isLeaf);
  const internals = nodes.filter((n) => !n.isLeaf);

  // Centre of each node along the x-axis. Leaves get consecutive slots in input order.
  const centreX = new Map<string, number>();
  leaves.forEach((leaf, i) => centreX.set(leaf.id, i * (NODE_WIDTH + leafGap) + NODE_WIDTH / 2));

  // Normalize internal heights into [0, 1] so taller merges rise further above the leaf row.
  const heights = internals.map((n) => n.height);
  const minH = heights.length ? Math.min(...heights) : 0;
  const maxH = heights.length ? Math.max(...heights) : 0;
  const norm = (h: number) => (maxH === minH ? 0.5 : (h - minH) / (maxH - minH));

  const children = new Map<string, DendrogramInputNode[]>();
  for (const n of nodes) {
    if (n.parentId == null) continue;
    const kids = children.get(n.parentId);
    if (kids) kids.push(n);
    else children.set(n.parentId, [n]);
  }

  // Bottom-up (ascending height) so an internal node's children are placed before it.
  const byHeight = [...internals].sort((a, b) => a.height - b.height);
  for (const node of byHeight) {
    const centres = (children.get(node.id) ?? []).map((k) => centreX.get(k.id)!);
    if (centres.length === 0) {
      centreX.set(node.id, 0);
      continue;
    }
    centreX.set(node.id, (Math.min(...centres) + Math.max(...centres)) / 2);
  }

  const flowNodes: FlowNode[] = nodes.map((n) => {
    const x = (centreX.get(n.id) ?? 0) - NODE_WIDTH / 2;
    // Leaves sit at y=0..NODE_HEIGHT; the base offset clears that row entirely so the lowest
    // merge (norm=0) doesn't dip into the leaf row.
    const y = n.isLeaf ? 0 : -(NODE_HEIGHT + TREE_LEVEL_GAP + norm(n.height) * heightBand);
    const hasChildren = (children.get(n.id)?.length ?? 0) > 0;
    return {
      id: n.id,
      type: "step" as const,
      position: { x, y },
      data: {
        title: n.label,
        icon,
        tone,
        hasInput: n.parentId != null,
        hasOutput: hasChildren,
        vertical: true,
      },
      draggable: true,
      connectable: false,
    };
  });

  const flowEdges: FlowEdge[] = nodes
    .filter((n) => n.parentId != null)
    .map((n) => childEdge(n.parentId!, n.id, tone));

  return { nodes: flowNodes, edges: flowEdges };
}
