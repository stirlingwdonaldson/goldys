export type RuleStrategy = "priority" | "manual" | "custom";
export type CustomLogic = "flag" | "highest" | "lowest" | "newest";

export const ENTITY_TYPES = ["daily_sales", "product_sales"] as const;
export type EntityType = (typeof ENTITY_TYPES)[number];

export interface ResolutionRule {
  id: string;
  entityType: string;
  fieldKey: string;
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

/** Human label for an entity type. */
export function entityLabel(entityType: string): string {
  switch (entityType) {
    case "daily_sales":
      return "Daily sales";
    case "product_sales":
      return "Product sales";
    default:
      return entityType;
  }
}

/** Human label for a field key within an entity type. */
export function fieldLabel(entityType: string, fieldKey: string): string {
  if (fieldKey === "*") return "All products";
  if (entityType === "daily_sales") return "Daily sales total";
  return fieldKey; // a product name key
}

/** The entity → field map used to derive unresolved rows, from the current product list. */
export function buildKnownFields(products: string[]): Record<string, string[]> {
  return {
    daily_sales: ["daily_sales"],
    product_sales: ["*", ...products],
  };
}

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
  entityType: string;
  fieldKey: string;
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
  const byKey = new Map(rules.map((r) => [`${r.entityType}:${r.fieldKey}`, r]));
  const rows: RuleRow[] = [];
  for (const entityType of Object.keys(knownFields).sort()) {
    for (const fieldKey of [...knownFields[entityType]].sort()) {
      rows.push({
        entityType,
        fieldKey,
        rule: byKey.get(`${entityType}:${fieldKey}`) ?? null,
      });
    }
  }
  return rows;
}
