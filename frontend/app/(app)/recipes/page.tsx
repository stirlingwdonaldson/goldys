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
import {
  EMPTY,
  formatCurrency,
  formatDay,
  formatNumber,
  formatPercent,
  toNumber,
  type Numeric,
} from "@/lib/format";
import { EmptyState } from "@/components/states/empty-state";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { currencyColumn } from "@/components/data-table/columns";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Display-only GP% threshold for the table's green/amber pill. No venue target is
 * configured in the API, so this is a legible default (a manager can eyeball it);
 * it colours the cell only and never feeds a calculation.
 */
const HEALTHY_GP_PERCENT = 0.7;

/** A missing figure: a muted em dash, never a fabricated zero (design-system §1). */
function MissingValue() {
  return <span className="text-muted-foreground">{EMPTY}</span>;
}

/** A right-aligned money figure; negatives get the amber "needs attention" tone. */
function currencyCell(value: Numeric) {
  const n = toNumber(value);
  if (n == null) return <MissingValue />;
  return (
    <span className={cn("whitespace-nowrap tabular-nums", n < 0 && "text-status-conflict")}>
      {formatCurrency(n)}
    </span>
  );
}

/**
 * A delisted dish's last-sold price/cost: the figure, dimmed, with a short "Last sold"
 * pill. The date lives in the pill's tooltip so the cell stays on one line
 * (docs/design/ui-direction.md: "pills stay short, the detail goes in the tooltip").
 */
function HistoricalFigure({ value, date }: { value: number | null; date: string | null }) {
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
      <span className="tabular-nums text-muted-foreground">{formatCurrency(value)}</span>
      <Badge variant="neutral" title={date ? `Last sold ${formatDay(date)}` : "Last sold"}>
        Last sold
      </Badge>
    </span>
  );
}

function gpColumn(): ColumnDef<RecipeCosting> {
  const healthy = `At or above the healthy ${formatPercent(HEALTHY_GP_PERCENT)} mark`;
  return {
    id: "gpPercent",
    accessorFn: (r) => toNumber(r.gpPercent) ?? 0,
    header: ({ column }) => <DataTableColumnHeader column={column} title="GP%" />,
    cell: ({ row }) => {
      const gp = toNumber(row.original.gpPercent);
      if (gp == null) return <MissingValue />;
      const isHealthy = gp >= HEALTHY_GP_PERCENT;
      return (
        <Badge
          variant={isHealthy ? "success" : "conflict"}
          className="tabular-nums"
          title={isHealthy ? healthy : `Below the healthy ${formatPercent(HEALTHY_GP_PERCENT)} mark`}
        >
          {formatPercent(gp)}
        </Badge>
      );
    },
    meta: { title: "GP%", align: "right" },
  };
}

/** A menu status in venue-friendly language for the "Menu price" cell. */
function menuStateLabel(menuState: string): string {
  if (menuState === "OFF_MENU") return "No current catalogue match";
  if (menuState === "UNKNOWN") return "Availability unknown";
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
        accessorFn: (r) => r.name?.trim() ?? "",
        header: "Recipe",
        cell: ({ row }) => {
          const name = row.original.name?.trim();
          // An empty name is real (unnamed CTB shells) — label it rather than
          // leave a blank cell; never hide absence (design-system §1).
          if (!name) return <span className="italic text-muted-foreground">Unnamed recipe</span>;
          return (
            <span className="block max-w-xs truncate font-medium text-foreground" title={name}>
              {name}
            </span>
          );
        },
      },
      {
        id: "folder",
        accessorFn: (r) => r.folder?.trim() ?? "",
        header: "Folder",
        cell: ({ row }) => {
          const folder = row.original.folder?.trim();
          if (!folder) return <MissingValue />;
          return (
            <span className="block max-w-40 truncate text-muted-foreground" title={folder}>
              {folder}
            </span>
          );
        },
      },
      {
        id: "yield",
        accessorFn: (r) => r.portionSize ?? "",
        header: "Yield",
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-muted-foreground">
            {row.original.portionSize?.trim() || EMPTY}
          </span>
        ),
      },
      currencyColumn("costPerPortion", "Cost/portion", (r) => r.costPerPortion),
      {
        id: "actualCost",
        accessorFn: (r) => r.actualCost ?? r.lastSoldCost ?? 0,
        header: ({ column }) => <DataTableColumnHeader column={column} title="Actual cost" />,
        cell: ({ row }) => {
          const r = row.original;
          if (r.priceSource === "HISTORICAL") {
            return <HistoricalFigure value={r.lastSoldCost} date={r.lastSoldDate} />;
          }
          if (r.priceSource === "NONE") return <MissingValue />;
          return currencyCell(r.actualCost);
        },
        meta: { title: "Actual cost", align: "right" },
      },
      {
        id: "costGap",
        accessorFn: (r) => toNumber(r.costGap) ?? 0,
        header: ({ column }) => <DataTableColumnHeader column={column} title="Cost gap" />,
        cell: ({ row }) => currencyCell(row.original.costGap),
        meta: { title: "Cost gap", align: "right" },
      },
      {
        id: "menuPrice",
        accessorFn: (r) => r.menuPrice ?? r.lastSoldPrice ?? 0,
        header: ({ column }) => <DataTableColumnHeader column={column} title="Menu price" />,
        cell: ({ row }) => {
          const r = row.original;
          if (r.priceSource === "HISTORICAL") {
            return <HistoricalFigure value={r.lastSoldPrice} date={r.lastSoldDate} />;
          }
          if (r.priceSource === "NONE") {
            return (
              <Badge variant="missing" className="whitespace-nowrap">
                {menuStateLabel(r.menuState)}
              </Badge>
            );
          }
          return currencyCell(r.menuPrice);
        },
        meta: { title: "Menu price", align: "right" },
      },
      {
        id: "margin",
        accessorFn: (r) => toNumber(r.margin) ?? 0,
        header: ({ column }) => <DataTableColumnHeader column={column} title="Margin" />,
        cell: ({ row }) => currencyCell(row.original.margin),
        meta: { title: "Margin", align: "right" },
      },
      gpColumn(),
      {
        id: "breakdown",
        header: "",
        cell: ({ row }) => (
          <Button
            variant="outline"
            size="sm"
            className="whitespace-nowrap"
            aria-label={`Show ingredients for ${row.original.name?.trim() || "recipe"}`}
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
        description="Recipe costs are estimates; sold prices do not prove current menu terms. GP and cost comparisons await matching tax and cost bases. Per-dish cost, margin and gross profit."
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
