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
import { layoutColumns } from "./layout";
import { StepNodeView } from "./step-node";
import type { ColumnGraph, StepNode, StepNodeData } from "./types";

const nodeTypes: NodeTypes = { step: StepNodeView };

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

interface FlowCanvasProps {
  graph: ColumnGraph;
  /** Accessible name for the canvas region; the graph itself is decorative to screen readers. */
  ariaLabel: string;
  height?: number;
  /** When set, clicking a node with `data.drill` calls this instead of navigating. */
  onDrill?: (id: string) => void;
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

function FlowCanvasInner({ graph, ariaLabel, height = 360, onDrill }: FlowCanvasProps) {
  const router = useRouter();
  const laid = useMemo(() => layoutColumns(graph), [graph]);
  const [nodes, setNodes, onNodesChange] = useNodesState<StepNode>(laid.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(laid.edges);

  // Re-seed when the data behind the graph changes (e.g. after a reload); drag positions reset.
  useEffect(() => {
    setNodes(laid.nodes);
    setEdges(laid.edges);
  }, [laid, setNodes, setEdges]);

  return (
    <div
      role="region"
      aria-label={ariaLabel}
      className="overflow-hidden rounded-lg border bg-muted/30"
      style={{ height }}
    >
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={(_, node) => {
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
        minZoom={0.3}
        maxZoom={1.5}
        proOptions={{ hideAttribution: true }}
      >
        <Background variant={BackgroundVariant.Dots} gap={18} size={1.2} />
        <Controls showInteractive={false} position="bottom-right" />
      </ReactFlow>
    </div>
  );
}
