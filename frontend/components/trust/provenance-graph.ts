import { BadgeCheck, GitMerge, Plug } from "lucide-react";
import type { Provenance, TrustState } from "@/lib/api/types";
import type { ColumnGraph, FlowTone } from "@/components/flow/types";
import { sourceLabel } from "@/lib/rule-logic";
import { TRUST_LABEL } from "./trust-indicator";

/**
 * How each backend resolution kind (`TrustService.resolutionDetail`) reads to an operator.
 * A conflict is `warn`, not `fail`: per the design system it's an expected, resolvable state.
 */
const RESOLUTION: Record<string, { title: string; tone: FlowTone }> = {
  agreed: { title: "Sources agree", tone: "ok" },
  rule: { title: "Standing rule", tone: "info" },
  override: { title: "Manual override", tone: "info" },
  conflict: { title: "Unresolved conflict", tone: "warn" },
  single: { title: "Single source", tone: "warn" },
};

const TRUST_TONE: Record<TrustState, FlowTone> = {
  VERIFIED: "ok",
  RESOLVED_BY_RULE: "info",
  MANUALLY_OVERRIDDEN: "info",
  SINGLE_SOURCE: "warn",
  CONFLICTED: "warn",
  INCOMPLETE: "warn",
  NOT_RECEIVED: "missing",
};

/** A rule-kind resolution's `reason` is the rule's strategy id; say what it means instead. */
const STRATEGY_LABEL: Record<string, string> = {
  priority: "Source priority",
  manual: "Manual decision",
  custom: "Custom logic",
};

/** Formats a metric value for display; sales metrics are money, everything else a plain number. */
export function formatMetricValue(metricId: string, value: number | null): string {
  if (value == null) return "No value";
  if (metricId.startsWith("sales.") || metricId.endsWith(".cost")) {
    return value.toLocaleString("en-AU", { style: "currency", currency: "AUD" });
  }
  return value.toLocaleString();
}

/**
 * One resolved value's lineage as a graph: each source's reported value → how it was resolved →
 * the value the screens show. The source the resolution chose is drawn in the resolution's colour;
 * a source with no value is dashed. Raw records are not drawn as nodes because the API doesn't
 * say which source each raw record came from — they're listed separately as evidence.
 */
export function buildProvenanceGraph(p: Provenance, metricLabel: string): ColumnGraph {
  const resolution = (p.resolution.kind && RESOLUTION[p.resolution.kind]) || {
    title: "Not resolved",
    tone: "missing" as FlowTone,
  };
  const chosen = p.resolution.source ?? p.trust.authoritativeSource;

  const sources = p.sources.map((s) => {
    const isChosen = chosen != null && s.sourceSystem === chosen;
    const differs = s.value != null && p.resolvedValue != null && s.value !== p.resolvedValue;
    const tone: FlowTone = s.value == null ? "missing" : differs ? "warn" : "neutral";
    return {
      id: `src:${s.sourceSystem}`,
      isChosen,
      data: {
        title: sourceLabel(s.sourceSystem),
        subtitle: s.value == null ? "No data" : formatMetricValue(p.metric, s.value),
        detail: isChosen ? "Chosen source" : differs ? "Disagrees with result" : undefined,
        icon: Plug,
        tone,
      },
    };
  });

  const detailParts = [p.resolution.actor, p.resolution.at ? new Date(p.resolution.at).toLocaleString() : null];
  const resolve = {
    id: "resolve",
    data: {
      title: resolution.title,
      subtitle:
        (p.resolution.kind === "rule" && p.resolution.reason && STRATEGY_LABEL[p.resolution.reason]) ||
        (p.resolution.reason ?? undefined),
      detail: detailParts.filter(Boolean).join(" · ") || undefined,
      icon: GitMerge,
      tone: resolution.tone,
      href: p.resolution.kind === "rule" ? "/resolution-rules" : p.resolution.kind === "conflict" ? "/reconciliation" : undefined,
    },
  };

  const result = {
    id: "result",
    data: {
      title: metricLabel,
      subtitle: formatMetricValue(p.metric, p.resolvedValue),
      detail: TRUST_LABEL[p.trust.state],
      icon: BadgeCheck,
      tone: TRUST_TONE[p.trust.state],
      emphasis: true,
    },
  };

  const edges: ColumnGraph["edges"] = sources.map((s) => ({
    source: s.id,
    target: "resolve",
    tone: s.data.tone === "missing" ? "missing" : s.isChosen ? resolution.tone : "neutral",
  }));
  edges.push({ source: "resolve", target: "result", tone: resolution.tone });

  return {
    columns: [sources.map(({ id, data }) => ({ id, data })), [resolve], [result]],
    edges,
  };
}
