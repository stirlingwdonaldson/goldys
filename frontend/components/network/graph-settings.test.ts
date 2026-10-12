import { afterEach, describe, expect, it, vi } from "vitest";
import {
  DEFAULT_SETTINGS,
  d3Forces,
  labelAlpha,
  nodeRadius,
  readSettings,
  writeSettings,
} from "./graph-settings";

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
});

describe("nodeRadius", () => {
  it("grows with connections and scales with the node size setting", () => {
    expect(nodeRadius(9, 3, 1)).toBeGreaterThan(nodeRadius(1, 3, 1));
    expect(nodeRadius(4, 3, 2)).toBeCloseTo(nodeRadius(4, 3, 1) * 2);
  });

  it("treats a negative degree as zero", () => {
    expect(nodeRadius(-3, 0, 1)).toBe(nodeRadius(0, 0, 1));
  });
});

describe("labelAlpha", () => {
  it("fades labels in as you zoom", () => {
    expect(labelAlpha(1.2, 0)).toBe(0);
    expect(labelAlpha(1.7, 0)).toBeCloseTo(0.5);
    expect(labelAlpha(2.4, 0)).toBe(1);
  });

  it("shows labels earlier at a lower threshold", () => {
    expect(labelAlpha(1.4, -1)).toBe(1);
    expect(labelAlpha(1.4, 1)).toBe(0);
  });
});

describe("d3Forces", () => {
  it("turns repel into a negative charge", () => {
    expect(d3Forces(DEFAULT_SETTINGS.forces)).toEqual({
      charge: -60,
      linkStrength: 0.6,
      linkDistance: 40,
      centerStrength: 0.5,
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
