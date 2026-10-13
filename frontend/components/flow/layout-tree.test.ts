import { describe, it, expect } from "vitest";
import { Box } from "lucide-react";
import { layoutTree } from "./layout";
import type { TreeNode } from "./layout";

const treeNode = (id: string, children?: TreeNode[]): TreeNode => ({
  id,
  data: { title: id, icon: Box, tone: "neutral" },
  children,
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

