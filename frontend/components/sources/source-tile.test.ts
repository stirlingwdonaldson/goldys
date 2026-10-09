import { describe, it, expect } from "vitest";
import { sourceStyle } from "./source-tile";

describe("sourceStyle", () => {
  it("recognises each Phase 1 source however it is spelled", () => {
    expect(sourceStyle("LIGHTSPEED").initials).toBe("LS");
    expect(sourceStyle("CTB").label).toBe("Cooking the Books");
    expect(sourceStyle("Cooking the Books").initials).toBe("CB");
    expect(sourceStyle("opentable").label).toBe("OpenTable");
    expect(sourceStyle("deputy").initials).toBe("DP");
  });
  it("falls back to initials and a neutral tile for unknown sources", () => {
    const s = sourceStyle("Square POS");
    expect(s.initials).toBe("SP");
    expect(s.className).toContain("bg-muted");
  });
});
