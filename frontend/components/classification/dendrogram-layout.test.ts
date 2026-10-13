import { describe, it, expect } from "vitest";
import type { AssignmentRow, DendrogramNode } from "@/lib/api";
import { layoutDendrogram } from "./dendrogram-layout";

const leaf = (id: string, parentId: string, productNameKey: string): DendrogramNode => ({
  id,
  parentId,
  isLeaf: true,
  productNameKey,
  mergeHeight: 0,
  size: 1,
});

const merge = (id: string, parentId: string | null, height: number, size: number): DendrogramNode => ({
  id,
  parentId,
  isLeaf: false,
  productNameKey: null,
  mergeHeight: height,
  size,
});

const assigned = (
  productNameKey: string,
  categoryId: string,
  status = "ASSIGNED",
): AssignmentRow => ({
  productNameKey,
  categoryId,
  categoryName: categoryId,
  groupName: null,
  confidence: 0.9,
  source: "JEV",
  status,
});

describe("layoutDendrogram", () => {
  it("keeps leaves on one row and raises merges with their height", () => {
    const nodes = [
      leaf("a", "m1", "pint"),
      leaf("b", "m1", "house red"),
      leaf("c", "m2", "schnitzel"),
      leaf("d", "m2", "parma"),
      merge("m1", "root", 0.4, 2),
      merge("m2", "root", 0.6, 2),
      merge("root", null, 0.8, 4),
    ];
    const { nodes: laid } = layoutDendrogram(nodes, [], { width: 800, height: 460 });
    const byId = new Map(laid.map((n) => [n.id, n]));

    const leafYs = new Set(laid.filter((n) => n.isLeaf).map((n) => n.y));
    expect(leafYs.size).toBe(1);

    const leafY = byId.get("a")!.y;
    // Internal nodes sit above the leaf row, and the root above its merges.
    expect(byId.get("m1")!.y).toBeLessThan(leafY);
    expect(byId.get("root")!.y).toBeLessThan(byId.get("m1")!.y);
    // A later merge (0.6) is closer to the root than an earlier one (0.4).
    expect(byId.get("m1")!.y).toBeGreaterThan(byId.get("m2")!.y);
  });

  it("marks a cluster settled when every leaf shares one category", () => {
    const nodes = [
      leaf("a", "m1", "pint"),
      leaf("b", "m1", "lager"),
      merge("m1", null, 0.4, 2),
    ];
    const { nodes: laid } = layoutDendrogram(
      nodes,
      [assigned("pint", "beer"), assigned("lager", "beer")],
      { width: 800, height: 460 },
    );
    expect(laid.find((n) => n.id === "m1")!.tone).toBe("settled");
  });

  it("marks a cluster mixed when its leaves disagree on category", () => {
    const nodes = [
      leaf("a", "m1", "pint"),
      leaf("b", "m1", "house red"),
      merge("m1", null, 0.4, 2),
    ];
    const { nodes: laid } = layoutDendrogram(
      nodes,
      [assigned("pint", "beer"), assigned("house red", "red-wine")],
      { width: 800, height: 460 },
    );
    expect(laid.find((n) => n.id === "m1")!.tone).toBe("mixed");
  });

  it("marks a cluster mixed when one leaf still needs review", () => {
    const nodes = [
      leaf("c", "m2", "schnitzel"),
      leaf("d", "m2", "parma"),
      merge("m2", null, 0.6, 2),
    ];
    const { nodes: laid } = layoutDendrogram(
      nodes,
      [assigned("schnitzel", "mains"), assigned("parma", "mains", "NEEDS_REVIEW")],
      { width: 800, height: 460 },
    );
    expect(laid.find((n) => n.id === "m2")!.tone).toBe("mixed");
  });

  it("marks an untouched cluster missing", () => {
    const nodes = [leaf("a", "m1", "pint"), leaf("b", "m1", "lager"), merge("m1", null, 0.4, 2)];
    const { nodes: laid } = layoutDendrogram(nodes, [], { width: 800, height: 460 });
    expect(laid.find((n) => n.id === "m1")!.tone).toBe("missing");
  });

  it("emits one elbow edge per non-root node, child to parent", () => {
    const nodes = [leaf("a", "m1", "pint"), leaf("b", "m1", "lager"), merge("m1", null, 0.4, 2)];
    const { edges } = layoutDendrogram(nodes, [], { width: 800, height: 460 });
    expect(edges.map((e) => [e.source, e.target])).toEqual([
      ["a", "m1"],
      ["b", "m1"],
    ]);
  });

  it("is deterministic", () => {
    const nodes = [
      leaf("a", "m1", "pint"),
      leaf("b", "m1", "lager"),
      leaf("c", "m2", "schnitzel"),
      merge("m1", "root", 0.4, 2),
      merge("m2", "root", 0.6, 2),
      merge("root", null, 0.8, 4),
    ];
    const first = layoutDendrogram(nodes, [], { width: 800, height: 460 });
    const second = layoutDendrogram(nodes, [], { width: 800, height: 460 });
    expect(first).toEqual(second);
  });
});
