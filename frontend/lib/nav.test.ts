import { describe, it, expect } from "vitest";
import { findNav, NAV_GROUPS } from "./nav";

describe("findNav", () => {
  it("names pages the same way as the sidebar, with their section", () => {
    expect(findNav("/dashboard")).toMatchObject({ group: "Business", item: { title: "Overview" } });
    expect(findNav("/reconciliation")).toMatchObject({ group: "Data", item: { title: "Reconciliation" } });
  });
  it("resolves the Sales detail page to the Business group", () => {
    expect(findNav("/sales-detail")).toMatchObject({
      group: "Business",
      item: { title: "Sales detail", href: "/sales-detail" },
    });
  });
  it("lists Sales detail directly after Sales in the Business group", () => {
    const titles = NAV_GROUPS.find((g) => g.label === "Business")?.items.map((i) => i.title) ?? [];
    expect(titles.indexOf("Sales detail")).toBe(titles.indexOf("Sales") + 1);
  });
  it("does not prefix-match a different page", () => {
    // "/dashboards" must not resolve to Overview ("/dashboard").
    expect(findNav("/dashboards")?.item.title).toBe("Custom dashboards");
  });
  it("returns null for unknown paths", () => {
    expect(findNav("/nope")).toBeNull();
  });
});
