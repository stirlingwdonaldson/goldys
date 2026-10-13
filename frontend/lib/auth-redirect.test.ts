import { describe, expect, it } from "vitest";
import { loginHref, nextPath } from "./auth-redirect";

describe("loginHref", () => {
  it("remembers the requested page", () => {
    expect(loginHref("/sales-detail")).toBe("/login?next=%2Fsales-detail");
  });
  it("adds nothing for the root or the login page itself", () => {
    expect(loginHref("/")).toBe("/login");
    expect(loginHref("/login")).toBe("/login");
    expect(loginHref(null)).toBe("/login");
  });
});

describe("nextPath", () => {
  it("returns a same-origin app path", () => {
    expect(nextPath("?next=%2Fsales-detail")).toBe("/sales-detail");
  });
  it.each(["", "?next=https%3A%2F%2Fevil.example", "?next=%2F%2Fevil.example", "?next=%2F%5Cevil.example"])(
    "falls back to the dashboard for %j",
    (search) => {
      expect(nextPath(search)).toBe("/dashboard");
    },
  );
});
