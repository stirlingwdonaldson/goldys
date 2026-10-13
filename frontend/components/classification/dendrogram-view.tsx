"use client";

import { useMemo, useState } from "react";
import { GitMerge, Scissors, Tag } from "lucide-react";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import type { FlowNode, StepNode } from "@/components/flow/types";
import { layoutDendrogram, type DendrogramInputNode } from "@/components/flow/layout";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { EmptyState } from "@/components/states/empty-state";
import { formatPercent } from "@/lib/format";
import type { AssignmentRow, DendrogramNode } from "@/lib/api";

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

/** Leaves are products; an internal node is a merge of everything below it. */
function toInputNode(node: DendrogramNode): DendrogramInputNode {
  return {
    id: node.id,
    parentId: node.parentId,
    isLeaf: node.isLeaf,
    label: node.productNameKey ?? `Merge of ${node.size}`,
    height: node.mergeHeight,
    size: node.size,
  };
}

function nodeLabel(node: DendrogramNode): string {
  return node.productNameKey ?? `Merge of ${node.size}`;
}

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

/**
 * The agglomerative dendrogram for the latest clustering run: leaves in a row, merges
 * raised by their merge height. Selecting a node says which cluster it covers and offers
 * curation of that cluster.
 */
export function DendrogramView({ nodes, assignments, onCurate, height = 460 }: DendrogramViewProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [renaming, setRenaming] = useState(false);
  const [renameName, setRenameName] = useState("");

  const laid = useMemo(() => {
    const { nodes: flowNodes, edges } = layoutDendrogram(nodes.map(toInputNode));
    // `drill` is the id a click reports back, which is how the canvas drives selection here.
    const selectable: FlowNode[] = flowNodes.map((node) => {
      const step = node as StepNode;
      return { ...step, data: { ...step.data, drill: step.id } };
    });
    return { nodes: selectable, edges };
  }, [nodes]);

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

  return (
    <div className="flex flex-col gap-3">
      <FlowCanvas
        laidOut={laid}
        ariaLabel="Product clustering dendrogram: merges and their heights"
        onDrill={select}
        height={height}
      />

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
