import { describe, it, expect } from "vitest";
import { cn } from "./utils";

describe("cn", () => {
  it("keeps a text colour next to a custom font size", () => {
    expect(cn("text-primary-foreground", "text-ui")).toBe("text-primary-foreground text-ui");
    expect(cn("text-muted-foreground text-2xs")).toBe("text-muted-foreground text-2xs");
  });

  it("still lets a later font size win over an earlier one", () => {
    expect(cn("text-sm", "text-ui")).toBe("text-ui");
    expect(cn("text-ui", "text-kpi")).toBe("text-kpi");
  });
});
