import { Boxes, CheckCircle2, Database, GitMerge, Plug } from "lucide-react";
import type { ConnectorRunStatus, ConnectorStatus, EntityDescriptor } from "@/lib/api/types";
import { RESOLVED_SCREENS } from "@/lib/data-layers";
import { sourceLabel } from "@/lib/rule-logic";
import type { ColumnGraph, FlowTone } from "@/components/flow/types";

/** A layer descriptor with its row count; `count` is null when the count couldn't be read. */
export interface CountedLayer extends EntityDescriptor {
  count: number | null;
}

export interface PipelineInput {
  connectors: ConnectorStatus[];
  /** Raw-record count per connector `source` (live sources are raw `sourceSystem` codes). */
  rawCountBySource: Record<string, number | null>;
  rawTotal: number | null;
  canonical: CountedLayer[];
  resolved: CountedLayer[];
  /** Standing resolution rules; null when the role can't read them. */
  ruleCount: number | null;
}

const CONNECTOR_TONE: Record<ConnectorRunStatus, FlowTone> = {
  success: "ok",
  no_new_data: "ok",
  running: "info",
  partial: "warn",
  failed: "fail",
  never_run: "missing",
};

const CONNECTOR_LABEL: Record<ConnectorRunStatus, string> = {
  success: "Last run succeeded",
  no_new_data: "No new data",
  running: "Running",
  partial: "Partial run",
  failed: "Last run failed",
  never_run: "Never run",
};

function count(n: number | null, one: string, many: string): string {
  if (n == null) return "Count unavailable";
  return `${n.toLocaleString()} ${n === 1 ? one : many}`;
}

/**
 * The three-layer data model (system-context.md) as a left-to-right graph:
 * sources → raw ledger → canonical entities → resolution → resolved domains.
 *
 * Canonical entities and resolved domains are joined through a single resolution node
 * rather than per-entity edges: the API doesn't expose which canonical entity feeds which
 * resolved domain, and drawing guessed edges would misstate lineage.
 */
export function buildPipelineGraph(input: PipelineInput): ColumnGraph {
  const sources = input.connectors.map((c) => {
    const tone = CONNECTOR_TONE[c.status];
    return {
      id: `src:${c.source}`,
      data: {
        title: sourceLabel(c.source),
        subtitle: CONNECTOR_LABEL[c.status],
        detail: c.failure?.message ?? c.connectorName,
        icon: Plug,
        tone,
        href: "/logs",
      },
    };
  });

  const raw = {
    id: "raw",
    data: {
      title: "Raw ledger",
      subtitle: count(input.rawTotal, "record", "records"),
      detail: "Append-only, never edited",
      icon: Database,
      tone: (input.rawTotal ? "neutral" : "missing") as FlowTone,
      href: "/data?layer=raw",
    },
  };

  const canonical = input.canonical.map((e) => ({
    id: `can:${e.id}`,
    data: {
      title: e.label,
      subtitle: e.placeholder ? "No source yet" : count(e.count, "row", "rows"),
      icon: Boxes,
      tone: (e.placeholder || e.count === 0 ? "missing" : "neutral") as FlowTone,
      href: `/data?layer=canonical&entity=${encodeURIComponent(e.id)}`,
    },
  }));

  const resolution = {
    id: "resolve",
    data: {
      title: "Resolution",
      subtitle:
        input.ruleCount == null
          ? "Agreement, rules, overrides"
          : count(input.ruleCount, "standing rule", "standing rules"),
      icon: GitMerge,
      tone: "info" as FlowTone,
      href: "/resolution-rules",
    },
  };

  const resolved = input.resolved.map((d) => {
    const screen = RESOLVED_SCREENS[d.id];
    return {
      id: `res:${d.id}`,
      data: {
        title: d.label,
        subtitle: d.placeholder ? "No source yet" : count(d.count, "row", "rows"),
        detail: screen ? `Shown on ${screen.label}` : undefined,
        icon: CheckCircle2,
        tone: (d.placeholder || d.count === 0 ? "missing" : "ok") as FlowTone,
        href: screen?.href ?? `/data?layer=resolved&entity=${encodeURIComponent(d.id)}`,
      },
    };
  });

  const edges: ColumnGraph["edges"] = [];
  for (const c of input.connectors) {
    const n = input.rawCountBySource[c.source] ?? null;
    const tone = CONNECTOR_TONE[c.status];
    edges.push({
      source: `src:${c.source}`,
      target: "raw",
      label: n == null ? undefined : count(n, "record", "records"),
      // A source that has never delivered anything draws a dashed "nothing flowing" edge.
      tone: n === 0 ? "missing" : tone === "fail" || tone === "warn" ? tone : "neutral",
    });
  }
  for (const node of canonical) {
    edges.push({
      source: "raw",
      target: node.id,
      tone: node.data.tone === "missing" ? "missing" : "neutral",
    });
    edges.push({
      source: node.id,
      target: "resolve",
      tone: node.data.tone === "missing" ? "missing" : "neutral",
    });
  }
  for (const node of resolved) {
    edges.push({
      source: "resolve",
      target: node.id,
      tone: node.data.tone === "missing" ? "missing" : "neutral",
    });
  }

  return { columns: [sources, [raw], canonical, [resolution], resolved], edges };
}
