import { describe, it, expect } from "vitest";
import { Box } from "lucide-react";
import { layoutTree, layoutDendrogram } from "./layout";
import type { DendrogramInputNode, TreeNode } from "./layout";

const treeNode = (id: string, children?: TreeNode[]): TreeNode => ({
  id,
  data: { title: id, icon: Box, tone: "neutral" },
  children,
});

const dendro = (n: Partial<DendrogramInputNode> & Pick<DendrogramInputNode, "id">): DendrogramInputNode => ({
  parentId: null,
  isLeaf: false,
  label: n.id,
  height: 0,
  size: 1,
  ...n,
});

describe("layoutTree", () => {
  it("tree_places_flat_category_at_root", () => {
    const { nodes, edges } = layoutTree([treeNode("Coffee")]);

    expect(nodes).toHaveLength(1);
    expect(edges).toHaveLength(0);
    expect(nodes[0].position).toEqual({ x: 0, y: 0 });
    expect(nodes[0].data.hasInput).toBe(false);
    expect(nodes[0].data.hasOutput).toBe(false);
  });

  it("tree_nests_group_then_category", () => {
    const roots = [treeNode("Drinks", [treeNode("Cocktails")])];
    const { nodes, edges } = layoutTree(roots);

    const drinks = nodes.find((n) => n.id === "Drinks")!;
    const cocktails = nodes.find((n) => n.id === "Cocktails")!;

    expect(edges.some((e) => e.source === "Drinks" && e.target === "Cocktails")).toBe(true);
    // Child sits below the parent in the default top-down layout.
    expect(cocktails.position.y).toBeGreaterThan(drinks.position.y);
    // A single child is centred under its parent.
    expect(cocktails.position.x).toBe(drinks.position.x);
    expect(drinks.data.hasOutput).toBe(true);
    expect(cocktails.data.hasInput).toBe(true);
  });
});

describe("layoutDendrogram", () => {
  const input = (): DendrogramInputNode[] => [
    dendro({ id: "a", parentId: "m1", isLeaf: true }),
    dendro({ id: "b", parentId: "m1", isLeaf: true }),
    dendro({ id: "c", parentId: "m2", isLeaf: true }),
    dendro({ id: "d", parentId: "m2", isLeaf: true }),
    dendro({ id: "m1", parentId: "root", height: 2 }),
    dendro({ id: "m2", parentId: "root", height: 3 }),
    dendro({ id: "root", height: 5 }),
  ];

  it("dendrogram_leaves_share_a_row", () => {
    const { nodes } = layoutDendrogram(input());
    const leaves = nodes.filter((n) => input().some((i) => i.id === n.id && i.isLeaf));
    const ys = new Set(leaves.map((n) => n.position.y));
    expect(ys.size).toBe(1);

    const a = nodes.find((n) => n.id === "a")!;
    const m1 = nodes.find((n) => n.id === "m1")!;
    const root = nodes.find((n) => n.id === "root")!;
    // Internal nodes sit above the leaf row, and the root above its merges.
    expect(m1.position.y).toBeLessThan(a.position.y);
    expect(root.position.y).toBeLessThan(m1.position.y);
  });

  it("dendrogram_is_deterministic", () => {
    const first = layoutDendrogram(input());
    const second = layoutDendrogram(input());

    const positions = (r: ReturnType<typeof layoutDendrogram>) =>
      r.nodes.map((n) => [n.id, n.position.x, n.position.y]);
    expect(positions(first)).toEqual(positions(second));
    expect(first.edges.map((e) => [e.source, e.target])).toEqual(
      second.edges.map((e) => [e.source, e.target]),
    );
  });
});
