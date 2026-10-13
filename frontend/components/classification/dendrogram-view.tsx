"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { GitMerge, Scissors, Tag } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { EmptyState } from "@/components/states/empty-state";
import { formatPercent } from "@/lib/format";
import type { AssignmentRow, DendrogramNode } from "@/lib/api";
import {
  layoutDendrogram,
  DENDRO_LEFT_PAD,
  DENDRO_TOP_PAD,
  type DendroLayout,
  type DendroLayoutNode,
  type DendroTone,
} from "./dendrogram-layout";

/** What a selected cluster can be curated into. */
export type DendroAction = "rename" | "merge" | "split";

/** A selected dendrogram node, resolved against the assignment queue. */
export interface DendroSelection {
  /** The selected node: a product leaf, or a merge of the leaves beneath it. */
  node: DendrogramNode;
  /** Product leaves beneath the node (the node itself, when it is a leaf). */
  leaves: DendrogramNode[];
  /** The one category covering those leaves; null when they are uncategorised or disagree. */
  categoryId: string | null;
  /** That category's name, when the leaves share one. */
  categoryName: string | null;
  /** How many of the leaves have no category yet. */
  unassigned: number;
  /** Mean confidence across the leaves that have one; null when none does. */
  confidence: number | null;
}

interface DendrogramViewProps {
  nodes: DendrogramNode[];
  /** The assignment queue, used to say whether a cluster already maps to a category. */
  assignments: AssignmentRow[];
  onCurate: (action: DendroAction, selection: DendroSelection, name?: string) => void;
  height?: number;
}

/** Walks the parent links downwards: the product leaves beneath a merge node. */
export function leavesUnder(nodeId: string, nodes: DendrogramNode[]): DendrogramNode[] {
  const children = new Map<string, DendrogramNode[]>();
  for (const node of nodes) {
    if (node.parentId == null) continue;
    const bucket = children.get(node.parentId) ?? [];
    bucket.push(node);
    children.set(node.parentId, bucket);
  }
  const leaves: DendrogramNode[] = [];
  const stack = [...(children.get(nodeId) ?? [])];
  while (stack.length > 0) {
    const node = stack.pop()!;
    if (node.isLeaf) leaves.push(node);
    else stack.push(...(children.get(node.id) ?? []));
  }
  return leaves;
}

/** Mean confidence across the leaves that have one; null when none does. */
function meanConfidence(leaves: DendrogramNode[], assignments: AssignmentRow[]): number | null {
  const values = leaves
    .map((leaf) => assignments.find((a) => a.productNameKey === leaf.productNameKey)?.confidence)
    .filter((confidence): confidence is number => confidence != null);
  if (values.length === 0) return null;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

/** Resolves a dendrogram node into the cluster it covers, against the assignment queue. */
export function toSelection(
  node: DendrogramNode,
  nodes: DendrogramNode[],
  assignments: AssignmentRow[],
): DendroSelection {
  const leaves = node.isLeaf ? [node] : leavesUnder(node.id, nodes);
  const byKey = new Map(assignments.map((a) => [a.productNameKey, a]));
  const rows = leaves.map((leaf) => byKey.get(leaf.productNameKey ?? "") ?? null);
  const categoryIds = new Set(rows.map((row) => row?.categoryId ?? null));
  // One category across every leaf names the cluster; disagreement or none leaves it null.
  const shared = categoryIds.size === 1 ? rows[0]?.categoryId ?? null : null;
  return {
    node,
    leaves,
    categoryId: shared,
    categoryName: shared ? rows.find((row) => row?.categoryId === shared)?.categoryName ?? null : null,
    unassigned: rows.filter((row) => row == null || row.categoryId == null).length,
    confidence: meanConfidence(leaves, assignments),
  };
}

function nodeLabel(node: DendrogramNode): string {
  return node.productNameKey ?? `Merge of ${node.size}`;
}

/** A merge distance rendered on the axis: two decimals, or three when the range is sub-unit. */
function formatHeight(value: number, maxHeight: number): string {
  return maxHeight >= 1 ? value.toFixed(1) : value.toFixed(2);
}

// Tone → colour. Problems draw the eye; a settled node reads as quiet ink, never a loud green.
const TONE_FILL: Record<DendroTone, string> = {
  settled: "hsl(var(--muted-foreground))",
  mixed: "hsl(var(--status-conflict))",
  missing: "hsl(var(--status-missing))",
};
const LEAF_TEXT: Record<DendroTone, string> = {
  settled: "hsl(var(--foreground))",
  mixed: "hsl(var(--status-conflict))",
  missing: "hsl(var(--status-missing))",
};
const EDGE_STROKE: Record<DendroTone, string> = {
  settled: "hsl(var(--muted-foreground) / 0.45)",
  mixed: "hsl(var(--status-conflict))",
  missing: "hsl(var(--status-missing))",
};

/**
 * The select-then-action bar: what the selected cluster covers, and the curation it
 * offers. Rename replaces the shared category's name; Merge puts every product below the
 * node into one category; Split drops those products' manual overrides.
 */
function ActionBar({
  selection,
  renameName,
  renaming,
  onRenameNameChange,
  onRenameStart,
  onRenameSave,
  onRenameCancel,
  onAction,
}: {
  selection: DendroSelection;
  renameName: string;
  renaming: boolean;
  onRenameNameChange: (name: string) => void;
  onRenameStart: () => void;
  onRenameSave: () => void;
  onRenameCancel: () => void;
  onAction: (action: DendroAction) => void;
}) {
  const { leaves, categoryName, unassigned } = selection;
  return (
    <div className="flex flex-col gap-3 rounded-lg border bg-card p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm font-medium">{nodeLabel(selection.node)}</span>
        <Badge variant="neutral">{leaves.length === 1 ? "1 product" : `${leaves.length} products`}</Badge>
        {selection.confidence != null ? (
          <Badge variant="outline">{formatPercent(selection.confidence)} mean confidence</Badge>
        ) : null}
        {categoryName ? <Badge variant="success">{categoryName}</Badge> : null}
        {unassigned > 0 ? <Badge variant="missing">{unassigned} unassigned</Badge> : null}
      </div>

      {renaming ? (
        <div className="flex flex-wrap items-center gap-2">
          <Input
            aria-label="New name"
            value={renameName}
            onChange={(event) => onRenameNameChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") onRenameSave();
            }}
            className="h-8 w-56"
          />
          <Button size="sm" disabled={renameName.trim() === ""} onClick={onRenameSave}>
            Save name
          </Button>
          <Button size="sm" variant="ghost" onClick={onRenameCancel}>
            Cancel
          </Button>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              // Only a cluster that already maps to one category has a name to change.
              disabled={selection.categoryId == null}
              title={selection.categoryId == null ? "Merge the cluster into a category first." : undefined}
              onClick={onRenameStart}
            >
              <Tag aria-hidden="true" />
              Rename
            </Button>
            <Button variant="outline" size="sm" onClick={() => onAction("merge")}>
              <GitMerge aria-hidden="true" />
              Merge into one category
            </Button>
            <Button variant="outline" size="sm" onClick={() => onAction("split")}>
              <Scissors aria-hidden="true" />
              Split
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            Merge puts every product below this node into one category. Split drops those
            products&apos; manual overrides, so they fall back to the classifier&apos;s suggestion.
          </p>
        </div>
      )}
    </div>
  );
}

/** One selectable node: a leaf dot + rotated label, or a merge dot. */
function NodeGlyph({
  node,
  leafY,
  selected,
  onSelect,
}: {
  node: DendroLayoutNode;
  leafY: number;
  selected: boolean;
  onSelect: (id: string) => void;
}) {
  const label = node.isLeaf
    ? (node.productNameKey ?? "")
    : `Merge of ${node.size} products`;
  const colour = node.isLeaf ? LEAF_TEXT[node.tone] : TONE_FILL[node.tone];
  const ring = selected ? "hsl(var(--ring))" : undefined;

  return (
    <g
      role="button"
      tabIndex={0}
      aria-label={label}
      aria-pressed={selected}
      className="dendro-node cursor-pointer"
      onClick={() => onSelect(node.id)}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onSelect(node.id);
        }
      }}
    >
      {node.isLeaf ? (
        <>
          <circle
            cx={node.x}
            cy={leafY}
            r={3.5}
            fill={node.tone === "missing" ? "none" : colour}
            stroke={colour}
            strokeWidth={1.25}
          />
          <text
            transform={`translate(${node.x + 4} ${leafY + 8}) rotate(-45)`}
            textAnchor="end"
            fontSize={11}
            fill={colour}
            className="pointer-events-none select-none"
          >
            {node.productNameKey}
          </text>
        </>
      ) : (
        <circle
          cx={node.x}
          cy={node.y}
          r={4.5}
          fill={node.tone === "missing" ? "none" : colour}
          stroke={colour}
          strokeWidth={1.5}
        />
      )}
      {ring ? (
        <circle
          cx={node.x}
          cy={node.isLeaf ? leafY : node.y}
          r={9}
          fill="none"
          stroke={ring}
          strokeWidth={1.5}
          aria-hidden="true"
        />
      ) : null}
      {/* Generous invisible hit target so a small dot is easy to click. */}
      <circle
        cx={node.x}
        cy={node.isLeaf ? leafY : node.y}
        r={12}
        fill="transparent"
        stroke="none"
        aria-hidden="true"
      />
      <title>{node.isLeaf ? `${node.productNameKey ?? ""}` : `Merge of ${node.size} products`}</title>
    </g>
  );
}

/** Measures the container so a small tree stretches to fill it while a wide one scrolls. */
function useContainerWidth(): [React.RefObject<HTMLDivElement | null>, number] {
  const ref = useRef<HTMLDivElement | null>(null);
  const [width, setWidth] = useState(960);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => setWidth(el.clientWidth || 960);
    measure();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  return [ref, width];
}

/**
 * The agglomerative dendrogram for the latest clustering run: leaves in a row along the bottom,
 * merges raised by their merge height on the distance axis. Selecting a node says which cluster it
 * covers and offers curation of that cluster. A proper dendrogram, not a node graph: the vertical
 * axis is the merge distance, edges are undirected elbows, and only problems get colour.
 */
export function DendrogramView({ nodes, assignments, onCurate, height = 800 }: DendrogramViewProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [renaming, setRenaming] = useState(false);
  const [renameName, setRenameName] = useState("");
  const [ref, width] = useContainerWidth();

  const laid: DendroLayout = useMemo(
    () => layoutDendrogram(nodes, assignments, { width, height }),
    [nodes, assignments, width, height],
  );

  const selected = selectedId ? nodes.find((node) => node.id === selectedId) ?? null : null;
  const selection = useMemo(
    () => (selected ? toSelection(selected, nodes, assignments) : null),
    [selected, nodes, assignments],
  );

  const select = (id: string) => {
    setSelectedId((current) => (current === id ? null : id));
    setRenaming(false);
  };

  const saveRename = () => {
    if (!selection || renameName.trim() === "") return;
    onCurate("rename", selection, renameName.trim());
    setRenaming(false);
  };

  if (nodes.length === 0) {
    return (
      <EmptyState
        title="No clustering run yet"
        description="Run a classification to embed and cluster products; the merge tree lands here."
      />
    );
  }

  const { maxHeight, leafY, axisHeight, width: contentWidth, height: contentHeight } = laid;
  const ticks = maxHeight <= 0 ? [0] : [0, maxHeight / 2, maxHeight];

  return (
    <div className="flex flex-col gap-3">
      <div ref={ref} role="region" aria-label="Product clustering dendrogram: merges and their heights">
        <div className="overflow-x-auto rounded-lg border bg-muted/30" style={{ height }}>
          <svg
            width={contentWidth}
            height={contentHeight}
            viewBox={`0 0 ${contentWidth} ${contentHeight}`}
            className="block"
            style={{ margin: "0 auto" }}
            aria-hidden={nodes.length > 0 ? undefined : true}
          >
            {/* Distance axis, ticks at the bottom, middle and top of the merge range. */}
            <line
              x1={DENDRO_LEFT_PAD}
              y1={leafY}
              x2={DENDRO_LEFT_PAD}
              y2={DENDRO_TOP_PAD}
              stroke="hsl(var(--muted-foreground) / 0.4)"
              aria-hidden="true"
            />
            {ticks.map((value) => {
              const y = leafY - (maxHeight <= 0 ? 0 : (value / maxHeight) * axisHeight);
              return (
                <g key={value} aria-hidden="true">
                  <line x1={DENDRO_LEFT_PAD - 4} x2={DENDRO_LEFT_PAD} y1={y} y2={y} stroke="hsl(var(--muted-foreground) / 0.4)" />
                  <text
                    x={DENDRO_LEFT_PAD - 7}
                    y={y}
                    textAnchor="end"
                    dominantBaseline="middle"
                    fontSize={10}
                    fill="hsl(var(--muted-foreground))"
                    className="tabular-nums"
                  >
                    {formatHeight(value, maxHeight)}
                  </text>
                </g>
              );
            })}

            {/* Undirected elbows: child rises to the parent's height, then across to the parent. */}
            {laid.edges.map((edge) => {
              const source = laid.nodes.find((n) => n.id === edge.source);
              const target = laid.nodes.find((n) => n.id === edge.target);
              if (!source || !target) return null;
              return (
                <path
                  key={edge.id}
                  d={`M ${source.x} ${source.y} V ${target.y} H ${target.x}`}
                  fill="none"
                  stroke={EDGE_STROKE[source.tone]}
                  strokeWidth={source.tone === "mixed" ? 1.5 : 1}
                  strokeDasharray={source.tone === "missing" ? "4 4" : undefined}
                  aria-hidden="true"
                />
              );
            })}

            {laid.nodes.map((node) => (
              <NodeGlyph key={node.id} node={node} leafY={leafY} selected={node.id === selectedId} onSelect={select} />
            ))}
          </svg>
        </div>
      </div>

      {selection ? (
        <ActionBar
          selection={selection}
          renameName={renameName}
          renaming={renaming}
          onRenameNameChange={setRenameName}
          onRenameStart={() => {
            setRenameName(nodeLabel(selection.node));
            setRenaming(true);
          }}
          onRenameSave={saveRename}
          onRenameCancel={() => setRenaming(false)}
          onAction={(action) => onCurate(action, selection)}
        />
      ) : (
        <p className="text-sm text-muted-foreground">Select a node to name, merge or split a cluster.</p>
      )}
    </div>
  );
}
