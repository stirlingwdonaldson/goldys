import { describe, it, expect } from "vitest";
import { summarizeRule, ruleDetail, buildRuleRows, entityLabel, fieldLabel } from "./rule-logic";
import type { ResolutionRule } from "./rule-logic";

function rule(overrides: Partial<ResolutionRule> = {}): ResolutionRule {
  return {
    id: "rule-1",
    entityType: "daily_sales",
    fieldKey: "daily_sales",
    strategy: "priority",
    sourcePriority: ["Cooking the Books", "Lightspeed"],
    updatedAt: "2026-09-29T18:00:00Z",
    updatedBy: "Stirling Donaldson",
    ...overrides,
  };
}

describe("entityLabel", () => {
  it("maps the two real entity types to human labels", () => {
    expect(entityLabel("daily_sales")).toBe("Daily sales");
    expect(entityLabel("product_sales")).toBe("Product sales");
  });
});

describe("fieldLabel", () => {
  it("labels the daily total and the product catch-all", () => {
    expect(fieldLabel("daily_sales", "daily_sales")).toBe("Daily sales total");
    expect(fieldLabel("product_sales", "*")).toBe("All products");
  });

  it("passes a product name through unchanged", () => {
    expect(fieldLabel("product_sales", "garlic aioli")).toBe("garlic aioli");
  });
});

describe("summarizeRule", () => {
  it("summarizes a priority rule as '<first source> wins'", () => {
    expect(summarizeRule(rule())).toBe("Cooking the Books wins");
  });

  it("summarizes a manual rule distinctly", () => {
    expect(summarizeRule(rule({ strategy: "manual", sourcePriority: undefined }))).toBe(
      "Manual override — always ask",
    );
  });

  it("maps every custom logic value to a distinct label", () => {
    const base = { strategy: "custom" as const, sourcePriority: undefined };
    expect(summarizeRule(rule({ ...base, customLogic: "flag" }))).toBe("Flag unresolved");
    expect(summarizeRule(rule({ ...base, customLogic: "highest" }))).toBe("Pick highest");
    expect(summarizeRule(rule({ ...base, customLogic: "lowest" }))).toBe("Pick lowest");
    expect(summarizeRule(rule({ ...base, customLogic: "newest" }))).toBe("Pick newest");
  });
});

describe("ruleDetail", () => {
  it("shows the full priority order for a priority rule", () => {
    expect(ruleDetail(rule())).toBe("priority: Cooking the Books > Lightspeed");
  });

  it("returns an empty string for non-priority rules", () => {
    expect(ruleDetail(rule({ strategy: "manual", sourcePriority: undefined }))).toBe("");
  });
});

describe("buildRuleRows", () => {
  it("emits one row per known field, marking unruly fields unresolved", () => {
    const rules = [rule()]; // only daily_sales.daily_sales has a rule
    const known = { daily_sales: ["daily_sales"], product_sales: ["*", "garlic aioli"] };
    const rows = buildRuleRows(rules, known);
    expect(rows).toHaveLength(3);
    const resolved = rows.find((r) => r.fieldKey === "daily_sales");
    const unresolved = rows.find((r) => r.fieldKey === "*");
    expect(resolved?.rule).not.toBeNull();
    expect(unresolved?.rule).toBeNull();
  });

  it("groups rows by entity and sorts fields alphabetically", () => {
    const rules = [rule()];
    const known = { product_sales: ["*", "garlic aioli"], daily_sales: ["daily_sales"] };
    const rows = buildRuleRows(rules, known);
    expect(rows.map((r) => `${r.entityType}:${r.fieldKey}`)).toEqual([
      "daily_sales:daily_sales",
      "product_sales:*",
      "product_sales:garlic aioli",
    ]);
  });
});
