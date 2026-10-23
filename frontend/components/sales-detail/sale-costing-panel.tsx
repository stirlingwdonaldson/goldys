"use client";

import { useQuery } from "@tanstack/react-query";
import type { Api } from "@/lib/api";
import { formatCurrency, formatDateTime, formatNumber, formatPercent } from "@/lib/format";

const STATUS: Record<string, string> = {
  CURRENT_RECIPE_ESTIMATE: "Current recipe estimate",
  UNMATCHED: "No recipe match",
  AMBIGUOUS_NAME: "Several recipes share this name",
  AMBIGUOUS_OR_INACTIVE_LINK: "Recipe link needs review",
  RECIPE_MISSING: "Linked recipe has not been received",
  COST_MISSING: "Recipe cost is missing",
  COST_DATE_UNKNOWN: "Cost observation date is unknown",
  STALE_COST: "Recipe cost needs refreshing",
};

/** Cost permission/failure is independent of the ordinary sale receipt. */
export function SaleCostingPanel({ saleNumber, api }: { saleNumber: string; api: Api }) {
  const result = useQuery({
    queryKey: ["sale-costing", saleNumber],
    queryFn: ({ signal }) => api.getSaleCosting(saleNumber, "2020-11-23", new Date().toISOString().slice(0, 10), { signal }),
  });
  if (result.isPending) return <p role="status">Loading recipe costing…</p>;
  if (result.error) return <p role="alert" className="text-sm text-muted-foreground">
    {result.error.code === "NOT_PERMITTED" ? "You do not have permission to view recipe costs." : "Recipe costing could not be loaded."}
  </p>;
  const costing = result.data;
  return <section aria-label="Recipe costing" className="rounded-xl border bg-card p-5 text-sm">
    <h3 className="font-semibold">Gross margin: {formatPercent(costing.margin)}</h3>
    <p className="mt-1 text-muted-foreground">{costing.qualification === "SALE_TOO_LARGE"
      ? "This sale exceeds the costing limit. Complete costing is unavailable."
      : costing.complete
        ? "Recipe costs are available. Gross margin is unavailable until comparable tax and cost bases are verified."
        : "Complete recipe costing is unavailable. Missing, ambiguous or outdated costs are not treated as zero."}</p>
    <p className="mt-1 text-muted-foreground">Recipe observations describe current portions, not historical actual food cost.</p>
    <ul className="mt-3 flex flex-col gap-3">
      {costing.lines.map((line) => <li key={line.receiptLineId}>
        <span className="font-medium">{line.itemName ?? "Unnamed item"}</span>
        {line.recipeId != null ? <span> → <a className="underline" href={`/recipes/${line.recipeId}`}>{line.recipeName}</a></span> : null}
        <div className="text-muted-foreground">
          Sold for {formatCurrency(line.revenueIncTax)} incl. GST{line.revenueExTax != null ? ` (${formatCurrency(line.revenueExTax)} excl. GST)` : ""}
          {" · "}{STATUS[line.status] ?? line.status}
          {line.matchMethod === "EXACT_RECIPE_NAME" ? " · matched by exact name" : line.matchMethod === "CTB_LINK" ? " · CTB recipe link" : ""}
          {line.recipeCost != null ? ` · ${formatNumber(line.quantity)} × ${formatCurrency(line.costPerPortion)} = ${formatCurrency(line.recipeCost)} observed recipe cost` : ""}
          {line.costObservedAt ? ` · observed ${formatDateTime(line.costObservedAt)} (${line.costSource ?? "CTB"})` : ""}
        </div>
      </li>)}
    </ul>
  </section>;
}
