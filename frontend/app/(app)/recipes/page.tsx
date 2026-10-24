"use client";

import Link from "next/link";
import { Suspense, useMemo } from "react";
import { usePathname, useSearchParams } from "next/navigation";
import type { ColumnDef } from "@tanstack/react-table";
import { BookOpen } from "lucide-react";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";
import type { RecipeCosting } from "@/lib/api";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { AwaitingData } from "@/components/states/awaiting-data";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { StatCard } from "@/components/data-display/stat-card";
import { formatCurrency, formatDateTime, formatDay } from "@/lib/format";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { currencyColumn } from "@/components/data-table/columns";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { recipeCoverage } from "@/components/recipes/recipe-presentation";

function RecipeCoverage({ recipe }: { recipe: RecipeCosting }) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          aria-label={`Data coverage for ${recipe.name?.trim() || "unnamed recipe"}`}
          className="h-auto gap-2 p-0"
        >
          <Badge variant="neutral">{recipeCoverage(recipe)}</Badge>
          {recipe.stale ? <Badge variant="conflict">Overdue</Badge> : null}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="space-y-2 text-sm">
        <p className="font-medium">{recipeCoverage(recipe)}</p>
        <p>
          Captured{" "}
          {recipe.capturedAt
            ? formatDateTime(recipe.capturedAt)
            : "at an unknown time"}
          .
        </p>
        <p className="break-words text-muted-foreground">
          {recipe.costSource ?? "Source details unavailable"}
        </p>
        <p className="text-xs text-muted-foreground">
          Received details do not establish cost accuracy or current menu
          availability.
        </p>
      </PopoverContent>
    </Popover>
  );
}

function RecipesContent() {
  const api = useApi();
  const recipes = useQuery({
    queryKey: ["getRecipes"],
    queryFn: ({ signal }) => api.getRecipes({ signal }),
  });
  const params = useSearchParams();
  const pathname = usePathname();
  const search = params.get("search") ?? "";
  const folder = params.get("folder") ?? "";
  const status = params.get("status") ?? "";
  function filter(key: string, value: string) {
    const next = new URLSearchParams(params.toString());
    if (value) next.set(key, value);
    else next.delete(key);
    window.history.replaceState(
      null,
      "",
      `${pathname}${next.size ? `?${next}` : ""}`,
    );
  }
  const returnTo = `/recipes${params.size ? `?${params}` : ""}`;
  const columns = useMemo(() => recipeColumns(returnTo), [returnTo]);
  if (recipes.isPending) return <LoadingState rows={5} />;
  if (recipes.isLoadingError)
    return (
      <ErrorState
        title="Couldn't load recipes"
        message={recipes.error.message}
        onRetry={recipes.refetch}
      />
    );
  const rows = recipes.data ?? [];
  const folders = [
    ...new Set(
      rows.map((r) => r.folder?.trim()).filter((v): v is string => !!v),
    ),
  ].sort();
  const filtered = rows.filter(
    (r) =>
      (!search ||
        (r.name ?? "").toLowerCase().includes(search.toLowerCase())) &&
      (!folder || r.folder?.trim() === folder) &&
      (!status || recipeCoverage(r) === status),
  );
  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader
        title="Recipes"
        description="Find a recipe, explore its ingredients and batches, and review CTB costing."
      />
      {!rows.length ? (
        <AwaitingData
          label="Recipes"
          description="Recipe ingredients and batch costing."
          reason="Awaiting Cooking the Books recipe data."
          icon={BookOpen}
        />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
            <StatCard label="Recipes" value={String(rows.length)} />
            <StatCard
              label="Details received"
              value={String(
                rows.filter((r) => r.coverage === "COMPLETE").length,
              )}
            />
            <StatCard
              label="Refresh overdue"
              value={String(rows.filter((r) => r.stale).length)}
            />
          </div>
          <Section
            title="Recipe catalogue"
            description="CTB source costs and separate POS price observations. POS observations do not establish current menu availability."
          >
            <div className="flex flex-wrap gap-2">
              <Input
                aria-label="Search recipes"
                placeholder="Search recipes…"
                value={search}
                onChange={(e) => filter("search", e.target.value)}
                className="h-9 w-full sm:w-64"
              />
              <Select
                value={folder || "all"}
                onValueChange={(value) =>
                  filter("folder", value === "all" ? "" : value)
                }
              >
                <SelectTrigger
                  aria-label="Recipe folder"
                  className="h-9 w-full sm:w-48"
                >
                  <SelectValue placeholder="All folders" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All folders</SelectItem>
                  {folder && !folders.includes(folder) ? (
                    <SelectItem value={folder}>{folder}</SelectItem>
                  ) : null}
                  {folders.map((name) => (
                    <SelectItem key={name} value={name}>
                      {name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select
                value={status || "all"}
                onValueChange={(value) =>
                  filter("status", value === "all" ? "" : value)
                }
              >
                <SelectTrigger
                  aria-label="Recipe details status"
                  className="h-9 w-full sm:w-48"
                >
                  <SelectValue placeholder="All details statuses" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All details statuses</SelectItem>
                  {["Details received", "Legacy only", "Details missing"].map(
                    (name) => (
                      <SelectItem key={name} value={name}>
                        {name}
                      </SelectItem>
                    ),
                  )}
                </SelectContent>
              </Select>
              {search || folder || status ? (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() =>
                    window.history.replaceState(null, "", pathname)
                  }
                >
                  Clear filters
                </Button>
              ) : null}
            </div>
            <DataTable
              columns={columns}
              data={filtered}
              stateKey="recipes"
              getRowId={(r) => String(r.recipeId)}
            />
            <p className="text-xs text-muted-foreground">
              Historical observations retain their last-sold date. Open a recipe
              for ingredients, method, related recipes and source evidence.
            </p>
          </Section>
        </>
      )}
    </div>
  );
}

export default function RecipesPage() {
  return (
    <Suspense fallback={<LoadingState rows={5} />}>
      <RecipesContent />
    </Suspense>
  );
}

function recipeColumns(returnTo: string): ColumnDef<RecipeCosting>[] {
  return [
    {
      id: "name",
      accessorFn: (r) => r.name?.trim() ?? "",
      header: ({ column }) => (
        <DataTableColumnHeader column={column} title="Recipe" />
      ),
      cell: ({ row }) => (
        <Link
          className="block max-w-xs truncate font-medium underline underline-offset-4"
          title={row.original.name?.trim() || "Unnamed recipe"}
          href={`/recipes/${row.original.recipeId}?from=${encodeURIComponent(returnTo)}`}
        >
          {row.original.name?.trim() || "Unnamed recipe"}
        </Link>
      ),
      meta: { title: "Recipe" },
    },
    {
      id: "folder",
      accessorFn: (r) => r.folder?.trim() ?? "",
      header: "Folder",
      cell: ({ row }) => (
        <span
          className="block max-w-40 truncate text-muted-foreground"
          title={row.original.folder ?? undefined}
        >
          {row.original.folder?.trim() || "—"}
        </span>
      ),
    },
    {
      accessorKey: "portionSize",
      header: "Yield",
      cell: ({ getValue }) => getValue<string | null>() || "—",
    },
    currencyColumn(
      "costPerPortion",
      "CTB cost / portion",
      (r) => r.costPerPortion,
    ),
    {
      id: "posPrice",
      accessorFn: (r) =>
        r.priceSource === "HISTORICAL" ? r.lastSoldPrice : r.menuPrice,
      header: ({ column }) => (
        <DataTableColumnHeader column={column} title="POS price observation" />
      ),
      cell: ({ row }) => {
        const r = row.original;
        return r.priceSource === "NONE" ? (
          <span className="text-muted-foreground">No price matched</span>
        ) : (
          <span className="inline-flex items-center gap-2 tabular-nums">
            {formatCurrency(
              r.priceSource === "HISTORICAL" ? r.lastSoldPrice : r.menuPrice,
            )}
            {r.priceSource === "HISTORICAL" ? (
              <Badge
                variant="neutral"
                title={
                  r.lastSoldDate
                    ? `Last sold ${formatDay(r.lastSoldDate)}`
                    : "Last sold date unavailable"
                }
              >
                Last sold
                {r.lastSoldDate ? ` ${formatDay(r.lastSoldDate)}` : ""}
              </Badge>
            ) : null}
          </span>
        );
      },
      meta: { title: "POS price observation", align: "right" },
    },
    {
      id: "coverage",
      accessorFn: recipeCoverage,
      header: "Data coverage",
      cell: ({ row }) => <RecipeCoverage recipe={row.original} />,
    },
  ];
}
