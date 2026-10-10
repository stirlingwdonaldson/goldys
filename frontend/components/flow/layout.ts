import { MarkerType } from "@xyflow/react";
import type { ColumnGraph, FlowEdge, FlowTone, StepNode } from "./types";

export const NODE_WIDTH = 216;
export const NODE_HEIGHT = 64;
const COLUMN_GAP = 120;
const ROW_GAP = 20;
/** Vertical layout: gap between side-by-side nodes in a stage, and between stages. */
const VERTICAL_SIDE_GAP = 24;
const VERTICAL_STAGE_GAP = 56;

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
 * Lays a {@link ColumnGraph} out left-to-right, n8n style: each column is a stage, nodes in a
 * column stack vertically and are centred against the tallest column so fan-in/fan-out edges
 * stay symmetric. Deterministic (no physics), so the same data always draws the same picture.
 */
export function layoutColumns(
  graph: ColumnGraph,
  options: { columnGap?: number; direction?: FlowDirection } = {},
): { nodes: StepNode[]; edges: FlowEdge[] } {
  const columnGap = options.columnGap ?? COLUMN_GAP;
  const vertical = options.direction === "vertical";
  const tallest = Math.max(1, ...graph.columns.map((c) => c.length));
  const lastColumn = graph.columns.length - 1;
  // Within a stage, nodes stack along the cross axis: down the page when horizontal,
  // across it when vertical. Each stage is centred against the widest one.
  const crossSize = vertical ? NODE_WIDTH : NODE_HEIGHT;
  const crossGap = vertical ? VERTICAL_SIDE_GAP : ROW_GAP;
  const stageStep = vertical ? NODE_HEIGHT + VERTICAL_STAGE_GAP : NODE_WIDTH + columnGap;
  const fullCross = tallest * crossSize + (tallest - 1) * crossGap;

  const nodes: StepNode[] = graph.columns.flatMap((column, col) => {
    const extent = column.length * crossSize + Math.max(0, column.length - 1) * crossGap;
    const start = (fullCross - extent) / 2;
    return column.map((n, row) => {
      const along = col * stageStep;
      const across = start + row * (crossSize + crossGap);
      return {
        id: n.id,
        type: "step" as const,
        position: vertical ? { x: across, y: along } : { x: along, y: across },
        data: { hasInput: col > 0, hasOutput: col < lastColumn, vertical, ...n.data },
        draggable: true,
        connectable: false,
      };
    });
  });

  const edges: FlowEdge[] = graph.edges.map((e) => {
    const tone = e.tone ?? "neutral";
    const stroke = EDGE_STROKE[tone];
    return {
      id: `${e.source}->${e.target}`,
      source: e.source,
      target: e.target,
      label: e.label,
      data: { tone },
      style: {
        stroke,
        strokeWidth: tone === "neutral" || tone === "ok" ? 1.5 : 2,
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
