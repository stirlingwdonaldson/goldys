import { MarkerType } from "@xyflow/react";
import type { ColumnGraph, FlowEdge, FlowTone, StepNode } from "./types";

export const NODE_WIDTH = 216;
export const NODE_HEIGHT = 64;
const COLUMN_GAP = 120;
const ROW_GAP = 20;

/** Edge stroke per tone. `neutral`/`ok` stay quiet grey so only problems draw the eye. */
const EDGE_STROKE: Record<FlowTone, string> = {
  ok: "hsl(var(--muted-foreground) / 0.45)",
  neutral: "hsl(var(--muted-foreground) / 0.45)",
  info: "hsl(var(--status-info))",
  warn: "hsl(var(--status-warning))",
  fail: "hsl(var(--destructive))",
  missing: "hsl(var(--status-missing))",
};

/**
 * Lays a {@link ColumnGraph} out left-to-right, n8n style: each column is a stage, nodes in a
 * column stack vertically and are centred against the tallest column so fan-in/fan-out edges
 * stay symmetric. Deterministic (no physics), so the same data always draws the same picture.
 */
export function layoutColumns(graph: ColumnGraph): { nodes: StepNode[]; edges: FlowEdge[] } {
  const tallest = Math.max(1, ...graph.columns.map((c) => c.length));
  const fullHeight = tallest * NODE_HEIGHT + (tallest - 1) * ROW_GAP;
  const lastColumn = graph.columns.length - 1;

  const nodes: StepNode[] = graph.columns.flatMap((column, col) => {
    const height = column.length * NODE_HEIGHT + Math.max(0, column.length - 1) * ROW_GAP;
    const top = (fullHeight - height) / 2;
    return column.map((n, row) => ({
      id: n.id,
      type: "step" as const,
      position: { x: col * (NODE_WIDTH + COLUMN_GAP), y: top + row * (NODE_HEIGHT + ROW_GAP) },
      data: { hasInput: col > 0, hasOutput: col < lastColumn, ...n.data },
      draggable: true,
      connectable: false,
    }));
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
