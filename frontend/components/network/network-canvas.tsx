"use client";

import dynamic from "next/dynamic";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type ForceGraphComponent from "react-force-graph-2d";
import type { ForceGraphMethods, LinkObject, NodeObject } from "react-force-graph-2d";
import type { FlowTone } from "@/components/flow/types";
import { d3Forces, labelAlpha, nodeRadius, type DisplaySettings, type ForceSettings } from "./graph-settings";
import { initialPositions, readPositions, writePositions, type Positions } from "./layout-cache";
import type { NetworkGraph, NetworkNode } from "./types";

// Canvas + window access: never render on the server. `next/dynamic` drops the component's
// generic node/link types, so restore its own signature.
const ForceGraph2D = dynamic(() => import("react-force-graph-2d"), {
  ssr: false,
}) as unknown as typeof ForceGraphComponent;

type SimNode = NodeObject<NetworkNode>;
type LinkData = { weight: number; label?: string; tone?: FlowTone };
type SimLink = LinkObject<NetworkNode, LinkData>;
type Graph = ForceGraphMethods<SimNode, SimLink>;

/** Design tokens the canvas paints with, resolved at mount (canvas can't read `var(...)`). */
const TOKENS = {
  foreground: "--foreground",
  muted: "--muted-foreground",
  accent: "--chart-3",
} as const;
type Palette = Record<keyof typeof TOKENS, string> & { font: string };

function readPalette(): Palette {
  const style = getComputedStyle(document.documentElement);
  const colours = Object.fromEntries(
    Object.entries(TOKENS).map(([key, variable]) => {
      const value = style.getPropertyValue(variable).trim();
      return [key, value ? `hsl(${value})` : "#888"];
    }),
  ) as Record<keyof typeof TOKENS, string>;
  // A canvas `font` can't use var(...); an invalid value is silently ignored, so resolve it here.
  const font = getComputedStyle(document.body).fontFamily || "sans-serif";
  return { ...colours, font };
}

/** `hsl(h s% l%)` → `hsl(h s% l% / a)`; other colour strings pass through unchanged. */
function withAlpha(colour: string, alpha: number): string {
  return colour.startsWith("hsl(") && !colour.includes("/")
    ? colour.replace(/\)$/, ` / ${alpha})`)
    : colour;
}

/** Largest zoom a fit may reach, so a small graph doesn't fill the canvas with giant dots. */
const MAX_FIT_ZOOM = 2;

/** Zoom to show every node, but never past {@link MAX_FIT_ZOOM} (`zoomToFit` ignores `maxZoom`). */
function fit(graph: Graph | undefined): void {
  if (!graph) return;
  graph.zoomToFit(400, 48);
  window.setTimeout(() => {
    if (graph.zoom() > MAX_FIT_ZOOM) graph.zoom(MAX_FIT_ZOOM, 200);
  }, 450);
}

const endpointId = (end: SimLink["source"]) =>
  typeof end === "object" && end !== null ? String(end.id) : String(end);

export interface NetworkCanvasProps {
  graph: NetworkGraph;
  /** Names the cached layout, e.g. "platform-map". */
  graphKey: string;
  ariaLabel: string;
  height: number;
  focusId?: string;
  onFocus?: (id: string | undefined) => void;
  /** Fill colour from the active colour group, or undefined for the default grey. */
  colourFor: (node: NetworkNode) => string | undefined;
  /** Extra nodes kept lit while a node is focused, e.g. everything downstream of it. */
  highlightIds?: ReadonlySet<string>;
  /** Nodes to emphasise when nothing is hovered or focused, e.g. search matches. */
  emphasisIds?: ReadonlySet<string>;
  display: DisplaySettings;
  forces: ForceSettings;
  /** Bumped by the "Animate" button: scatter the nodes and let them settle again. */
  animateTick?: number;
}

/**
 * An Obsidian-style force-directed graph on one plain canvas: small flat dots sized by their
 * connections, hairline links, and labels that fade in as you zoom. Hovering a node paints it and
 * its links in the accent colour and fades everything else. Wheel zoom is off until the canvas is
 * clicked so the page still scrolls past it.
 */
export function NetworkCanvas({
  graph,
  graphKey,
  ariaLabel,
  height,
  focusId,
  onFocus,
  colourFor,
  highlightIds,
  emphasisIds,
  display,
  forces,
  animateTick = 0,
}: NetworkCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | undefined>(undefined);
  const [width, setWidth] = useState(0);
  const [palette, setPalette] = useState<Palette | null>(null);
  const [hoverId, setHoverId] = useState<string | null>(null);
  const [wheelZoom, setWheelZoom] = useState(false);

  useEffect(() => {
    setPalette(readPalette());
    const element = containerRef.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  // The library mutates node and link objects (x/y, resolved source/target), so hand it copies.
  const data = useMemo(() => {
    const positions = initialPositions(graph, readPositions(graphKey, graph));
    return {
      nodes: graph.nodes.map((n): SimNode => ({ ...n, ...positions[n.id] })),
      links: graph.edges.map((e): SimLink => ({ ...e })),
    };
  }, [graph, graphKey]);

  const neighbours = useMemo(() => {
    const map = new Map<string, Set<string>>();
    for (const e of graph.edges) {
      if (!map.has(e.source)) map.set(e.source, new Set());
      if (!map.has(e.target)) map.set(e.target, new Set());
      map.get(e.source)!.add(e.target);
      map.get(e.target)!.add(e.source);
    }
    return map;
  }, [graph]);

  const active = hoverId ?? focusId ?? null;
  // Which nodes stay at full strength; null means "all of them".
  const lit = useMemo(() => {
    if (active) {
      const ids = new Set([active, ...(neighbours.get(active) ?? [])]);
      // The extra highlight belongs to the focused node; hovering elsewhere shows that node alone.
      if (active === focusId) for (const id of highlightIds ?? []) ids.add(id);
      return ids;
    }
    return emphasisIds && emphasisIds.size > 0 ? emphasisIds : null;
  }, [active, focusId, neighbours, highlightIds, emphasisIds]);

  const applyForces = useCallback(() => {
    const g = graphRef.current;
    if (!g) return false;
    const f = d3Forces(forces);
    (g.d3Force("charge") as unknown as { strength?: (s: number) => void })?.strength?.(f.charge);
    const link = g.d3Force("link") as unknown as
      | { distance?: (d: number) => unknown; strength?: (s: number) => unknown }
      | undefined;
    link?.distance?.(f.linkDistance);
    link?.strength?.(f.linkStrength);
    (g.d3Force("center") as unknown as { strength?: (s: number) => void })?.strength?.(f.centerStrength);
    return true;
  }, [forces]);

  // Forces apply on change (reheating so the graph visibly responds) and once the canvas exists.
  const forcesApplied = useRef(false);
  useEffect(() => {
    if (applyForces() && forcesApplied.current) graphRef.current?.d3ReheatSimulation();
    forcesApplied.current = graphRef.current !== undefined;
  }, [applyForces]);

  // Refit whenever the data changes (e.g. switching to a local graph). onEngineStop alone isn't
  // enough: the library doesn't always report a stop after new data.
  useEffect(() => {
    const timer = window.setTimeout(() => fit(graphRef.current), 700);
    return () => window.clearTimeout(timer);
  }, [data, width]);

  // "Animate": scatter the nodes on a wide disc and let the simulation pull them back together.
  useEffect(() => {
    if (animateTick === 0) return;
    const spread = 25 * Math.sqrt(Math.max(1, data.nodes.length));
    data.nodes.forEach((n, i) => {
      const angle = i * 2.399963; // golden angle: an even spiral, no randomness
      const radius = spread * Math.sqrt((i + 0.5) / data.nodes.length);
      n.x = radius * Math.cos(angle);
      n.y = radius * Math.sin(angle);
      n.vx = 0;
      n.vy = 0;
    });
    graphRef.current?.d3ReheatSimulation();
    const timer = window.setTimeout(() => fit(graphRef.current), 1500);
    return () => window.clearTimeout(timer);
  }, [animateTick, data]);

  const degree = (id: string) => neighbours.get(id)?.size ?? 0;
  const radius = (n: SimNode) => nodeRadius(degree(String(n.id)), n.size, display.nodeSize);

  return (
    <div
      ref={containerRef}
      role="region"
      aria-label={ariaLabel}
      className="absolute inset-0"
      onClick={() => setWheelZoom(true)}
      onMouseLeave={() => {
        setWheelZoom(false);
        setHoverId(null);
      }}
    >
      <div aria-hidden="true">
        {palette && width > 0 && (
          <ForceGraph2D<NetworkNode, LinkData>
            ref={graphRef}
            graphData={data}
            width={width}
            height={height}
            backgroundColor="rgba(0,0,0,0)"
            warmupTicks={60}
            cooldownTicks={300}
            minZoom={0.15}
            maxZoom={8}
            enableZoomInteraction={wheelZoom}
            nodeLabel={() => ""}
            linkCanvasObjectMode={() => "replace"}
            linkCanvasObject={(l: SimLink, ctx, scale) => {
              const source = l.source as SimNode;
              const target = l.target as SimNode;
              if (source?.x == null || target?.x == null) return;
              const touchesActive =
                active !== null && (endpointId(l.source) === active || endpointId(l.target) === active);
              const isLit = lit === null || (lit.has(endpointId(l.source)) && lit.has(endpointId(l.target)));
              ctx.beginPath();
              ctx.moveTo(source.x, source.y ?? 0);
              ctx.lineTo(target.x, target.y ?? 0);
              ctx.strokeStyle = touchesActive
                ? withAlpha(palette.accent, 0.6)
                : withAlpha(palette.muted, isLit ? 0.14 : 0.03);
              // Screen pixels, whatever the zoom.
              ctx.lineWidth = (display.linkThickness * (touchesActive ? 1.4 : 0.6 + 0.6 * l.weight)) / scale;
              ctx.stroke();
            }}
            nodeCanvasObject={(n: SimNode, ctx, scale) => {
              const id = String(n.id);
              const isActive = id === active;
              const dimmed = lit !== null && !lit.has(id);
              const r = radius(n);
              ctx.globalAlpha = dimmed ? 0.12 : 1;
              ctx.beginPath();
              ctx.arc(n.x ?? 0, n.y ?? 0, r, 0, 2 * Math.PI);
              ctx.fillStyle = isActive ? palette.accent : (colourFor(n) ?? withAlpha(palette.muted, 0.75));
              ctx.fill();

              // Labels fade in with zoom; the active node and its lit neighbours always show theirs.
              const alpha = dimmed
                ? 0
                : active !== null && lit?.has(id)
                  ? 1
                  : labelAlpha(scale, display.textFade);
              if (alpha > 0.02) {
                ctx.globalAlpha = alpha;
                ctx.font = `${isActive ? 500 : 400} ${10.5 / scale}px ${palette.font}`;
                ctx.textAlign = "center";
                ctx.textBaseline = "top";
                ctx.fillStyle = isActive ? palette.foreground : palette.muted;
                ctx.fillText(n.label, n.x ?? 0, (n.y ?? 0) + r + 3 / scale);
              }
              ctx.globalAlpha = 1;
            }}
            nodePointerAreaPaint={(n: SimNode, colour, ctx) => {
              ctx.fillStyle = colour;
              ctx.beginPath();
              ctx.arc(n.x ?? 0, n.y ?? 0, radius(n) + 2, 0, 2 * Math.PI);
              ctx.fill();
            }}
            onNodeHover={(n: SimNode | null) => setHoverId(n ? String(n.id) : null)}
            onNodeClick={(n: SimNode) => onFocus?.(String(n.id) === focusId ? undefined : String(n.id))}
            onBackgroundClick={() => onFocus?.(undefined)}
            onEngineTick={() => {
              if (!forcesApplied.current && applyForces()) forcesApplied.current = true;
            }}
            onEngineStop={() => {
              const positions: Positions = {};
              for (const n of data.nodes) {
                if (n.x != null && n.y != null) positions[String(n.id)] = { x: n.x, y: n.y };
              }
              writePositions(graphKey, graph, positions);
              fit(graphRef.current);
            }}
          />
        )}
      </div>
    </div>
  );
}
