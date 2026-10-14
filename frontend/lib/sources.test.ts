import { describe, it, expect } from "vitest";
import { connectorDisplayName, groupConnectors, isLegacyCsvDrop, sourceIdentity, uploadTargetFor } from "./sources";
import type { ConnectorStatus } from "@/lib/api";

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

function connector(overrides: Partial<ConnectorStatus> & { source: string; connectorName: string }): ConnectorStatus {
  return { lastRunAt: null, status: "never_run", failureCount: 0, failure: null, runnable: false, ...overrides };
}

describe("groupConnectors", () => {
  it("collapses each source to one group and drops the legacy CSV drop", () => {
    const groups = groupConnectors([
      connector({ source: "LIGHTSPEED", connectorName: "lightspeed-insights", status: "success" }),
      connector({ source: "LIGHTSPEED", connectorName: "lightspeed-product-catalog", status: "success" }),
      connector({ source: "OPENTABLE", connectorName: "opentable-csv-drop" }),
      connector({ source: "OPENTABLE", connectorName: "opentable-reservations" }),
      connector({ source: "OPENTABLE", connectorName: "opentable-reviews" }),
      connector({ source: "OPENTABLE", connectorName: "opentable-menu-items" }),
      connector({ source: "CTB", connectorName: "ctb-revenue" }),
    ]);

    expect(groups.map((g) => g.source)).toEqual(["LIGHTSPEED", "OPENTABLE", "CTB"]);
    expect(groups.find((g) => g.source === "LIGHTSPEED")!.connectors).toHaveLength(2);
    // The deprecated thin drop is dropped, leaving the three rich exports.
    expect(groups.find((g) => g.source === "OPENTABLE")!.connectors).toHaveLength(3);
  });

  it("aggregates status to the worst member and takes the latest run", () => {
    const groups = groupConnectors([
      connector({ source: "LIGHTSPEED", connectorName: "a", status: "success", lastRunAt: "2026-10-01T00:00:00Z" }),
      connector({ source: "LIGHTSPEED", connectorName: "b", status: "failed", lastRunAt: "2026-10-03T00:00:00Z" }),
    ]);

    expect(groups[0].status).toBe("failed");
    expect(groups[0].lastRunAt).toBe("2026-10-03T00:00:00Z");
  });

  it("flags OpenTable as syncable and any runnable member as runnable", () => {
    const opentable = groupConnectors([
      connector({ source: "OPENTABLE", connectorName: "opentable-reservations" }),
    ])[0];
    expect(opentable.syncable).toBe(true);
    expect(opentable.runnable).toBe(false);

    const ctb = groupConnectors([
      connector({ source: "CTB", connectorName: "ctb-revenue", runnable: true }),
    ])[0];
    expect(ctb.syncable).toBe(false);
    expect(ctb.runnable).toBe(true);
  });
});
