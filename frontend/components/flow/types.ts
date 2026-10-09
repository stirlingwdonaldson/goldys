import type { Edge, Node } from "@xyflow/react";
import type { LucideIcon } from "lucide-react";

/**
 * How a node reads at a glance. Mirrors the design system's status vocabulary:
 * `ok` → status-success, `warn` → status-conflict (expected, resolvable), `fail` →
 * destructive (a genuine system fault), `missing` → status-missing ("no data"),
 * `info` → status-info (resolved by a rule or a person), `neutral` → no status.
 */
export type FlowTone = "ok" | "warn" | "fail" | "missing" | "info" | "neutral";

export interface StepNodeData extends Record<string, unknown> {
  title: string;
  /** One short line under the title — usually a count or value. */
  subtitle?: string;
  /** A second, quieter line — e.g. a last-run time or a failure message. */
  detail?: string;
  icon: LucideIcon;
  tone: FlowTone;
  /** Where clicking the node goes. Omit for a non-navigable node. */
  href?: string;
  /** An opaque id the consumer handles via `FlowCanvas` `onDrill`. Takes precedence over `href`. */
  drill?: string;
  /** Hide the incoming/outgoing handle for nodes at the start/end of a graph. */
  hasInput?: boolean;
  hasOutput?: boolean;
  /** Emphasise this node (e.g. the resolved value a provenance graph explains). */
  emphasis?: boolean;
}

export type StepNode = Node<StepNodeData, "step">;
export type FlowEdge = Edge<{ tone?: FlowTone }>;

/** Logical graph before layout: nodes grouped into left-to-right columns. */
export interface ColumnGraph {
  columns: { id: string; data: StepNodeData }[][];
  edges: { source: string; target: string; label?: string; tone?: FlowTone }[];
}
