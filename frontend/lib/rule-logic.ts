export type RuleStrategy = "priority" | "manual" | "custom";
export type CustomLogic = "flag" | "highest" | "lowest" | "newest";

export interface ResolutionRule {
  id: string;
  entity: string;
  field: string;
  strategy: RuleStrategy;
  sourcePriority?: string[];
  customLogic?: CustomLogic;
  updatedAt: string;
  updatedBy: string;
}

const CUSTOM_LABEL: Record<CustomLogic, string> = {
  flag: "Flag unresolved",
  highest: "Pick highest",
  lowest: "Pick lowest",
  newest: "Pick newest",
};

/** One-line effect of a rule, shown in the rule list. */
export function summarizeRule(rule: ResolutionRule): string {
  if (rule.strategy === "priority") return `${rule.sourcePriority?.[0] ?? "First source"} wins`;
  if (rule.strategy === "manual") return "Manual override — always ask";
  return rule.customLogic ? CUSTOM_LABEL[rule.customLogic] : "Flag unresolved";
}

/** Secondary line for a rule (e.g. the full source order); empty when none applies. */
export function ruleDetail(rule: ResolutionRule): string {
  if (rule.strategy === "priority" && rule.sourcePriority && rule.sourcePriority.length > 0) {
    return `priority: ${rule.sourcePriority.join(" > ")}`;
  }
  return "";
}

export interface RuleRow {
  entity: string;
  field: string;
  rule: ResolutionRule | null; // null => unresolved (no rule)
}

/**
 * One row per known field, in entity/field order. A field present in `knownFields`
 * but absent from `rules` yields a `rule: null` (unresolved) row — absence is
 * surfaced, never omitted.
 */
export function buildRuleRows(
  rules: ResolutionRule[],
  knownFields: Record<string, string[]>,
): RuleRow[] {
  const byKey = new Map(rules.map((r) => [`${r.entity}:${r.field}`, r]));
  const rows: RuleRow[] = [];
  for (const entity of Object.keys(knownFields).sort()) {
    for (const field of [...knownFields[entity]].sort()) {
      rows.push({ entity, field, rule: byKey.get(`${entity}:${field}`) ?? null });
    }
  }
  return rows;
}
