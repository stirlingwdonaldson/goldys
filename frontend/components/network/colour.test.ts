import { describe, expect, it } from "vitest";
import { mixColour, toRgba } from "./colour";

describe("toRgba", () => {
  it("converts design-token HSL to rgba", () => {
    expect(toRgba("hsl(0 0% 100%)")).toBe("rgba(255, 255, 255, 1)");
    expect(toRgba("hsl(0 73% 42%)")).toBe("rgba(185, 29, 29, 1)");
  });

  it("multiplies alphas", () => {
    expect(toRgba("hsl(240 6% 10% / 0.5)", 0.5)).toBe("rgba(24, 24, 27, 0.25)");
  });

  it("passes other formats through", () => {
    expect(toRgba("#8a8a8a", 0.4)).toBe("#8a8a8a");
  });
});

describe("mixColour", () => {
  it("blends toward the background and returns an opaque colour", () => {
    expect(mixColour("hsl(0 0% 0%)", "hsl(0 0% 100%)", 0.5)).toBe("rgb(128, 128, 128)");
    expect(mixColour("hsl(0 0% 0%)", "hsl(0 0% 100%)", 2)).toBe("rgb(255, 255, 255)");
  });

  it("returns the colour unchanged when it can't be parsed", () => {
    expect(mixColour("#123456", "hsl(0 0% 100%)", 0.5)).toBe("#123456");
  });
});
