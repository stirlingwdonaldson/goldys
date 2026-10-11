"use client";

import type { FlowTone } from "@/components/flow/types";
import { StatusDot } from "@/components/ui/status-dot";
import { cn } from "@/lib/utils";
import type { NetworkGraph, NetworkKind } from "./types";

/** The words a screen reader hears for a tone; `ok`/`neutral` say nothing extra. */
export const TONE_TEXT: Record<FlowTone, string | null> = {
  ok: null,
  neutral: null,
  info: "Resolved",
  warn: "Needs attention",
  fail: "Failing",
  missing: "No data",
};

const DOT: Partial<Record<FlowTone, "success" | "conflict" | "missing" | "failed">> = {
  warn: "conflict",
  fail: "failed",
  missing: "missing",
};

interface NetworkListProps {
  graph: NetworkGraph;
  kinds: { kind: NetworkKind; label: string }[];
  focusId?: string;
  onFocus: (id: string) => void;
}

/**
 * The same graph as a list, grouped by kind. It is the accessible view on every screen and the
 * only view on a phone, where a force layout is unusable.
 */
export function NetworkList({ graph, kinds, focusId, onFocus }: NetworkListProps) {
  const groups = kinds
    .map(({ kind, label }) => ({
      kind,
      label,
      nodes: graph.nodes
        .filter((n) => n.kind === kind)
        .sort((a, b) => b.size - a.size || a.label.localeCompare(b.label)),
    }))
    .filter((g) => g.nodes.length > 0);

  if (groups.length === 0) {
    return <p className="text-sm text-muted-foreground">Nothing to show with these filters.</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      {groups.map((group) => (
        <section key={group.kind} aria-label={group.label}>
          <h3 className="mb-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {group.label} <span className="tabular-nums">({group.nodes.length})</span>
          </h3>
          <ul className="flex flex-col">
            {group.nodes.map((node) => {
              const toneText = TONE_TEXT[node.tone];
              const dot = DOT[node.tone];
              return (
                <li key={node.id}>
                  <button
                    type="button"
                    onClick={() => onFocus(node.id)}
                    aria-current={node.id === focusId ? "true" : undefined}
                    className={cn(
                      "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-muted",
                      node.id === focusId && "bg-muted font-medium",
                    )}
                  >
                    {dot ? <StatusDot tone={dot} halo={false} /> : <span className="size-2 shrink-0" />}
                    <span className="min-w-0 flex-1 truncate">{node.label}</span>
                    {node.subtitle && (
                      <span className="shrink-0 text-xs text-muted-foreground">{node.subtitle}</span>
                    )}
                    {toneText && <span className="sr-only">({toneText})</span>}
                  </button>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}
