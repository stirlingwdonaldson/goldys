import { describe, it, expect } from "vitest";
import { demoApi } from "./demo";

describe("demoApi resolution rules", () => {
  it("applies a changed entity type, field key, and source priority when editing a rule", async () => {
    await demoApi.saveResolutionRule({
      id: "rule-2",
      entityType: "product_sales",
      fieldKey: "pint carlton draught",
      strategy: "priority",
      sourcePriority: ["Lightspeed", "Cooking the Books"],
    });

    const rules = await demoApi.listResolutionRules();
    const edited = rules.find((r) => r.id === "rule-2");

    expect(edited?.entityType).toBe("product_sales");
    expect(edited?.fieldKey).toBe("pint carlton draught");
    expect(edited?.sourcePriority).toEqual(["Lightspeed", "Cooking the Books"]);
  });
});
