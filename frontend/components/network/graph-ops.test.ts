import { describe, expect, it } from "vitest";
import {
  filterKinds,
  neighbourhood,
  neighboursOf,
  positionCacheKey,
  seededRandom,
} from "./graph-ops";
import type { NetworkGraph, NetworkKind, NetworkNode } from "./types";

const node = (id: string, kind: NetworkKind = "metric"): NetworkNode => ({
  id,
  kind,
  label: id.toUpperCase(),
  tone: "neutral",
  size: 3,
});

// a–b, b–c, a–d
const graph: NetworkGraph = {
  nodes: [node("a"), node("b"), node("c"), node("d", "dashboard")],
  edges: [
    { source: "a", target: "b", weight: 0.5 },
    { source: "b", target: "c", weight: 1 },
    { source: "d", target: "a", weight: 0.9 },
  ],
};

const ids = (g: NetworkGraph) => g.nodes.map((n) => n.id).sort();

describe("neighbourhood", () => {
  it("returns only the focused node at depth 0", () => {
    const local = neighbourhood(graph, "a", 0);
    expect(ids(local)).toEqual(["a"]);
    expect(local.edges).toEqual([]);
  });

  it("follows edges in both directions and keeps only edges between kept nodes", () => {
    const local = neighbourhood(graph, "a", 1);
    expect(ids(local)).toEqual(["a", "b", "d"]);
    expect(local.edges.map((e) => `${e.source}-${e.target}`).sort()).toEqual(["a-b", "d-a"]);
  });

  it("reaches two hops at depth 2", () => {
    const local = neighbourhood(graph, "a", 2);
    expect(ids(local)).toEqual(["a", "b", "c", "d"]);
    expect(local.edges).toHaveLength(3);
  });

  it("returns an empty graph for an unknown id", () => {
    expect(neighbourhood(graph, "missing", 2)).toEqual({ nodes: [], edges: [] });
  });
});

describe("neighboursOf", () => {
  it("lists neighbours by edge weight, strongest first", () => {
    expect(neighboursOf(graph, "a").map((n) => n.node.id)).toEqual(["d", "b"]);
  });
});

describe("filterKinds", () => {
  it("drops hidden kinds and their dangling edges", () => {
    const filtered = filterKinds(graph, new Set<NetworkKind>(["metric"]));
    expect(ids(filtered)).toEqual(["a", "b", "c"]);
    expect(filtered.edges.map((e) => `${e.source}-${e.target}`)).toEqual(["a-b", "b-c"]);
  });
});

describe("seededRandom", () => {
  it("is reproducible and stays in [0, 1)", () => {
    const first = seededRandom(42);
    const second = seededRandom(42);
    const a = Array.from({ length: 5 }, first);
    const b = Array.from({ length: 5 }, second);
    expect(a).toEqual(b);
    for (const value of a) {
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThan(1);
    }
  });

  it("does not stall on a zero seed", () => {
    const random = seededRandom(0);
    expect(random()).not.toBe(random());
  });
});

describe("positionCacheKey", () => {
  it("ignores node order but changes when the node set changes", () => {
    const reordered: NetworkGraph = { ...graph, nodes: [...graph.nodes].reverse() };
    expect(positionCacheKey("platform", reordered)).toBe(positionCacheKey("platform", graph));
    const grown: NetworkGraph = { ...graph, nodes: [...graph.nodes, node("e")] };
    expect(positionCacheKey("platform", grown)).not.toBe(positionCacheKey("platform", graph));
  });
});
