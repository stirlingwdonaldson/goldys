import { describe, it, expect } from "vitest";
import { isOwner } from "./roles";

describe("isOwner", () => {
  it("matches the backend's uppercase OWNER code", () => {
    expect(isOwner("OWNER")).toBe(true);
  });

  it("normalizes other casings to OWNER", () => {
    expect(isOwner("owner")).toBe(true);
  });

  it("rejects non-owner seniority", () => {
    expect(isOwner("STAFF")).toBe(false);
    expect(isOwner("MANAGER")).toBe(false);
  });

  it("fails closed when the value is absent", () => {
    expect(isOwner(undefined)).toBe(false);
    expect(isOwner(null)).toBe(false);
  });
});
