"use client";

import { useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { BookOpen } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import type { RecipeCosting, RecipeIngredient } from "@/lib/api";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { AwaitingData } from "@/components/states/awaiting-data";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { StatCard } from "@/components/data-display/stat-card";
import { formatCurrency, formatNumber, formatPercent } from "@/lib/format";
import { EmptyState } from "@/components/states/empty-state";
import { DataTable } from "@/components/data-table/data-table";
import { currencyColumn } from "@/components/data-table/columns";
import { Button } from "@/components/ui/button";

function gpColumn(): ColumnDef<RecipeCosting> {
  return {
    id: "gpPercent",
    accessorFn: (r) => r.gpPercent ?? 0,
    header: "GP%",
    cell: ({ row }) => (
      <span className="tabular-nums">{formatPercent(row.original.gpPercent)}</span>
    ),
    meta: { title: "GP%", align: "right" },
  };
}

/** A menu status in venue-friendly language for the "Menu price" cell. */
function menuStateLabel(menuState: string): string {
  if (menuState === "OFF_MENU") return "Off menu";
  if (menuState === "UNKNOWN") return "Not on menu";
  return "No price matched";
}

/** Sub-recipe lines grouped by their sub-recipe name, preserving first-seen order. */
function groupSubRecipes(lines: RecipeIngredient[]): [string, RecipeIngredient[]][] {
  const groups = new Map<string, RecipeIngredient[]>();
  for (const line of lines) {
    const key = line.lineRecipeName ?? "Sub-recipe";
    const bucket = groups.get(key) ?? [];
    bucket.push(line);
    groups.set(key, bucket);
  }
  return [...groups.entries()];
}

function IngredientLinesTable({ label, lines }: { label: string; lines: RecipeIngredient[] }) {
  if (lines.length === 0) return null;
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-sm font-medium text-muted-foreground">{label}</h3>
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b text-left text-xs uppercase text-muted-foreground">
            <th className="py-1.5 pr-4 font-medium">Ingredient</th>
            <th className="py-1.5 pr-4 text-right font-medium">Qty</th>
            <th className="py-1.5 pr-4 font-medium">Unit</th>
            <th className="py-1.5 pr-4 text-right font-medium">Cost</th>
            <th className="py-1.5 font-medium">Supplier</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((line) => (
            <tr key={line.recipeDetailId} className="border-b border-muted">
              <td className="py-1.5 pr-4">{line.stockDescription ?? "—"}</td>
              <td className="py-1.5 pr-4 text-right tabular-nums">
                {formatNumber(line.portionWeight, 4)}
              </td>
              <td className="py-1.5 pr-4">{line.unitOfMeasurement ?? "—"}</td>
              <td className="py-1.5 pr-4 text-right tabular-nums">
                {formatCurrency(line.costPerQty)}
              </td>
              <td className="py-1.5">{line.supplierName ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function RecipeIngredientsPanel({ recipe }: { recipe: RecipeCosting }) {
  const lines = useApiData((api) => api.getRecipeIngredients(recipe.recipeId), [recipe.recipeId]);

  if (lines.loading) return <LoadingState rows={3} />;
  if (lines.error) {
    return (
      <ErrorState
        title="Couldn't load ingredients"
        message={lines.error.message}
        onRetry={lines.reload}
      />
    );
  }

  const all = lines.data ?? [];
  if (all.length === 0) {
    return (
      <EmptyState
        title="No ingredients recorded"
        description={`${recipe.name ?? "This recipe"} has no ingredient lines in Cooking the Books yet.`}
      />
    );
  }

  const main = all.filter((line) => !line.isSubRecipe);
  const sub = groupSubRecipes(all.filter((line) => line.isSubRecipe));

  return (
    <div className="flex flex-col gap-4">
      <IngredientLinesTable label="Ingredients" lines={main} />
      {sub.map(([name, lines]) => (
        <IngredientLinesTable key={name} label={`Sub-recipe: ${name}`} lines={lines} />
      ))}
    </div>
  );
}

export default function RecipesPage() {
  const recipes = useApiData((api) => api.getRecipes());
  const [selected, setSelected] = useState<RecipeCosting | null>(null);

  const columns = useMemo<ColumnDef<RecipeCosting>[]>(
    () => [
      {
        id: "name",
        accessorFn: (r) => r.name ?? "",
        header: "Recipe",
        cell: ({ row }) => row.original.name ?? "—",
      },
      {
        id: "folder",
        accessorFn: (r) => r.folder ?? "",
        header: "Folder",
        cell: ({ row }) => row.original.folder ?? "—",
      },
      {
        id: "yield",
        accessorFn: (r) => r.portionSize ?? "",
        header: "Yield",
        cell: ({ row }) => row.original.portionSize ?? "—",
      },
      currencyColumn("costPerPortion", "Cost/portion", (r) => r.costPerPortion),
      currencyColumn("actualCost", "Actual cost", (r) => r.actualCost ?? r.lastSoldCost),
      currencyColumn("costGap", "Cost gap", (r) => r.costGap),
      {
        id: "menuPrice",
        accessorFn: (r) => r.menuPrice ?? r.lastSoldPrice ?? 0,
        header: "Menu price",
        cell: ({ row }) => {
          const r = row.original;
          if (r.priceSource === "HISTORICAL") {
            return (
              <span className="tabular-nums">
                {formatCurrency(r.lastSoldPrice)}
                <span className="text-muted-foreground"> · Last sold {r.lastSoldDate}</span>
              </span>
            );
          }
          if (r.priceSource === "NONE") {
            return <span className="text-muted-foreground">{menuStateLabel(r.menuState)}</span>;
          }
          return <span className="tabular-nums">{formatCurrency(r.menuPrice)}</span>;
        },
        meta: { title: "Menu price", align: "right" },
      },
      currencyColumn("margin", "Margin", (r) => r.margin),
      gpColumn(),
      {
        id: "breakdown",
        header: "",
        cell: ({ row }) => (
          <Button
            variant="outline"
            size="sm"
            aria-label={`Show ingredients for ${row.original.name ?? "recipe"}`}
            onClick={() => setSelected(row.original)}
          >
            Ingredients
          </Button>
        ),
      },
    ],
    [],
  );

  if (recipes.loading) return <LoadingState rows={5} />;
  if (recipes.error) {
    return (
      <ErrorState
        title="Couldn't load recipes"
        message={recipes.error.message}
        onRetry={recipes.reload}
      />
    );
  }

  const rows = recipes.data ?? [];
  const withMargin = rows.filter((r) => r.margin != null);
  const best = withMargin.reduce(
    (a, b) => ((a?.margin ?? 0) >= (b.margin ?? 0) ? a : b),
    withMargin[0] ?? null,
  );
  const withGp = rows.filter((r) => r.gpPercent != null);
  const avgGp = withGp.length
    ? withGp.reduce((s, r) => s + (r.gpPercent ?? 0), 0) / withGp.length
    : null;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Recipes"
        description="Per-dish cost, margin and gross profit."
      />

      {rows.length === 0 ? (
        <AwaitingData
          label="Recipes"
          description="Recipe list, per-dish ingredient cost, and gross margin."
          reason="Awaiting Cooking the Books recipe data."
          icon={BookOpen}
        />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
            <StatCard label="Recipes" value={String(rows.length)} />
            <StatCard
              label="Avg GP%"
              value={avgGp == null ? "—" : formatPercent(avgGp)}
            />
            <StatCard
              label="Best margin"
              value={best == null ? "—" : `${best.name ?? "—"} · ${formatCurrency(best.margin)}`}
            />
          </div>

          <Section title="Recipe costing">
            <DataTable columns={columns} data={rows} getRowId={(r) => String(r.recipeId)} />
            <p className="text-xs text-muted-foreground">
              Last-sold figures are historical, not current prices.
            </p>
          </Section>

          {selected ? (
            <Section title={`${selected.name ?? "Recipe"} — ingredients`}>
              <RecipeIngredientsPanel recipe={selected} />
            </Section>
          ) : null}
        </>
      )}
    </div>
  );
}
