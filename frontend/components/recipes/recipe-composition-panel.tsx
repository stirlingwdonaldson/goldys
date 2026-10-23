"use client";

import { useApiData } from "@/lib/use-api-data";
import { formatCurrency, formatNumber } from "@/lib/format";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";

export function RecipeCompositionPanel({ recipeId }: { recipeId: number }) {
  const result = useApiData((api) => api.getRecipeComposition(recipeId), [recipeId]);
  if (result.loading) return <LoadingState rows={2} />;
  if (result.error) {
    return <ErrorState title="Couldn't load recipe details" message={result.error.message} onRetry={result.reload} />;
  }
  const data = result.data;
  if (!data) return <p className="text-sm text-muted-foreground">Complete recipe details have not been received yet.</p>;
  const header = data.definition.header;
  const observed = new Date(data.definition.observedAt).toLocaleString("en-AU", { timeZone: "Australia/Melbourne" });
  const outletIds = data.outlets.filter((outlet) => !outlet.deallocated).map((outlet) => outlet.outletId);
  return (
    <div className="flex flex-col gap-3 text-sm">
      <p className="text-muted-foreground">
        {data.folderPath.join(" / ") || "Folder not received"} · Observed {observed}
        {data.stale ? " · Refresh overdue" : ""}
      </p>
      <dl className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <div><dt className="text-muted-foreground">Batch yield</dt><dd>{formatNumber(header.portionCount, 4)} × {formatNumber(header.portionSize, 4)} {header.unit ?? "—"}</dd></div>
        <div><dt className="text-muted-foreground">CTB selling price incl. GST</dt><dd>{formatCurrency(header.sellingPriceIncludingGst)}</dd></div>
        <div><dt className="text-muted-foreground">CTB revenue excl. GST</dt><dd>{formatCurrency(header.revenueExcludingGst)}</dd></div>
        <div><dt className="text-muted-foreground">CTB gross profit</dt><dd>{formatCurrency(header.grossProfit)}</dd></div>
        <div><dt className="text-muted-foreground">CTB batch cost</dt><dd>{formatCurrency(header.totalCost)}</dd></div>
        <div><dt className="text-muted-foreground">Recomputed batch estimate</dt><dd>{formatCurrency(data.calculatedTotal)}</dd></div>
        <div><dt className="text-muted-foreground">Difference from CTB</dt><dd>{formatCurrency(data.difference)}</dd></div>
        <div><dt className="text-muted-foreground">Allocated outlets</dt><dd>{outletIds.join(", ") || "None recorded"}</dd></div>
      </dl>
      {header.description ? <p>{header.description}</p> : null}
      {header.menuDescription ? <p>{header.menuDescription}</p> : null}
      {header.hints ? <p>{header.hints}</p> : null}
      {data.definition.usages.length > 0 ? (
        <div>
          <h3 className="mb-1 font-medium">Sub-recipe usage</h3>
          <ul className="list-inside list-disc">
            {data.definition.usages.map((usage) => (
              <li key={usage.lineId}>
                {usage.name ?? "Unnamed recipe"}: {formatNumber(usage.quantity, 4)} {usage.unit ?? "—"}
                {" from a "}{formatNumber(usage.originalPortionSize, 4)} {usage.originalUnit ?? "—"} yield
                {" · CTB contribution "}{formatCurrency(usage.cost)}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {data.issues.length > 0 ? (
        <p role="status" className="text-amber-700 dark:text-amber-400">
          {data.issues.some((issue) => issue.includes("COST_DISCREPANCY"))
            ? "The recomputed estimate differs from CTB; review the quantities, units and batch costs. " : ""}
          {data.issues.some((issue) => issue.startsWith("STALE")) ? "Some recipe or folder observations need refreshing. " : ""}
          {data.calculatedTotal == null ? "A complete cost estimate is unavailable: a linked recipe, quantity, unit or cost needs checking. " : ""}
          {data.issues.some((issue) => issue.includes("FOLDER") || issue.includes("CATALOG")) ? "Folder information is incomplete or needs refreshing." : ""}
        </p>
      ) : null}
      <p className="text-xs text-muted-foreground">CTB figures are source estimates, separate from POS prices. Negative quantities represent adjustments.</p>
    </div>
  );
}
