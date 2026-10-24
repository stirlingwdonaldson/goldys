"use client";

import "@xyflow/react/dist/style.css";

import { useEffect, useMemo } from "react";
import { useRouter } from "next/navigation";
import {
  Background,
  BackgroundVariant,
  Controls,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  type NodeTypes,
} from "@xyflow/react";
import { layoutColumns, type FlowDirection } from "./layout";
import { ColumnHeading } from "./column-heading";
import { StepNodeView } from "./step-node";
import type {
  ColumnGraph,
  FlowEdge,
  FlowNode,
  StepNode,
  StepNodeData,
} from "./types";

const nodeTypes: NodeTypes = {
  step: StepNodeView,
  columnHeading: ColumnHeading,
};

/**
 * Decides what a node click does: a node with a `drill` id and an `onDrill` handler drills in;
 * otherwise a node with an `href` navigates. Extracted so the precedence is unit-testable without
 * rendering ReactFlow.
 */
export function resolveNodeClick(
  data: StepNodeData,
  onDrill: ((id: string) => void) | undefined,
  navigate: (href: string) => void,
): void {
  if (onDrill && data.drill) {
    onDrill(data.drill);
    return;
  }
  if (data.href) {
    navigate(data.href);
  }
}

/**
 * Nodes and edges already positioned by one of the `layout.ts` tree layouts
 * (`layoutColumns`, `layoutTree`). Their absolute coordinates are used as-is,
 * so `fitView` frames the picture.
 */
export interface LaidOutGraph {
  nodes: FlowNode[];
  edges: FlowEdge[];
}

/** Renders nothing; the canvas still draws the dotted background and controls. */
const EMPTY_GRAPH: ColumnGraph = { columns: [], edges: [] };

interface FlowCanvasProps {
  /** Logical column graph, laid out with `layoutColumns`. Omit when `laidOut` is given. */
  graph?: ColumnGraph;
  /** Accessible name for the canvas region; the graph itself is decorative to screen readers. */
  ariaLabel: string;
  height?: number;
  /** Large folder trees need a wider zoom range than small workflow diagrams. */
  minZoom?: number;
  /** When set, clicking a node with `data.drill` calls this instead of navigating. */
  onDrill?: (id: string) => void;
  /** Pre-positioned nodes/edges; takes precedence over `graph`. */
  laidOut?: LaidOutGraph;
  /** Horizontal space between stages; tighter for wide graphs so they stay readable when fitted. */
  columnGap?: number;
  /** Left-to-right (default) or top-to-bottom. */
  direction?: FlowDirection;
}

/**
 * A read-only, n8n-style node canvas: dotted grid, left-to-right stages, draggable nodes,
 * zoom via the corner controls. Scroll-wheel zoom is off so the page still scrolls normally
 * when the pointer passes over the canvas. Clicking a node with an `href` navigates there.
 */
export function FlowCanvas(props: FlowCanvasProps) {
  return (
    <ReactFlowProvider>
      <FlowCanvasInner {...props} />
    </ReactFlowProvider>
  );
}

function FlowCanvasInner({
  graph,
  ariaLabel,
  height = 360,
  minZoom = 0.3,
  onDrill,
  laidOut,
  columnGap,
  direction,
}: FlowCanvasProps) {
  const router = useRouter();
  const laid = useMemo(
    () =>
      laidOut ?? layoutColumns(graph ?? EMPTY_GRAPH, { columnGap, direction }),
    [laidOut, graph, columnGap, direction],
  );
  const accessibleNodes = useMemo(
    () =>
      laid.nodes.map((node) =>
        node.type === "step" && (node.data.href || node.data.drill)
          ? { ...node, ariaRole: "button", ariaLabel: node.data.title }
          : node,
      ),
    [laid.nodes],
  );
  const [nodes, setNodes, onNodesChange] =
    useNodesState<FlowNode>(accessibleNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(laid.edges);

  // Re-seed when the data behind the graph changes (e.g. after a reload); drag positions reset.
  useEffect(() => {
    setNodes(accessibleNodes);
    setEdges(laid.edges);
  }, [laid, accessibleNodes, setNodes, setEdges]);

  return (
    <div
      role="region"
      aria-label={ariaLabel}
      className="overflow-hidden rounded-lg border bg-muted/30"
      style={{ height }}
      onKeyDownCapture={(event) => {
        // React Flow selects on Enter; linked nodes and folder toggles should activate.
        if (event.key !== "Enter" && event.key !== " ") return;
        const element = (event.target as HTMLElement).closest(
          ".react-flow__node",
        );
        const node = nodes.find(
          (candidate) => candidate.id === element?.getAttribute("data-id"),
        );
        if (node?.type !== "step" || (!node.data.href && !node.data.drill))
          return;
        event.preventDefault();
        event.stopPropagation();
        resolveNodeClick(node.data, onDrill, (href) => router.push(href));
      }}
    >
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={(_, node) => {
          if (node.type !== "step") return;
          const data = (node as StepNode).data;
          resolveNodeClick(data, onDrill, (href) => router.push(href));
        }}
        nodesConnectable={false}
        edgesFocusable={false}
        zoomOnScroll={false}
        preventScrolling={false}
        panOnScroll={false}
        fitView
        fitViewOptions={{ padding: 0.15, maxZoom: 1 }}
        minZoom={minZoom}
        maxZoom={1.5}
        proOptions={{ hideAttribution: true }}
      >
        <Background variant={BackgroundVariant.Dots} gap={18} size={1.2} />
        <Controls showInteractive={false} position="bottom-right" />
      </ReactFlow>
    </div>
  );
}
