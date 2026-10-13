"use client";

import "@react-sigma/core/lib/style.css";

import { useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import Graph from "graphology";
import forceAtlas2 from "graphology-layout-forceatlas2";
import { SigmaContainer, useRegisterEvents, useSetSettings, useSigma } from "@react-sigma/core";
import { Maximize, Minus, Plus } from "lucide-react";
import { mixColour, toRgba } from "./colour";
import { fa2Settings, labelThreshold, nodeSize, type ForceSettings } from "./graph-settings";
import { initialPositions, readPositions, writePositions, type Positions } from "./layout-cache";
import type { NetworkCanvasProps } from "./network-canvas";
import type { NetworkNode } from "./types";
import { VisualTween } from "./tween";

/** How long a hover/focus highlight takes to fade in or out. */
const HIGHLIGHT_FADE_MS = 180;

interface NodeAttributes {
  x: number;
  y: number;
  label: string;
  /** Size before the viewer's node-size setting; the reducer scales it. */
  baseSize: number;
  size: number;
  color: string;
  node: NetworkNode;
}
interface EdgeAttributes {
  weight: number;
  label?: string;
  size: number;
  color: string;
}
type NetworkGraphModel = Graph<NodeAttributes, EdgeAttributes>;

/** Design tokens, resolved once (WebGL can't read `var(...)`, or parse hsl()). */
function readTokens() {
  const style = getComputedStyle(document.documentElement);
  const token = (name: string) => {
    const value = style.getPropertyValue(name).trim();
    return value ? `hsl(${value})` : "hsl(0 0% 50%)";
  };
  return {
    foreground: token("--foreground"),
    muted: token("--muted-foreground"),
    accent: token("--chart-3"),
    background: token("--card"),
    font: getComputedStyle(document.body).fontFamily || "sans-serif",
  };
}
type Tokens = ReturnType<typeof readTokens>;

/**
 * Draws a node's label centred below it, Obsidian style. Used for hover too, so hovering adds no
 * box or outline: the hovered node's label is simply forced on.
 */
function drawLabelBelow(
  context: CanvasRenderingContext2D,
  data: { x: number; y: number; size: number; label?: string | null },
  settings: { labelSize: number; labelFont: string; labelWeight: string; labelColor: { color?: string } },
) {
  if (!data.label) return;
  context.font = `${settings.labelWeight} ${settings.labelSize}px ${settings.labelFont}`;
  context.fillStyle = settings.labelColor.color ?? "#555";
  context.textAlign = "center";
  context.textBaseline = "top";
  context.fillText(data.label, data.x, data.y + data.size + 3);
}

/** Iterations to settle a fresh layout. ForceAtlas2 is deterministic from fixed starting points. */
const SETTLE_ITERATIONS = 400;

function buildGraph(props: NetworkCanvasProps, forces: ForceSettings): { model: NetworkGraphModel; cached: boolean } {
  const { graph, graphKey } = props;
  const model: NetworkGraphModel = new Graph({ type: "undirected", multi: false });
  const cachedPositions = readPositions(graphKey, graph);
  const start = initialPositions(graph, cachedPositions);
  const degree = new Map<string, number>();
  for (const e of graph.edges) {
    degree.set(e.source, (degree.get(e.source) ?? 0) + 1);
    degree.set(e.target, (degree.get(e.target) ?? 0) + 1);
  }
  for (const n of graph.nodes) {
    const baseSize = nodeSize(degree.get(n.id) ?? 0, n.size, 1);
    model.addNode(n.id, { ...start[n.id], label: n.label, baseSize, size: baseSize, color: "#999", node: n });
  }
  for (const e of graph.edges) {
    if (e.source !== e.target && !model.hasEdge(e.source, e.target)) {
      model.addEdge(e.source, e.target, { weight: e.weight, label: e.label, size: 1, color: "#ccc" });
    }
  }
  const cached = cachedPositions !== undefined && graph.nodes.every((n) => cachedPositions[n.id]);
  if (!cached && model.order > 1) {
    forceAtlas2.assign(model, { iterations: SETTLE_ITERATIONS, getEdgeWeight: "weight", settings: fa2Settings(forces) });
  }
  return { model, cached };
}

function savePositions(model: NetworkGraphModel, props: NetworkCanvasProps) {
  const positions: Positions = {};
  model.forEachNode((id, a) => {
    positions[id] = { x: a.x, y: a.y };
  });
  writePositions(props.graphKey, props.graph, positions);
}

/**
 * The Sigma.js (WebGL) renderer behind {@link NetworkCanvas}. Sigma does the drawing that made the
 * hand-rolled canvas look rough: anti-aliased nodes and edges, a label grid that drops colliding
 * labels instead of overprinting them, hover labels in a box, smooth camera moves. ForceAtlas2 lays
 * the graph out.
 */
export default function SigmaGraph(props: NetworkCanvasProps) {
  const [tokens, setTokens] = useState<Tokens | null>(null);
  useEffect(() => setTokens(readTokens()), []);

  // Forces are read when a graph is first laid out; later changes re-run the layout in place.
  const forcesRef = useRef(props.forces);
  forcesRef.current = props.forces;
  const { model } = useMemo(
    () => buildGraph(props, forcesRef.current),
    // Rebuild only for a new graph, not on every prop change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [props.graph, props.graphKey],
  );

  if (!tokens) return null;
  return (
    <SigmaContainer<NodeAttributes, EdgeAttributes>
      graph={model}
      className="!bg-transparent"
      style={{ height: props.height, ["--sigma-background-color" as string]: "transparent" } as CSSProperties}
      settings={{
        allowInvalidContainer: true,
        renderEdgeLabels: false,
        zIndex: true,
        labelFont: tokens.font,
        labelSize: 11,
        labelWeight: "500",
        labelColor: { color: toRgba(tokens.foreground, 0.75) },
        labelDensity: 0.9,
        labelGridCellSize: 90,
        labelRenderedSizeThreshold: labelThreshold(props.display.textFade),
        defaultDrawNodeLabel: drawLabelBelow,
        defaultDrawNodeHover: () => {},
        stagePadding: 48,
        minCameraRatio: 0.08,
        maxCameraRatio: 4,
        enableCameraZooming: false,
      }}
    >
      <Controller {...props} model={model} tokens={tokens} />
    </SigmaContainer>
  );
}

function Controller(props: NetworkCanvasProps & { model: NetworkGraphModel; tokens: Tokens }) {
  const { model, tokens, focusId, onFocus, colourFor, emphasisIds, display, forces, animateTick = 0 } = props;
  const sigma = useSigma<NodeAttributes, EdgeAttributes>();
  const setSettings = useSetSettings<NodeAttributes, EdgeAttributes>();
  const registerEvents = useRegisterEvents<NodeAttributes, EdgeAttributes>();
  const [hoverId, setHoverId] = useState<string | null>(null);
  const [wheelZoom, setWheelZoom] = useState(false);
  const dragging = useRef<string | null>(null);
  // One tween per graph: node and edge ids from an old graph mean nothing in a new one.
  const tween = useMemo(() => {
    void model;
    return new VisualTween(HIGHLIGHT_FADE_MS);
  }, [model]);

  const active = hoverId ?? focusId ?? null;
  // Full strength for the active node and anything directly linked to it; everything further away
  // fades. With nothing active, search matches (if any) stay full strength.
  const lit = useMemo(() => {
    if (active && model.hasNode(active)) return new Set([active, ...model.neighbors(active)]);
    return emphasisIds && emphasisIds.size > 0 ? emphasisIds : null;
  }, [active, model, emphasisIds]);

  // Colour, size and highlight are reducers over the graph, so display changes never rebuild it.
  // Faint and faded colours are opaque mixes toward the card background (see mixColour).
  useEffect(() => {
    const bg = tokens.background;
    const grey = mixColour(tokens.muted, bg, 0.35);
    const edgeQuiet = mixColour(tokens.muted, bg, 0.72);
    const edgeLit = mixColour(tokens.muted, bg, 0.5);
    const edgeFaded = mixColour(tokens.muted, bg, 0.93);
    const edgeActive = mixColour(tokens.accent, bg, 0.25);
    setSettings({
      labelRenderedSizeThreshold: labelThreshold(display.textFade),
      enableCameraZooming: wheelZoom,
      // Each reducer works out the element's target look, then the tween eases colour and size
      // toward it, so highlights fade in and out instead of snapping (HIGHLIGHT_FADE_MS).
      nodeReducer: (id, a) => {
        const colour = colourFor(a.node);
        const fill = colour ? toRgba(colour) : grey;
        const base = { ...a, size: a.baseSize * display.nodeSize, color: fill };
        let target: typeof base & { forceLabel?: boolean; zIndex?: number } = base;
        if (lit !== null && lit.has(id)) {
          target = { ...base, forceLabel: active !== null, zIndex: id === active ? 3 : 2 };
        } else if (lit !== null) {
          target = { ...base, color: mixColour(fill, bg, 0.85), label: "", zIndex: 0 };
        }
        return { ...target, ...tween.resolve(`n:${id}`, target, performance.now()) };
      },
      edgeReducer: (id, a) => {
        const [source, target] = model.extremities(id);
        const size = display.linkThickness * (0.8 + 0.8 * a.weight);
        let look: EdgeAttributes & { zIndex: number };
        if (active !== null && (source === active || target === active)) {
          look = { ...a, size: size * 1.5, color: edgeActive, zIndex: 2 };
        } else if (lit !== null && !(lit.has(source) && lit.has(target))) {
          look = { ...a, size, color: edgeFaded, zIndex: 0 };
        } else {
          look = { ...a, size, color: lit === null ? edgeQuiet : edgeLit, zIndex: 1 };
        }
        return { ...look, ...tween.resolve(`e:${id}`, look, performance.now()) };
      },
    });
    // setSettings refreshes with the new targets (Sigma may defer that to its next frame), so
    // refresh every frame for at least one fade, and for as long as anything is still moving.
    const settleBy = performance.now() + HIGHLIGHT_FADE_MS;
    let frame = requestAnimationFrame(function tick() {
      sigma.refresh();
      if (performance.now() < Math.max(settleBy, tween.activeUntil())) frame = requestAnimationFrame(tick);
    });
    return () => cancelAnimationFrame(frame);
  }, [setSettings, sigma, tween, model, tokens, colourFor, display, active, lit, wheelZoom]);

  useEffect(() => {
    registerEvents({
      enterNode: ({ node }) => setHoverId(node),
      leaveNode: () => setHoverId(null),
      clickNode: ({ node }) => onFocus?.(node === focusId ? undefined : node),
      clickStage: () => {
        setWheelZoom(true);
        onFocus?.(undefined);
      },
      // Drag a node, Obsidian style: it follows the pointer and stays where it's dropped.
      downNode: ({ node }) => {
        dragging.current = node;
        setWheelZoom(true);
        if (!sigma.getCustomBBox()) sigma.setCustomBBox(sigma.getBBox());
      },
      moveBody: ({ event }) => {
        if (!dragging.current) return;
        const position = sigma.viewportToGraph(event);
        model.mergeNodeAttributes(dragging.current, { x: position.x, y: position.y });
        event.preventSigmaDefault();
        event.original.preventDefault();
        event.original.stopPropagation();
      },
      upNode: () => {
        if (dragging.current) savePositions(model, props);
        dragging.current = null;
      },
      upStage: () => {
        if (dragging.current) savePositions(model, props);
        dragging.current = null;
      },
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [registerEvents, sigma, model, onFocus, focusId]);

  // Re-run the layout in place, a few iterations a frame, so the graph visibly settles.
  const runLayout = useRef<(frames: number) => void>(() => {});
  runLayout.current = (frames: number) => {
    let frame = 0;
    const settings = fa2Settings(forces);
    const step = () => {
      forceAtlas2.assign(model, { iterations: 4, getEdgeWeight: "weight", settings });
      frame += 1;
      if (frame < frames) requestAnimationFrame(step);
      else {
        savePositions(model, props);
        sigma.getCamera().animatedReset({ duration: 400 });
      }
    };
    requestAnimationFrame(step);
  };

  const forcesKey = `${forces.center}|${forces.repel}|${forces.link}`;
  const firstForces = useRef(true);
  useEffect(() => {
    if (firstForces.current) {
      firstForces.current = false;
      return;
    }
    runLayout.current(60);
  }, [forcesKey]);

  // "Animate": scatter on an even spiral and let ForceAtlas2 pull it back together.
  useEffect(() => {
    if (animateTick === 0) return;
    const count = model.order;
    let i = 0;
    model.forEachNode((id) => {
      const angle = i * 2.399963; // golden angle
      const radius = 30 * Math.sqrt((i + 0.5) / count) * Math.sqrt(count);
      model.mergeNodeAttributes(id, { x: radius * Math.cos(angle), y: radius * Math.sin(angle) });
      i += 1;
    });
    sigma.setCustomBBox(null);
    runLayout.current(120);
  }, [animateTick, model, sigma]);

  const camera = sigma.getCamera();
  return (
    <div
      className="absolute bottom-3 right-3 z-10 flex flex-col overflow-hidden rounded-md border bg-background/95 shadow-sm backdrop-blur"
      onMouseLeave={() => setHoverId(null)}
    >
      {[
        { label: "Zoom in", icon: Plus, run: () => camera.animatedZoom({ duration: 250 }) },
        { label: "Zoom out", icon: Minus, run: () => camera.animatedUnzoom({ duration: 250 }) },
        {
          label: "Fit graph",
          icon: Maximize,
          run: () => {
            sigma.setCustomBBox(null);
            camera.animatedReset({ duration: 400 });
          },
        },
      ].map(({ label, icon: Icon, run }) => (
        <button
          key={label}
          type="button"
          aria-label={label}
          title={label}
          onClick={run}
          className="flex size-7 items-center justify-center border-b text-muted-foreground last:border-b-0 hover:bg-muted hover:text-foreground"
        >
          <Icon className="size-3.5" />
        </button>
      ))}
    </div>
  );
}
