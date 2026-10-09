import { describe, it, expect } from "vitest";
import { findNav } from "./nav";

describe("findNav", () => {
  it("names pages the same way as the sidebar, with their section", () => {
    expect(findNav("/dashboard")).toMatchObject({ group: "Business", item: { title: "Overview" } });
    expect(findNav("/reconciliation")).toMatchObject({ group: "Data", item: { title: "Reconciliation" } });
  });
  it("does not prefix-match a different page", () => {
    // "/dashboards" must not resolve to Overview ("/dashboard").
    expect(findNav("/dashboards")?.item.title).toBe("Custom dashboards");
  });
  it("returns null for unknown paths", () => {
    expect(findNav("/nope")).toBeNull();
  });
});
