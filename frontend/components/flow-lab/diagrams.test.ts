import { describe, expect, it } from "vitest";
import { DIAGRAMS, describeNode, findDiagram, toColumnGraph } from "./diagrams";

describe("Flow lab diagrams", () => {
  it("has fifteen diagrams with unique ids", () => {
    expect(DIAGRAMS).toHaveLength(15);
    expect(new Set(DIAGRAMS.map((d) => d.id)).size).toBe(15);
  });

  it("covers the uses the product brief asked for", () => {
    const ids = DIAGRAMS.map((d) => d.id);
    for (const id of [
      "explore-dependencies",
      "build-automations",
      "inspect-execution",
      "connector-pipeline",
      "metric-lineage",
      "entity-resolution",
      "schema-map",
    ]) {
      expect(ids).toContain(id);
    }
  });

  it.each(DIAGRAMS.map((d) => [d.id, d] as const))("%s only draws edges between its own nodes", (_, d) => {
    const ids = d.columns.flat().map((n) => n.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const edge of d.edges) {
      expect(ids).toContain(edge.source);
      expect(ids).toContain(edge.target);
    }
  });

  it("makes every node drill into the inspector", () => {
    const graph = toColumnGraph(DIAGRAMS[0]);
    expect(graph.columns.flat().every((n) => n.data.drill === n.id)).toBe(true);
  });

  it("describes a node's neighbours", () => {
    const info = describeNode(findDiagram("build-automations"), "c");
    expect(info?.incoming.map((x) => x.node?.id)).toEqual(["d"]);
    expect(info?.outgoing.map((x) => x.label)).toEqual(["Yes", "No"]);
  });

  it("falls back to the first diagram for an unknown id", () => {
    expect(findDiagram("nope").id).toBe(DIAGRAMS[0].id);
    expect(findDiagram(null).id).toBe(DIAGRAMS[0].id);
  });
});

describe("layout orientation", () => {
  it("lays long chains top-to-bottom and short wide stages left-to-right", async () => {
    const { bestDirection } = await import("@/components/flow/layout");
    const viewport = { width: 1100, height: 560 };
    expect(bestDirection(toColumnGraph(findDiagram("schema-map")), viewport)).toBe("vertical");
    const wideStage = {
      columns: [Array.from({ length: 6 }, (_, i) => ({ id: `n${i}`, data: toColumnGraph(DIAGRAMS[0]).columns[0][0].data }))],
      edges: [],
    };
    expect(bestDirection(wideStage, viewport)).toBe("horizontal");
  });
});
