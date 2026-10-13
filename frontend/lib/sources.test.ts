import { describe, it, expect } from "vitest";
import { connectorDisplayName, isLegacyCsvDrop, sourceIdentity, uploadTargetFor } from "./sources";

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

describe("connectorDisplayName", () => {
  it("names the four OpenTable exports distinctly", () => {
    expect(connectorDisplayName("OPENTABLE", "opentable-reservations")).toBe("OpenTable reservations");
    expect(connectorDisplayName("OPENTABLE", "opentable-reviews")).toBe("OpenTable reviews");
    expect(connectorDisplayName("OPENTABLE", "opentable-menu-items")).toBe("OpenTable menu");
    expect(connectorDisplayName("OPENTABLE", "opentable-csv-drop")).toBe("OpenTable legacy CSV");
  });
  it("falls back to the source label for non-OpenTable sources", () => {
    expect(connectorDisplayName("CTB", "ctb-revenue")).toBe("Cooking the Books");
    expect(connectorDisplayName("DEPUTY", "deputy-api")).toBe("Deputy");
  });
});

describe("uploadTargetFor / isLegacyCsvDrop", () => {
  it("marks only the three rich exports as upload targets", () => {
    expect(uploadTargetFor("opentable-reservations")).toBe("opentable-reservations");
    expect(uploadTargetFor("opentable-reviews")).toBe("opentable-reviews");
    expect(uploadTargetFor("opentable-menu-items")).toBe("opentable-menu-items");
    expect(uploadTargetFor("opentable-csv-drop")).toBeNull();
    expect(uploadTargetFor("ctb-revenue")).toBeNull();
  });
  it("recognises the deprecated thin CSV drop", () => {
    expect(isLegacyCsvDrop("opentable-csv-drop")).toBe(true);
    expect(isLegacyCsvDrop("opentable-reservations")).toBe(false);
  });
});
