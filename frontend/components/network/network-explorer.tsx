"use client";

import Link from "next/link";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useIsMobile } from "@/components/hooks/use-mobile";
import { cn } from "@/lib/utils";
import { filterKinds, neighbourhood, neighboursOf } from "./graph-ops";
import {
  GraphSettingsPanel,
  SettingsSearch,
  SettingsSection,
  SettingsSlider,
  SettingsSwitch,
} from "./graph-settings-panel";
import { DEFAULT_SETTINGS, readSettings, writeSettings, type GraphSettings } from "./graph-settings";
import { NetworkCanvas } from "./network-canvas";
import { NetworkList, TONE_TEXT } from "./network-list";
import type { NetworkGraph, NetworkGroupSet, NetworkKind, NetworkNode } from "./types";
import { useCssColours } from "./use-css-colours";

interface NetworkExplorerProps {
  graph: NetworkGraph;
  graphKey: string;
  ariaLabel: string;
  /** The kinds this graph uses, in filter order. */
  kinds: { kind: NetworkKind; label: string }[];
  /** Kinds hidden until the viewer turns them on. */
  hiddenByDefault?: NetworkKind[];
  /** Ways to colour the graph; the first is the default. Unmatched nodes stay grey. */
  groupSets: NetworkGroupSet[];
  initialFocusId?: string;
  /** Start in local-graph mode (e.g. the provenance sheet's "Related" tab). */
  initialLocal?: boolean;
  /** Extra content for the focus card, e.g. an impact summary or "pairs best with". */
  focusPanelExtra?: (node: NetworkNode, graph: NetworkGraph) => ReactNode;
  /** Ids to keep lit while a node is focused, beyond its direct neighbours. */
  highlightFor?: (node: NetworkNode) => string[];
  height?: number;
  /** Start with the settings card open (on a full page) or collapsed (in a sheet). */
  defaultSettingsOpen?: boolean;
}

/**
 * An Obsidian-style graph view: the canvas is the whole surface, settings float in a collapsible
 * card (filters, colour groups, display, forces) and the focused node's details float bottom-left.
 * The canvas is hidden from screen readers, so the same nodes are always in the DOM as a list,
 * which is also the only view on a phone.
 */
export function NetworkExplorer({
  graph,
  graphKey,
  ariaLabel,
  kinds,
  hiddenByDefault = [],
  groupSets,
  initialFocusId,
  initialLocal = false,
  focusPanelExtra,
  highlightFor,
  height = 560,
  defaultSettingsOpen = true,
}: NetworkExplorerProps) {
  const isMobile = useIsMobile();
  const [visible, setVisible] = useState<Set<NetworkKind>>(
    () => new Set(kinds.map((k) => k.kind).filter((k) => !hiddenByDefault.includes(k))),
  );
  const [showOrphans, setShowOrphans] = useState(true);
  const [focusId, setFocusId] = useState<string | undefined>(initialFocusId);
  const [local, setLocal] = useState(initialLocal);
  const [depth, setDepth] = useState(1);
  const [query, setQuery] = useState("");
  const [groupSetId, setGroupSetId] = useState(groupSets[0]?.id);
  const [settingsOpen, setSettingsOpen] = useState(defaultSettingsOpen);
  const [settings, setSettings] = useState<GraphSettings>(DEFAULT_SETTINGS);
  const [animateTick, setAnimateTick] = useState(0);

  // Saved settings load after mount so the server render and first client render agree.
  useEffect(() => setSettings(readSettings(graphKey)), [graphKey]);
  const updateSettings = (next: GraphSettings) => {
    setSettings(next);
    writeSettings(graphKey, next);
  };

  const groupSet = groupSets.find((s) => s.id === groupSetId) ?? groupSets[0];
  const colourVars = useMemo(
    () => Object.fromEntries(groupSets.flatMap((s) => s.groups.map((g) => [g.colourVar, g.colourVar]))),
    [groupSets],
  );
  const colours = useCssColours(colourVars);
  const colourFor = (node: NetworkNode) => {
    const group = groupSet?.groups.find((g) => g.matches(node));
    return group ? colours[group.colourVar] : undefined;
  };

  const filtered = useMemo(() => {
    const byKind = filterKinds(graph, visible);
    if (showOrphans) return byKind;
    const linked = new Set(byKind.edges.flatMap((e) => [e.source, e.target]));
    return { nodes: byKind.nodes.filter((n) => linked.has(n.id)), edges: byKind.edges };
  }, [graph, visible, showOrphans]);

  const focused = filtered.nodes.find((n) => n.id === focusId);
  const shown = useMemo(
    () => (local && focused ? neighbourhood(filtered, focused.id, depth) : filtered),
    [local, focused, filtered, depth],
  );
  const highlightIds = useMemo(
    () => (focused && highlightFor ? new Set(highlightFor(focused)) : undefined),
    [focused, highlightFor],
  );
  const matches = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return undefined;
    return new Set(shown.nodes.filter((n) => n.label.toLowerCase().includes(needle)).map((n) => n.id));
  }, [query, shown]);

  const toggleKind = (kind: NetworkKind, on: boolean) =>
    setVisible((current) => {
      const next = new Set(current);
      if (on) next.add(kind);
      else next.delete(kind);
      return next;
    });

  const focusFirstMatch = () => {
    const first = shown.nodes.find((n) => matches?.has(n.id));
    if (first) setFocusId(first.id);
  };

  const counts = (set: NetworkGroupSet) =>
    set.groups.map((g) => ({ group: g, count: shown.nodes.filter((n) => set.groups.find((x) => x.matches(n)) === g).length }));

  const focusCard = focused ? (
    <FocusCard
      node={focused}
      graph={shown}
      onFocus={setFocusId}
      onClear={() => setFocusId(undefined)}
      extra={focusPanelExtra?.(focused, filtered)}
      floating={!isMobile}
    />
  ) : null;

  const list = <NetworkList graph={shown} kinds={kinds} focusId={focusId} onFocus={setFocusId} />;

  if (isMobile) {
    return (
      <div className="flex flex-col gap-3">
        <SettingsSearch value={query} onChange={setQuery} onSubmit={focusFirstMatch} />
        {focusCard}
        {list}
      </div>
    );
  }

  return (
    <div className="relative overflow-hidden rounded-lg border bg-card" style={{ height }}>
      <NetworkCanvas
        graph={shown}
        graphKey={local && focused ? `${graphKey}:local:${focused.id}:${depth}` : graphKey}
        ariaLabel={ariaLabel}
        height={height}
        focusId={focused?.id}
        onFocus={setFocusId}
        colourFor={colourFor}
        highlightIds={highlightIds}
        emphasisIds={matches}
        display={settings.display}
        forces={settings.forces}
        animateTick={animateTick}
      />

      <GraphSettingsPanel
        open={settingsOpen}
        onOpenChange={setSettingsOpen}
        onReset={() => {
          updateSettings(DEFAULT_SETTINGS);
          setVisible(new Set(kinds.map((k) => k.kind).filter((k) => !hiddenByDefault.includes(k))));
          setShowOrphans(true);
          setLocal(false);
          setDepth(1);
          setQuery("");
        }}
      >
        <SettingsSection title="Filters" defaultOpen>
          <SettingsSearch value={query} onChange={setQuery} onSubmit={focusFirstMatch} />
          {kinds.map(({ kind, label }) => (
            <SettingsSwitch
              key={kind}
              label={label}
              checked={visible.has(kind)}
              onCheckedChange={(on) => toggleKind(kind, on)}
            />
          ))}
          <SettingsSwitch label="Orphans" checked={showOrphans} onCheckedChange={setShowOrphans} />
          <SettingsSwitch
            label="Local graph"
            checked={local}
            onCheckedChange={setLocal}
          />
          {local && (
            <SettingsSlider
              label="Depth"
              value={depth}
              min={1}
              max={3}
              step={1}
              onChange={setDepth}
              format={(v) => String(v)}
            />
          )}
          {local && !focused && (
            <p className="text-xs text-muted-foreground">Select a node to see its local graph.</p>
          )}
        </SettingsSection>

        {groupSet && (
          <SettingsSection title="Groups" defaultOpen>
            {groupSets.length > 1 && (
              <div role="radiogroup" aria-label="Colour by" className="flex rounded-md border p-0.5 text-xs">
                {groupSets.map((set) => (
                  <button
                    key={set.id}
                    type="button"
                    role="radio"
                    aria-checked={set.id === groupSet.id}
                    onClick={() => setGroupSetId(set.id)}
                    className={cn(
                      "flex-1 rounded px-2 py-1",
                      set.id === groupSet.id ? "bg-muted font-medium" : "text-muted-foreground hover:text-foreground",
                    )}
                  >
                    {set.label}
                  </button>
                ))}
              </div>
            )}
            <ul className="flex flex-col gap-1.5">
              {counts(groupSet).map(({ group, count }) => (
                <li key={group.id} className="flex items-center gap-2 text-[13px]">
                  <span
                    aria-hidden="true"
                    className="size-2.5 shrink-0 rounded-full"
                    style={{ background: colours[group.colourVar] ?? `hsl(var(${group.colourVar}))` }}
                  />
                  <span className="flex-1">{group.label}</span>
                  <span className="text-xs tabular-nums text-muted-foreground">{count}</span>
                </li>
              ))}
            </ul>
          </SettingsSection>
        )}

        <SettingsSection title="Display">
          <SettingsSlider
            label="Text fade threshold"
            value={settings.display.textFade}
            min={-1}
            max={1}
            step={0.05}
            onChange={(textFade) => updateSettings({ ...settings, display: { ...settings.display, textFade } })}
          />
          <SettingsSlider
            label="Node size"
            value={settings.display.nodeSize}
            min={0.5}
            max={3}
            step={0.05}
            onChange={(nodeSize) => updateSettings({ ...settings, display: { ...settings.display, nodeSize } })}
          />
          <SettingsSlider
            label="Link thickness"
            value={settings.display.linkThickness}
            min={0.25}
            max={3}
            step={0.05}
            onChange={(linkThickness) =>
              updateSettings({ ...settings, display: { ...settings.display, linkThickness } })
            }
          />
          <Button type="button" variant="outline" size="sm" className="self-end" onClick={() => setAnimateTick((t) => t + 1)}>
            Animate
          </Button>
        </SettingsSection>

        <SettingsSection title="Forces">
          <SettingsSlider
            label="Center force"
            value={settings.forces.center}
            min={0}
            max={1}
            step={0.01}
            onChange={(center) => updateSettings({ ...settings, forces: { ...settings.forces, center } })}
          />
          <SettingsSlider
            label="Repel force"
            value={settings.forces.repel}
            min={0}
            max={20}
            step={0.5}
            format={(v) => v.toFixed(1)}
            onChange={(repel) => updateSettings({ ...settings, forces: { ...settings.forces, repel } })}
          />
          <SettingsSlider
            label="Link force"
            value={settings.forces.link}
            min={0}
            max={1}
            step={0.01}
            onChange={(link) => updateSettings({ ...settings, forces: { ...settings.forces, link } })}
          />
        </SettingsSection>
      </GraphSettingsPanel>

      {focusCard}

      <div className="sr-only">{list}</div>
    </div>
  );
}

function FocusCard({
  node,
  graph,
  onFocus,
  onClear,
  extra,
  floating,
}: {
  node: NetworkNode;
  graph: NetworkGraph;
  onFocus: (id: string) => void;
  onClear: () => void;
  extra?: ReactNode;
  floating: boolean;
}) {
  const neighbours = neighboursOf(graph, node.id);
  const toneText = TONE_TEXT[node.tone];
  return (
    <section
      aria-label={`${node.label} details`}
      aria-live="polite"
      className={cn(
        "flex flex-col gap-3 rounded-lg border bg-background/95 p-3 text-sm shadow-sm backdrop-blur",
        floating && "absolute bottom-3 left-3 z-10 max-h-[calc(100%-1.5rem)] w-72 overflow-y-auto",
      )}
    >
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">{node.label}</p>
          {node.subtitle && <p className="text-xs text-muted-foreground">{node.subtitle}</p>}
          {toneText && <p className="text-xs text-muted-foreground">{toneText}</p>}
        </div>
        {node.href && (
          <Link href={node.href} className="pt-0.5 text-xs font-medium underline underline-offset-2">
            Open
          </Link>
        )}
        <button
          type="button"
          onClick={onClear}
          aria-label="Clear focus"
          className="flex size-6 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
        >
          <X className="size-3.5" />
        </button>
      </div>
      {extra}
      <div>
        <h3 className="mb-1 text-xs font-medium text-muted-foreground">Connected ({neighbours.length})</h3>
        <ul className="flex flex-col">
          {neighbours.map(({ node: other, edge }) => (
            <li key={other.id}>
              <button
                type="button"
                onClick={() => onFocus(other.id)}
                className="flex w-full items-center gap-2 rounded px-1.5 py-1 text-left text-[13px] hover:bg-muted"
                title={edge.label}
              >
                <span className="min-w-0 flex-1 truncate">{other.label}</span>
                {edge.label && <span className="max-w-[45%] shrink-0 truncate text-xs text-muted-foreground">{edge.label}</span>}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
