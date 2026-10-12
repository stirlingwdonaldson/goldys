"use client";

import Link from "next/link";
import { useMemo, useState, type ReactNode } from "react";
import { Search, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useIsMobile } from "@/components/hooks/use-mobile";
import { cn } from "@/lib/utils";
import { filterKinds, neighbourhood, neighboursOf } from "./graph-ops";
import { NetworkCanvas } from "./network-canvas";
import { NetworkList, TONE_TEXT } from "./network-list";
import type { NetworkGraph, NetworkKind, NetworkNode } from "./types";

interface NetworkExplorerProps {
  graph: NetworkGraph;
  graphKey: string;
  ariaLabel: string;
  /** The kinds this graph uses, in legend order. */
  kinds: { kind: NetworkKind; label: string }[];
  /** Kinds hidden until the viewer turns them on. */
  hiddenByDefault?: NetworkKind[];
  colourFor: (node: NetworkNode) => string;
  initialFocusId?: string;
  /** Start in local-graph mode (e.g. the provenance sheet's "Related" tab). */
  initialLocal?: boolean;
  /** Extra content for the focus panel, e.g. an impact summary or "pairs best with". */
  focusPanelExtra?: (node: NetworkNode, graph: NetworkGraph) => ReactNode;
  /** Ids to keep lit on the canvas while a node is focused, beyond its direct neighbours. */
  highlightFor?: (node: NetworkNode) => string[];
  height?: number;
}

/**
 * A network graph with Obsidian's controls: kind filters, search-to-focus, a local graph with a
 * depth slider, and a focus panel listing the focused node's neighbours. Phones get the list only.
 */
export function NetworkExplorer({
  graph,
  graphKey,
  ariaLabel,
  kinds,
  hiddenByDefault = [],
  colourFor,
  initialFocusId,
  initialLocal = false,
  focusPanelExtra,
  highlightFor,
  height,
}: NetworkExplorerProps) {
  const isMobile = useIsMobile();
  const [visible, setVisible] = useState<Set<NetworkKind>>(
    () => new Set(kinds.map((k) => k.kind).filter((k) => !hiddenByDefault.includes(k))),
  );
  const [focusId, setFocusId] = useState<string | undefined>(initialFocusId);
  const [local, setLocal] = useState(initialLocal);
  const [depth, setDepth] = useState(1);
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => filterKinds(graph, visible), [graph, visible]);
  const focused = filtered.nodes.find((n) => n.id === focusId);
  const shown = useMemo(
    () => (local && focused ? neighbourhood(filtered, focused.id, depth) : filtered),
    [local, focused, filtered, depth],
  );
  const highlightIds = useMemo(
    () => (focused && highlightFor ? new Set(highlightFor(focused)) : undefined),
    [focused, highlightFor],
  );

  const toggleKind = (kind: NetworkKind) =>
    setVisible((current) => {
      const next = new Set(current);
      if (next.has(kind)) next.delete(kind);
      else next.add(kind);
      return next;
    });

  const search = () => {
    const needle = query.trim().toLowerCase();
    if (!needle) return;
    const match = filtered.nodes.find((n) => n.label.toLowerCase().includes(needle));
    if (match) setFocusId(match.id);
  };

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <form
          role="search"
          className="relative w-full sm:w-56"
          onSubmit={(event) => {
            event.preventDefault();
            search();
          }}
        >
          <Search className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted-foreground" aria-hidden="true" />
          <Input
            aria-label="Find a node"
            placeholder="Find…"
            className="pl-8"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </form>
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Show kinds">
          {kinds.map(({ kind, label }) => (
            <Button
              key={kind}
              type="button"
              size="sm"
              variant={visible.has(kind) ? "secondary" : "outline"}
              aria-pressed={visible.has(kind)}
              onClick={() => toggleKind(kind)}
              className={cn("h-7 gap-1.5 px-2 text-xs", !visible.has(kind) && "text-muted-foreground")}
            >
              <span
                aria-hidden="true"
                className="size-2 rounded-full"
                style={{ background: colourFor({ id: kind, kind, label, tone: "neutral", size: 1, group: kind }) }}
              />
              {label}
            </Button>
          ))}
        </div>
        <label className="ml-auto flex items-center gap-2 text-xs text-muted-foreground">
          <input
            type="checkbox"
            checked={local}
            onChange={(event) => setLocal(event.target.checked)}
            className="accent-foreground"
          />
          Local graph
        </label>
        {local && (
          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            Depth
            <input
              type="range"
              min={1}
              max={3}
              value={depth}
              aria-label="Local graph depth"
              onChange={(event) => setDepth(Number(event.target.value))}
              className="w-20 accent-foreground"
            />
            <span className="tabular-nums">{depth}</span>
          </label>
        )}
      </div>

      {local && !focused && (
        <p className="text-xs text-muted-foreground">Select a node to see its local graph.</p>
      )}

      <div className={cn("grid gap-3", !isMobile && "lg:grid-cols-[minmax(0,1fr)_18rem]")}>
        {!isMobile && (
          <NetworkCanvas
            graph={shown}
            graphKey={local && focused ? `${graphKey}:local:${focused.id}:${depth}` : graphKey}
            ariaLabel={ariaLabel}
            height={height}
            focusId={focused?.id}
            onFocus={setFocusId}
            colourFor={colourFor}
            highlightIds={highlightIds}
          />
        )}
        <Card className="flex max-h-[30rem] flex-col gap-3 overflow-y-auto p-3">
          {focused ? (
            <FocusPanel
              node={focused}
              graph={shown}
              onFocus={setFocusId}
              onClear={() => setFocusId(undefined)}
              extra={focusPanelExtra?.(focused, filtered)}
            />
          ) : (
            <NetworkList graph={shown} kinds={kinds} focusId={focusId} onFocus={setFocusId} />
          )}
        </Card>
      </div>
    </div>
  );
}

function FocusPanel({
  node,
  graph,
  onFocus,
  onClear,
  extra,
}: {
  node: NetworkNode;
  graph: NetworkGraph;
  onFocus: (id: string) => void;
  onClear: () => void;
  extra?: ReactNode;
}) {
  const neighbours = neighboursOf(graph, node.id);
  const toneText = TONE_TEXT[node.tone];
  return (
    <div className="flex flex-col gap-3" aria-live="polite">
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{node.label}</p>
          {node.subtitle && <p className="text-xs text-muted-foreground">{node.subtitle}</p>}
          {toneText && <p className="text-xs text-muted-foreground">{toneText}</p>}
        </div>
        <Button type="button" size="icon" variant="ghost" className="size-7" onClick={onClear} aria-label="Clear focus">
          <X className="size-4" />
        </Button>
      </div>
      {node.href && (
        <Link href={node.href} className="text-xs font-medium underline underline-offset-2">
          Open
        </Link>
      )}
      {extra}
      <div>
        <h3 className="mb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
          Connected ({neighbours.length})
        </h3>
        <ul className="flex flex-col">
          {neighbours.map(({ node: other, edge }) => (
            <li key={other.id}>
              <button
                type="button"
                onClick={() => onFocus(other.id)}
                className="flex w-full items-center gap-2 rounded-md px-1.5 py-1 text-left text-sm hover:bg-muted"
                title={edge.label}
              >
                <span className="min-w-0 flex-1 truncate">{other.label}</span>
                {edge.label && <span className="max-w-[45%] shrink-0 truncate text-xs text-muted-foreground">{edge.label}</span>}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
