import { afterEach, describe, expect, it, vi } from "vitest";
import {
  DEFAULT_SETTINGS,
  fa2Settings,
  labelThreshold,
  nodeSize,
  readSettings,
  writeSettings,
} from "./graph-settings";

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
});

describe("nodeSize", () => {
  it("grows with connections and scales with the node size setting", () => {
    expect(nodeSize(9, 3, 1)).toBeGreaterThan(nodeSize(1, 3, 1));
    expect(nodeSize(4, 3, 2)).toBeCloseTo(nodeSize(4, 3, 1) * 2);
  });

  it("treats a negative degree as zero", () => {
    expect(nodeSize(-3, 0, 1)).toBe(nodeSize(0, 0, 1));
  });
});

describe("labelThreshold", () => {
  it("moves between 2px and 14px with the slider, clamped", () => {
    expect(labelThreshold(0)).toBe(8);
    expect(labelThreshold(-1)).toBe(2);
    expect(labelThreshold(1)).toBe(14);
    expect(labelThreshold(5)).toBe(14);
  });
});

describe("fa2Settings", () => {
  it("maps the sliders onto ForceAtlas2", () => {
    expect(fa2Settings(DEFAULT_SETTINGS.forces)).toMatchObject({
      gravity: 0.35,
      scalingRatio: 18,
      edgeWeightInfluence: 1,
      strongGravityMode: true,
    });
  });
});

describe("settings storage", () => {
  it("round-trips and fills gaps from the defaults", () => {
    writeSettings("t", { ...DEFAULT_SETTINGS, display: { ...DEFAULT_SETTINGS.display, nodeSize: 2 } });
    expect(readSettings("t").display.nodeSize).toBe(2);
    window.localStorage.setItem("graph-settings:t", JSON.stringify({ forces: { repel: "x", center: 0.2 } }));
    const read = readSettings("t");
    expect(read.forces.repel).toBe(DEFAULT_SETTINGS.forces.repel);
    expect(read.forces.center).toBe(0.2);
    expect(read.display).toEqual(DEFAULT_SETTINGS.display);
  });

  it("falls back to the defaults when storage throws", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(readSettings("t")).toEqual(DEFAULT_SETTINGS);
  });
});
