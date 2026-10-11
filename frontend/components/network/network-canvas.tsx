"use client";

import dynamic from "next/dynamic";
import { useEffect, useMemo, useRef, useState } from "react";
import type ForceGraphComponent from "react-force-graph-2d";
import type { ForceGraphMethods, LinkObject, NodeObject } from "react-force-graph-2d";
import type { FlowTone } from "@/components/flow/types";
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

/** CSS custom properties the canvas paints with, read once at mount (the app has one theme). */
const TOKENS = {
  foreground: "--foreground",
  muted: "--muted-foreground",
  background: "--background",
  ok: "--status-success",
  warn: "--status-conflict",
  fail: "--destructive",
  missing: "--status-missing",
  info: "--status-info",
} as const;
type Palette = Record<keyof typeof TOKENS, string>;

function readPalette(): Palette {
  const style = getComputedStyle(document.documentElement);
  const entries = Object.entries(TOKENS).map(([key, variable]) => {
    const value = style.getPropertyValue(variable).trim();
    return [key, value ? `hsl(${value})` : "#888"];
  });
  return Object.fromEntries(entries) as Palette;
}

/** The ring colour for a tone; `ok` and `neutral` draw no ring so only problems stand out. */
function ringColour(tone: FlowTone, palette: Palette): string | null {
  switch (tone) {
    case "warn":
      return palette.warn;
    case "fail":
      return palette.fail;
    case "missing":
      return palette.missing;
    case "info":
      return palette.info;
    default:
      return null;
  }
}

export const nodeRadius = (size: number) => 3 + size * 1.4;

interface NetworkCanvasProps {
  graph: NetworkGraph;
  /** Names the cached layout, e.g. "platform-map". */
  graphKey: string;
  /** Accessible name for the region; the canvas itself is hidden from screen readers. */
  ariaLabel: string;
  height?: number;
  focusId?: string;
  onFocus?: (id: string | undefined) => void;
  /** Fill colour per node, e.g. by category or kind. */
  colourFor: (node: NetworkNode) => string;
}

/**
 * An Obsidian-style force-directed graph: nodes settle under physics, hovering a node keeps it and
 * its neighbours lit and dims the rest, clicking focuses it. Wheel zoom is off until the canvas is
 * clicked so the page still scrolls past it (as `FlowCanvas` does).
 */
export function NetworkCanvas({
  graph,
  graphKey,
  ariaLabel,
  height = 480,
  focusId,
  onFocus,
  colourFor,
}: NetworkCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<ForceGraphMethods<SimNode, SimLink> | undefined>(undefined);
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
  const lit = useMemo(() => {
    if (!active) return null;
    return new Set([active, ...(neighbours.get(active) ?? [])]);
  }, [active, neighbours]);

  const endpointId = (end: SimLink["source"]) =>
    typeof end === "object" && end !== null ? String(end.id) : String(end);

  return (
    <div
      ref={containerRef}
      role="region"
      aria-label={ariaLabel}
      className="overflow-hidden rounded-lg border bg-muted/30"
      style={{ height }}
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
            cooldownTicks={200}
            minZoom={0.2}
            maxZoom={6}
            enableZoomInteraction={wheelZoom}
            nodeLabel={(n: SimNode) => (n.subtitle ? `${n.label} · ${n.subtitle}` : n.label)}
            linkLabel={(l: SimLink) => l.label ?? ""}
            linkColor={(l: SimLink) => {
              const touchesLit =
                !lit || (lit.has(endpointId(l.source)) && lit.has(endpointId(l.target)));
              const base = l.tone ? (ringColour(l.tone, palette) ?? palette.muted) : palette.muted;
              return touchesLit ? withAlpha(base, 0.55) : withAlpha(base, 0.08);
            }}
            linkWidth={(l: SimLink) => 0.6 + 2.4 * l.weight}
            nodeCanvasObject={(n: SimNode, ctx, scale) => {
              const dimmed = lit !== null && !lit.has(String(n.id));
              const r = nodeRadius(n.size);
              ctx.globalAlpha = dimmed ? 0.15 : 1;
              ctx.beginPath();
              ctx.arc(n.x ?? 0, n.y ?? 0, r, 0, 2 * Math.PI);
              ctx.fillStyle = colourFor(n);
              ctx.fill();
              const ring = ringColour(n.tone, palette);
              if (ring || n.id === focusId) {
                ctx.lineWidth = n.id === focusId ? 2.5 / scale + 1 : 1.5;
                ctx.strokeStyle = n.id === focusId ? palette.foreground : ring!;
                ctx.stroke();
              }
              const showLabel = scale > 1.6 || n.id === hoverId || n.id === focusId || (lit?.has(String(n.id)) ?? false);
              if (showLabel && !dimmed) {
                const fontSize = Math.max(10 / scale, 2.5);
                ctx.font = `${fontSize}px var(--font-geist-sans, sans-serif)`;
                ctx.textAlign = "center";
                ctx.textBaseline = "top";
                ctx.fillStyle = palette.foreground;
                ctx.fillText(n.label, n.x ?? 0, (n.y ?? 0) + r + 1.5);
              }
              ctx.globalAlpha = 1;
            }}
            nodePointerAreaPaint={(n: SimNode, colour, ctx) => {
              ctx.fillStyle = colour;
              ctx.beginPath();
              ctx.arc(n.x ?? 0, n.y ?? 0, nodeRadius(n.size) + 2, 0, 2 * Math.PI);
              ctx.fill();
            }}
            onNodeHover={(n: SimNode | null) => setHoverId(n ? String(n.id) : null)}
            onNodeClick={(n: SimNode) => onFocus?.(String(n.id) === focusId ? undefined : String(n.id))}
            onBackgroundClick={() => onFocus?.(undefined)}
            onEngineStop={() => {
              const positions: Positions = {};
              for (const n of data.nodes) {
                if (n.x != null && n.y != null) positions[String(n.id)] = { x: n.x, y: n.y };
              }
              writePositions(graphKey, graph, positions);
              graphRef.current?.zoomToFit(300, 24);
            }}
          />
        )}
      </div>
    </div>
  );
}

/** `hsl(h s% l%)` → `hsl(h s% l% / a)`; other colour strings pass through unchanged. */
function withAlpha(colour: string, alpha: number): string {
  return colour.startsWith("hsl(") && !colour.includes("/")
    ? colour.replace(/\)$/, ` / ${alpha})`)
    : colour;
}
