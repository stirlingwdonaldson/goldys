import type { AssignmentRow, DendrogramNode } from "@/lib/api";

/**
 * How a node reads at a glance. This is the dendrogram's own vocabulary, kept separate from the
 * shared `FlowTone` because the meanings differ: "settled" = every leaf already sits in one shared
 * category, "mixed" = the leaves disagree (or some are unassigned) so a decision is needed,
 * "missing" = nothing has been assigned yet.
 */
export type DendroTone = "settled" | "mixed" | "missing";

export interface DendroLayoutNode {
  id: string;
  parentId: string | null;
  isLeaf: boolean;
  productNameKey: string | null;
  /** Authoritative leaf count from the run (shown in the action bar). */
  size: number;
  mergeHeight: number;
  /** Centre of the node along the leaf axis, px. */
  x: number;
  /** Centre of the node along the distance axis, px (smaller = higher up = later merge). */
  y: number;
  tone: DendroTone;
}

export interface DendroLayoutEdge {
  id: string;
  /** Child id. */
  source: string;
  /** Parent id. */
  target: string;
}

export interface DendroLayout {
  nodes: DendroLayoutNode[];
  edges: DendroLayoutEdge[];
  width: number;
  height: number;
  /** The distance axis spans this many px (between `topPad` and `leafY`). */
  axisHeight: number;
  /** y of the leaf row: the bottom baseline where every leaf sits. */
  leafY: number;
  /** Largest merge height in the tree (0 when there are no merges). */
  maxHeight: number;
  /** Horizontal slot per leaf, px. */
  slot: number;
}

export const DENDRO_LEFT_PAD = 48;
export const DENDRO_RIGHT_PAD = 16;
export const DENDRO_TOP_PAD = 14;
/** Room below the leaf row for the -45° product labels. */
export const DENDRO_BOTTOM_PAD = 80;
export const DENDRO_MIN_SLOT = 16;
/** Cap the per-leaf slot so a handful of products doesn't stretch into an airy, wide tree. */
export const DENDRO_MAX_SLOT = 160;
const DENDRO_MIN_AXIS = 120;

/** A product with no category yet reads as "nothing here", so it stays out of the status palette. */
export function dendroToneForLeaf(row: AssignmentRow | undefined): DendroTone {
  if (row == null || row.categoryId == null || row.status === "UNASSIGNED") return "missing";
  if (row.status === "NEEDS_REVIEW") return "mixed";
  return "settled";
}

interface Subtree {
  total: number;
  /** Leaves whose status is ASSIGNED (so the cluster is genuinely settled). */
  settled: number;
  /** Leaves with no category yet. */
  unassigned: number;
  categoryIds: Set<string>;
  minX: number;
  maxX: number;
}

/** A leaf counts as settled only when it is ASSIGNED, not merely when a category id is present. */
function isSettled(row: AssignmentRow | undefined): boolean {
  return row != null && row.status === "ASSIGNED" && row.categoryId != null;
}

/**
 * Pure geometry for the clustering dendrogram: leaves sit in a row along the bottom in input
 * order, each internal node is centred over its children and raised above the leaf row by its
 * merge height on a linear distance axis (the root sits highest). Tone is derived from the
 * assignment queue. Deterministic; no physics, no measurement, no React.
 */
export function layoutDendrogram(
  nodes: DendrogramNode[],
  assignments: AssignmentRow[],
  size: { width: number; height: number },
): DendroLayout {
  const leaves = nodes.filter((n) => n.isLeaf);
  const leafCount = leaves.length;
  const slot = Math.min(
    DENDRO_MAX_SLOT,
    Math.max(
      DENDRO_MIN_SLOT,
      (size.width - DENDRO_LEFT_PAD - DENDRO_RIGHT_PAD) / Math.max(1, leafCount),
    ),
  );
  const axisHeight = Math.max(DENDRO_MIN_AXIS, size.height - DENDRO_TOP_PAD - DENDRO_BOTTOM_PAD);
  const leafY = DENDRO_TOP_PAD + axisHeight;
  const width = DENDRO_LEFT_PAD + leafCount * slot + DENDRO_RIGHT_PAD;

  const byId = new Map(nodes.map((n) => [n.id, n]));
  const byKey = new Map(assignments.map((a) => [a.productNameKey, a]));

  const children = new Map<string, DendrogramNode[]>();
  for (const n of nodes) {
    if (n.parentId == null || !byId.has(n.parentId)) continue;
    const list = children.get(n.parentId) ?? [];
    list.push(n);
    children.set(n.parentId, list);
  }

  const maxHeight = nodes.reduce(
    (m, n) => (!n.isLeaf && n.mergeHeight > m ? n.mergeHeight : m),
    0,
  );
  const yOf = (n: DendrogramNode): number => {
    if (n.isLeaf || maxHeight <= 0) return leafY;
    return DENDRO_TOP_PAD + axisHeight * (1 - n.mergeHeight / maxHeight);
  };

  const leafX = new Map<string, number>();
  leaves.forEach((leaf, i) => leafX.set(leaf.id, DENDRO_LEFT_PAD + (i + 0.5) * slot));

  const out: DendroLayoutNode[] = [];
  const edges: DendroLayoutEdge[] = [];
  const subtree = new Map<string, Subtree>();

  // Post-order over the parent→child structure: a node's x and tone follow from its leaves, so
  // children must be resolved before parents. A visited guard keeps a malformed cycle from looping.
  const visit = (id: string): Subtree => {
    const cached = subtree.get(id);
    if (cached) return cached;
    const node = byId.get(id);
    if (!node) {
      // A parent link pointing at a node absent from the input: treat as an empty, quiet leaf.
      return { total: 1, settled: 0, unassigned: 1, categoryIds: new Set(), minX: DENDRO_LEFT_PAD, maxX: DENDRO_LEFT_PAD };
    }
    const kids = children.get(id) ?? [];
    if (node.isLeaf || kids.length === 0) {
      const cx = leafX.get(id) ?? DENDRO_LEFT_PAD;
      const row = node.isLeaf ? byKey.get(node.productNameKey ?? "") : undefined;
      const result: Subtree = {
        total: 1,
        settled: isSettled(row) ? 1 : 0,
        unassigned: row == null || row.categoryId == null ? 1 : 0,
        categoryIds: new Set(row?.categoryId ? [row.categoryId] : []),
        minX: cx,
        maxX: cx,
      };
      out.push({
        id,
        parentId: node.parentId,
        isLeaf: node.isLeaf,
        productNameKey: node.productNameKey,
        size: node.size,
        mergeHeight: node.mergeHeight,
        x: cx,
        y: yOf(node),
        tone: node.isLeaf ? dendroToneForLeaf(row) : "missing",
      });
      subtree.set(id, result);
      return result;
    }

    let total = 0;
    let settled = 0;
    let unassigned = 0;
    let minX = Infinity;
    let maxX = -Infinity;
    const categoryIds = new Set<string>();
    for (const kid of kids) {
      const ki = visit(kid.id);
      total += ki.total;
      settled += ki.settled;
      unassigned += ki.unassigned;
      minX = Math.min(minX, ki.minX);
      maxX = Math.max(maxX, ki.maxX);
      for (const c of ki.categoryIds) categoryIds.add(c);
    }

    const tone: DendroTone =
      settled === total && categoryIds.size === 1
        ? "settled"
        : unassigned === total
          ? "missing"
          : "mixed";
    const result: Subtree = { total, settled, unassigned, categoryIds, minX, maxX };
    out.push({
      id,
      parentId: node.parentId,
      isLeaf: false,
      productNameKey: null,
      size: node.size,
      mergeHeight: node.mergeHeight,
      x: (minX + maxX) / 2,
      y: yOf(node),
      tone,
    });
    subtree.set(id, result);
    return result;
  };

  // Roots: any node whose parent is absent from the input.
  for (const n of nodes) {
    if (n.parentId == null || !byId.has(n.parentId)) visit(n.id);
  }

  for (const n of nodes) {
    if (n.parentId != null && byId.has(n.parentId)) {
      edges.push({ id: `${n.id}->${n.parentId}`, source: n.id, target: n.parentId });
    }
  }

  return {
    nodes: out,
    edges,
    width,
    height: leafY + DENDRO_BOTTOM_PAD,
    axisHeight,
    leafY,
    maxHeight,
    slot,
  };
}
