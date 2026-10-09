import { describe, it, expect } from "vitest";
import type { Provenance } from "@/lib/api/types";
import { buildProvenanceGraph } from "./provenance-graph";

const provenance = (overrides: Partial<Provenance> = {}): Provenance => ({
  metric: "sales.gross",
  date: "2026-10-05",
  resolvedValue: 1000,
  trust: {
    state: "RESOLVED_BY_RULE",
    freshness: "FRESH",
    authoritativeSource: "LIGHTSPEED",
    resolvedAt: null,
    lastIngestionAt: null,
    threshold: null,
  },
  sources: [
    { sourceSystem: "LIGHTSPEED", value: 1000, recordedAt: null },
    { sourceSystem: "CTB", value: 960, recordedAt: null },
  ],
  resolution: { kind: "rule", source: "LIGHTSPEED", reason: "priority", actor: null, at: null },
  rawRecordIds: ["raw-1"],
  ...overrides,
});

describe("buildProvenanceGraph", () => {
  it("draws sources → resolution → resolved value", () => {
    const g = buildProvenanceGraph(provenance(), "Gross sales");
    expect(g.columns.map((c) => c.map((n) => n.id))).toEqual([
      ["src:LIGHTSPEED", "src:CTB"],
      ["resolve"],
      ["result"],
    ]);
    expect(g.columns[1][0].data.title).toBe("Standing rule");
    expect(g.columns[1][0].data.subtitle).toBe("Source priority");
    expect(g.columns[0][1].data.title).toBe("Cooking the Books");
    expect(g.columns[2][0].data.title).toBe("Gross sales");
    expect(g.columns[2][0].data.detail).toBe("Resolved by rule");
  });

  it("highlights the chosen source's edge and flags the disagreeing source", () => {
    const g = buildProvenanceGraph(provenance(), "Gross sales");
    expect(g.edges.find((e) => e.source === "src:LIGHTSPEED")?.tone).toBe("info");
    expect(g.edges.find((e) => e.source === "src:CTB")?.tone).toBe("neutral");
    const ctb = g.columns[0][1];
    expect(ctb.data.tone).toBe("warn");
    expect(ctb.data.detail).toBe("Disagrees with result");
  });

  it("shows a source with no value as 'No data', never as zero", () => {
    const g = buildProvenanceGraph(
      provenance({
        sources: [
          { sourceSystem: "LIGHTSPEED", value: 1000, recordedAt: null },
          { sourceSystem: "CTB", value: null, recordedAt: null },
        ],
        resolution: { kind: "single", source: "LIGHTSPEED", reason: null, actor: null, at: null },
      }),
      "Gross sales",
    );
    const ctb = g.columns[0][1];
    expect(ctb.data.subtitle).toBe("No data");
    expect(ctb.data.tone).toBe("missing");
    expect(g.edges.find((e) => e.source === "src:CTB")?.tone).toBe("missing");
  });

  it("treats an unresolved conflict as a resolvable warning and links to reconciliation", () => {
    const g = buildProvenanceGraph(
      provenance({
        resolvedValue: null,
        resolution: { kind: "conflict", source: null, reason: null, actor: null, at: null },
      }),
      "Gross sales",
    );
    const resolve = g.columns[1][0];
    expect(resolve.data.tone).toBe("warn");
    expect(resolve.data.href).toBe("/reconciliation");
    expect(g.columns[2][0].data.subtitle).toBe("No value");
  });
});
