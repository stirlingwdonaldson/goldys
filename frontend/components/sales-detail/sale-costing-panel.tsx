"use client";

import { useQuery } from "@tanstack/react-query";
import type { Api } from "@/lib/api";
import type { SaleCosting } from "@/lib/api/types";
import { formatCurrency, formatDateTime, formatNumber, formatPercent } from "@/lib/format";

const STATUS: Record<string, string> = {
  CURRENT_RECIPE_ESTIMATE: "Current recipe estimate",
  UNMATCHED: "No recipe match",
  AMBIGUOUS_NAME: "Several recipes share this name",
  AMBIGUOUS_OR_INACTIVE_LINK: "Recipe link needs review",
  RECIPE_MISSING: "Linked recipe has not been received",
  COST_MISSING: "Recipe cost is missing",
  COST_DATE_UNKNOWN: "Cost observation date is unknown",
  QUANTITY_REVENUE_MISMATCH: "Quantity and revenue signs need review",
  STALE_COST: "Recipe cost needs refreshing",
};

const QUALIFICATIONS: Record<string, string> = {
  SALE_TOO_LARGE: "This sale exceeds the costing limit. Complete costing is unavailable.",
  ZERO_NET_REVENUE: "Net sale revenue is zero, so no margin percentage can be calculated.",
  REVENUE_MISSING: "Ex-GST sale revenue is missing, so gross margin is unavailable.",
  TAX_BASIS_UNVERIFIED: "The recipe cost tax basis is unverified, so gross margin is unavailable.",
  INCOMPLETE_COSTING: "Complete recipe costing is unavailable. Missing, ambiguous or outdated costs are not treated as zero.",
  EX_GST_COST_ASSUMED: "Based on final sale revenue after adjustments, excluding GST and tips.",
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
    <h3 className="font-semibold">Estimated gross margin: {formatPercent(costing.margin)}</h3>
    {costing.grossProfit != null ? <p>Estimated gross profit: {formatCurrency(costing.grossProfit)} excl. GST</p> : null}
    <p className="mt-1 text-muted-foreground">{QUALIFICATIONS[costing.qualification] ?? "Recipe costing needs review."}</p>
    <p className="mt-1 text-muted-foreground">
      {costing.recipeCostTaxBasis === "ASSUMED_EX_GST" ? "Assumes CTB recipe costs exclude GST. " : ""}
      Uses current recipe observations, not historical actual food cost.
    </p>
    <ul className="mt-3 flex flex-col gap-3">
      {costing.lines.map((line) => <RecipeCostLine key={line.receiptLineId} line={line} />)}
    </ul>
  </section>;
}

function RecipeCostLine({ line }: { line: SaleCosting["lines"][number] }) {
  const detail = [
    `Sold for ${formatCurrency(line.revenueIncTax)} incl. GST`,
    line.revenueExTax != null ? `${formatCurrency(line.revenueExTax)} excl. GST` : null,
    STATUS[line.status] ?? line.status,
    line.matchMethod === "EXACT_RECIPE_NAME" ? "matched by exact name" : line.matchMethod === "CTB_LINK" ? "CTB recipe link" : null,
    line.recipeCost != null ? `${formatNumber(line.quantity, 6)} × ${formatCurrency(line.costPerPortion)} = ${formatCurrency(line.recipeCost)} observed recipe cost` : null,
    line.costObservedAt ? `observed ${formatDateTime(line.costObservedAt)} (${line.costSource ?? "CTB"})` : null,
  ].filter(Boolean).join(" · ");
  return <li>
    <span className="font-medium">{line.itemName ?? "Unnamed item"}</span>
    {line.recipeId != null ? <span> → <a className="underline" href={`/recipes/${line.recipeId}`}>{line.recipeName}</a></span> : null}
    <div className="text-muted-foreground">{detail}</div>
  </li>;
}
