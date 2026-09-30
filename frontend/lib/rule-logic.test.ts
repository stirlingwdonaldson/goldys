import { describe, it, expect } from "vitest";
import { summarizeRule, ruleDetail, buildRuleRows } from "./rule-logic";
import type { ResolutionRule } from "./rule-logic";

function rule(overrides: Partial<ResolutionRule> = {}): ResolutionRule {
  return {
    id: "rule-1",
    entity: "Sales",
    field: "quantity_sold",
    strategy: "priority",
    sourcePriority: ["Cooking the Books", "Lightspeed"],
    updatedAt: "2026-09-29T18:00:00Z",
    updatedBy: "Stirling Donaldson",
    ...overrides,
  };
}

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
    const rules = [rule()]; // only Sales.quantity_sold has a rule
    const known = { Sales: ["quantity_sold", "net_amount"] };
    const rows = buildRuleRows(rules, known);
    expect(rows).toHaveLength(2);
    const resolved = rows.find((r) => r.field === "quantity_sold");
    const unresolved = rows.find((r) => r.field === "net_amount");
    expect(resolved?.rule).not.toBeNull();
    expect(unresolved?.rule).toBeNull();
  });

  it("groups rows by entity and sorts fields alphabetically", () => {
    const rules = [rule()];
    const known = { Sales: ["net_amount", "quantity_sold"], Shifts: ["hours_worked"] };
    const rows = buildRuleRows(rules, known);
    expect(rows.map((r) => `${r.entity}:${r.field}`)).toEqual([
      "Sales:net_amount",
      "Sales:quantity_sold",
      "Shifts:hours_worked",
    ]);
  });
});
