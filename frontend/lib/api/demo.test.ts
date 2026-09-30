import { describe, it, expect } from "vitest";
import { demoApi } from "./demo";

describe("demoApi resolution rules", () => {
  it("applies a changed entity, field, and source priority when editing a rule", async () => {
    await demoApi.saveResolutionRule({
      id: "rule-2",
      entity: "Products",
      field: "unit_price",
      strategy: "priority",
      sourcePriority: ["Lightspeed", "Deputy"],
    });

    const rules = await demoApi.listResolutionRules();
    const edited = rules.find((r) => r.id === "rule-2");

    expect(edited?.entity).toBe("Products");
    expect(edited?.field).toBe("unit_price");
    expect(edited?.sourcePriority).toEqual(["Lightspeed", "Deputy"]);
  });
});
