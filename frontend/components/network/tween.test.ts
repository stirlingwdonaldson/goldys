import { describe, expect, it } from "vitest";
import { parseColour, VisualTween } from "./tween";

describe("parseColour", () => {
  it("reads rgb, rgba and hex", () => {
    expect(parseColour("rgb(10, 20, 30)")).toEqual([10, 20, 30, 1]);
    expect(parseColour("rgba(10, 20, 30, 0.5)")).toEqual([10, 20, 30, 0.5]);
    expect(parseColour("#0a141e")).toEqual([10, 20, 30, 1]);
    expect(parseColour("hsl(0 0% 50%)")).toBeNull();
  });
});

describe("VisualTween", () => {
  const dark = { color: "rgb(0, 0, 0)", size: 10 };
  const light = { color: "rgb(200, 200, 200)", size: 4 };

  it("draws an element as-is the first time it is seen", () => {
    const tween = new VisualTween(100);
    expect(tween.resolve("a", dark, 0)).toEqual(dark);
  });

  it("eases toward a new target and lands on it after the duration", () => {
    const tween = new VisualTween(100);
    tween.resolve("a", dark, 0);
    const start = tween.resolve("a", light, 1000);
    expect(start).toEqual({ color: "rgba(0, 0, 0, 1)", size: 10 });

    const mid = tween.resolve("a", light, 1050);
    const [r] = /\d+/.exec(mid.color)!.map(Number);
    expect(r).toBeGreaterThan(0);
    expect(r).toBeLessThan(200);
    expect(mid.size).toBeLessThan(10);
    expect(mid.size).toBeGreaterThan(4);

    expect(tween.resolve("a", light, 1100)).toEqual({ color: "rgba(200, 200, 200, 1)", size: 4 });
    expect(tween.activeUntil()).toBe(1100);
  });

  it("retargets from the current position rather than jumping", () => {
    const tween = new VisualTween(100);
    tween.resolve("a", dark, 0);
    tween.resolve("a", light, 1000);
    const halfway = tween.resolve("a", light, 1050);
    // Reversed mid-fade: the first frame of the new motion is where the old one had got to.
    expect(tween.resolve("a", dark, 1050)).toEqual(halfway);
  });
});
