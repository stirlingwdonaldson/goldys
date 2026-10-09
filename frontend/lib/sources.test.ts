import { describe, it, expect } from "vitest";
import { sourceIdentity } from "./sources";

describe("sourceIdentity", () => {
  it("recognises each Phase 1 source however it is spelled", () => {
    expect(sourceIdentity("LIGHTSPEED").initials).toBe("LS");
    expect(sourceIdentity("CTB").label).toBe("Cooking the Books");
    expect(sourceIdentity("Cooking the Books").initials).toBe("CB");
    expect(sourceIdentity("opentable").label).toBe("OpenTable");
    expect(sourceIdentity("deputy").initials).toBe("DP");
  });
  it("falls back to initials and a neutral tile for unknown sources", () => {
    const s = sourceIdentity("Square POS");
    expect(s.initials).toBe("SP");
    expect(s.className).toContain("bg-muted");
  });
});
