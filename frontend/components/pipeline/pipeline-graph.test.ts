import { describe, it, expect } from "vitest";
import type { ConnectorStatus } from "@/lib/api/types";
import { buildPipelineGraph, type PipelineInput } from "./pipeline-graph";

const connector = (source: string, status: ConnectorStatus["status"]): ConnectorStatus => ({
  source,
  connectorName: `${source.toLowerCase()}-connector`,
  lastRunAt: null,
  status,
  failureCount: 0,
  runnable: true,
});

const input = (overrides: Partial<PipelineInput> = {}): PipelineInput => ({
  connectors: [connector("CTB", "success"), connector("DEPUTY", "failed"), connector("OPENTABLE", "never_run")],
  rawCountBySource: { CTB: 120, DEPUTY: 4, OPENTABLE: 0 },
  rawTotal: 124,
  canonical: [
    { id: "daily_sales", label: "Daily sales", placeholder: false, count: 30 },
    { id: "shift", label: "Shifts", placeholder: true, count: 0 },
  ],
  resolved: [{ id: "resolved_daily_sales", label: "Daily sales", placeholder: false, count: 30 }],
  ruleCount: 2,
  ...overrides,
});

describe("buildPipelineGraph", () => {
  it("lays the three-layer model out as five stages", () => {
    const g = buildPipelineGraph(input());
    expect(g.columns.map((c) => c.map((n) => n.id))).toEqual([
      ["src:CTB", "src:DEPUTY", "src:OPENTABLE"],
      ["raw"],
      ["can:daily_sales", "can:shift"],
      ["resolve"],
      ["res:resolved_daily_sales"],
    ]);
  });

  it("maps connector status to tone and labels source edges with record counts", () => {
    const g = buildPipelineGraph(input());
    const tone = (id: string) => g.columns.flat().find((n) => n.id === id)?.data.tone;
    expect(tone("src:CTB")).toBe("ok");
    expect(tone("src:DEPUTY")).toBe("fail");
    expect(tone("src:OPENTABLE")).toBe("missing");

    const edge = (s: string) => g.edges.find((e) => e.source === s && e.target === "raw");
    expect(edge("src:CTB")?.label).toBe("120 records");
    expect(edge("src:DEPUTY")?.tone).toBe("fail");
    // Nothing has flowed from a never-run source: dashed "missing" edge, not a failure.
    expect(edge("src:OPENTABLE")?.tone).toBe("missing");
  });

  it("marks placeholder entities as having no source and links them to the explorer", () => {
    const shift = buildPipelineGraph(input()).columns[2][1];
    expect(shift.data.subtitle).toBe("No source yet");
    expect(shift.data.tone).toBe("missing");
    expect(shift.data.href).toBe("/data?layer=canonical&entity=shift");
  });

  it("routes resolved domains to the screen that shows them", () => {
    const res = buildPipelineGraph(input()).columns[4][0];
    expect(res.data.href).toBe("/sales");
    expect(res.data.detail).toBe("Shown on Sales");
  });

  it("says a count is unavailable rather than showing zero when it couldn't be read", () => {
    const g = buildPipelineGraph(input({ rawTotal: null, ruleCount: null }));
    const raw = g.columns[1][0];
    expect(raw.data.subtitle).toBe("Count unavailable");
    expect(g.columns[3][0].data.subtitle).toBe("Agreement, rules, overrides");
  });
});
