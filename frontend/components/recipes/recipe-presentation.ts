import type { RecipeCosting, RecipeSection } from "@/lib/api/types";

export function recipeCoverage(recipe: RecipeCosting): string {
  if (recipe.coverage === "COMPLETE") return "Details received";
  if (recipe.coverage === "DETAILS_MISSING") return "Details missing";
  return "Legacy only";
}

/** Pair each selection with its own source row, even when names repeat. */
export function selectedRecipeLabels(section: RecipeSection): string[] {
  const rows = new Map<string, { name?: string; selected?: boolean }>();
  for (const field of section.fields) {
    const match = /^(data\[\d+\])\.(\w+)$/.exec(field.path);
    if (!match) continue;
    const row = rows.get(match[1]) ?? {};
    if (
      /^(dietaryRequirementName|allergenName|recipeFactDescription)$/.test(
        match[2],
      ) &&
      field.value != null
    )
      row.name = field.value;
    if (match[2] === "isChecked") row.selected = field.value === "true";
    rows.set(match[1], row);
  }
  return [...rows.values()]
    .filter((row) => row.selected && row.name)
    .map((row) => row.name!);
}

export function recipeFieldValue(field: {
  value: string | null;
  type: string;
}): string {
  if (field.value == null) return "Not supplied";
  if (field.type === "EMPTY_ARRAY" || field.type === "EMPTY_OBJECT")
    return "No entries recorded";
  if (field.value === "") return "Empty in CTB";
  if (field.type === "BOOLEAN") return field.value === "true" ? "Yes" : "No";
  return field.value;
}

export function recipeHref(recipeId: number, listHref?: string): string {
  return `/recipes/${recipeId}${listHref ? `?from=${encodeURIComponent(listHref)}` : ""}`;
}
