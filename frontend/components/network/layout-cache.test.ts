import { afterEach, describe, expect, it, vi } from "vitest";
import { initialPositions, readPositions, writePositions } from "./layout-cache";
import type { NetworkGraph } from "./types";

const graph: NetworkGraph = {
  nodes: ["a", "b", "c"].map((id) => ({ id, kind: "metric", label: id, tone: "neutral", size: 3 })),
  edges: [],
};

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
});

describe("layout cache", () => {
  it("round-trips positions for the same node set", () => {
    writePositions("test", graph, { a: { x: 1, y: 2 } });
    expect(readPositions("test", graph)).toEqual({ a: { x: 1, y: 2 } });
  });

  it("returns undefined instead of throwing when storage is unavailable", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(readPositions("test", graph)).toBeUndefined();
  });

  it("swallows write failures", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("quota");
    });
    expect(() => writePositions("test", graph, {})).not.toThrow();
  });
});

describe("initialPositions", () => {
  it("is deterministic and prefers cached positions", () => {
    const first = initialPositions(graph, undefined);
    expect(initialPositions(graph, undefined)).toEqual(first);
    expect(initialPositions(graph, { b: { x: 7, y: 8 } }).b).toEqual({ x: 7, y: 8 });
  });

  it("gives every node a distinct start", () => {
    const positions = Object.values(initialPositions(graph, undefined)).map((p) => `${p.x},${p.y}`);
    expect(new Set(positions).size).toBe(3);
  });
});
