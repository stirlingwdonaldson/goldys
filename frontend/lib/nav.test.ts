import { describe, it, expect } from "vitest";
import { findNav, NAV_GROUPS } from "./nav";

describe("findNav", () => {
  it("names pages the same way as the sidebar, with their section", () => {
    expect(findNav("/dashboard")).toMatchObject({ group: "Workspace", item: { title: "Home" } });
    expect(findNav("/my-work")).toMatchObject({ group: "Workspace", item: { title: "My work" } });
    expect(findNav("/reconciliation")).toMatchObject({ group: "Workspace", item: { title: "Reconciliation" } });
    expect(findNav("/data-health")).toMatchObject({ group: "Administration", item: { title: "Data health" } });
    expect(findNav("/flow-lab")).toMatchObject({ group: "Intelligence", item: { title: "Flow lab" } });
  });
  it("resolves the Sales detail page to the Performance group", () => {
    expect(findNav("/sales-detail")).toMatchObject({
      group: "Performance",
      item: { title: "Sales detail", href: "/sales-detail" },
    });
  });
  it("lists Sales detail directly after Sales in the Performance group", () => {
    const titles = NAV_GROUPS.find((g) => g.label === "Performance")?.items.map((i) => i.title) ?? [];
    expect(titles.indexOf("Sales detail")).toBe(titles.indexOf("Sales") + 1);
  });
  it("does not prefix-match a different page", () => {
    // "/dashboards" must not resolve to Home ("/dashboard").
    expect(findNav("/dashboards")?.item.title).toBe("Custom dashboards");
  });
  it("returns null for unknown paths", () => {
    expect(findNav("/nope")).toBeNull();
  });
});
