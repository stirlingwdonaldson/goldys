"use client";

import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { BookOpen } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import type { RecipeCosting } from "@/lib/api";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { AwaitingData } from "@/components/states/awaiting-data";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { StatCard } from "@/components/data-display/stat-card";
import { formatCurrency, formatPercent } from "@/lib/format";
import { EmptyState } from "@/components/states/empty-state";
import { DataTable } from "@/components/data-table/data-table";
import { currencyColumn } from "@/components/data-table/columns";

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

export default function RecipesPage() {
  const recipes = useApiData((api) => api.getRecipes());

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
      currencyColumn("costPerPortion", "Cost/portion", (r) => r.costPerPortion),
      currencyColumn("actualCost", "Actual cost", (r) => r.actualCost),
      currencyColumn("costGap", "Cost gap", (r) => r.costGap),
      currencyColumn("menuPrice", "Menu price", (r) => r.menuPrice),
      currencyColumn("margin", "Margin", (r) => r.margin),
      gpColumn(),
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
            {rows.length === 0 ? (
              <EmptyState
                title="No recipes yet"
                description="Recipes appear here once Cooking the Books recipe data is resolved."
              />
            ) : (
              <DataTable columns={columns} data={rows} getRowId={(r) => String(r.recipeId)} />
            )}
          </Section>
        </>
      )}
    </div>
  );
}
