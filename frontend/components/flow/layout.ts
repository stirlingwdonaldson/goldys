import { MarkerType } from "@xyflow/react";
import type { ColumnGraph, FlowEdge, FlowNode, FlowTone } from "./types";

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
