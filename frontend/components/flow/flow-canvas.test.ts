import { describe, it, expect, vi } from "vitest";
import { resolveNodeClick } from "./flow-canvas";
import type { StepNodeData } from "./types";

const data = (overrides: Partial<StepNodeData> = {}): StepNodeData => ({
  title: "Node",
  icon: () => null,
  tone: "neutral",
  ...overrides,
});

describe("resolveNodeClick", () => {
  it("prefers drill over navigation when both are present", () => {
    const onDrill = vi.fn();
    const navigate = vi.fn();
    resolveNodeClick(data({ drill: "supplier:X", href: "/somewhere" }), onDrill, navigate);
    expect(onDrill).toHaveBeenCalledWith("supplier:X");
    expect(navigate).not.toHaveBeenCalled();
  });

  it("navigates when there is no drill", () => {
    const navigate = vi.fn();
    resolveNodeClick(data({ href: "/somewhere" }), vi.fn(), navigate);
    expect(navigate).toHaveBeenCalledWith("/somewhere");
  });

  it("falls back to href navigation when a drill is set but no onDrill handler is provided", () => {
    const navigate = vi.fn();
    resolveNodeClick(data({ drill: "supplier:X", href: "/somewhere" }), undefined, navigate);
    expect(navigate).toHaveBeenCalledWith("/somewhere");
  });
});
