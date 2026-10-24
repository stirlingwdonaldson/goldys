"use client";

import Link from "next/link";
import { RecipeIngredientDiagram } from "./recipe-ingredient-diagram";
import { recipeHref } from "./recipe-presentation";
import type {
  RecipeComposition,
  RecipeStock,
  RecipeUsage,
  RecipeHeader,
} from "@/lib/api/types";
import { formatCurrency, formatNumber } from "@/lib/format";
import { Section } from "@/components/layout/section";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

export function RecipeCompositionPanel({
  data,
  listHref,
}: {
  data: RecipeComposition;
  listHref?: string;
}) {
  const { header, stocks, usages } = data.definition;
  const stockExport =
    header.description === "This recipe is exported from a stock item";
  return (
    <>
      <div className="flex flex-wrap items-center gap-3 rounded-xl border bg-muted/30 p-4 text-sm">
        <span>
          <span className="text-muted-foreground">Batch yield </span>
          <strong>
            {formatNumber(header.portionCount, 4)} ×{" "}
            {formatNumber(header.portionSize, 4)} {header.unit ?? "—"}
          </strong>
        </span>
        {stockExport ? (
          <Badge variant="neutral">Stock-derived recipe</Badge>
        ) : null}
        {header.productionItem === true ? (
          <Badge variant="neutral">Production item</Badge>
        ) : null}
        {header.preparationMinutes != null && header.preparationMinutes > 0 ? (
          <span>{formatNumber(header.preparationMinutes)} min preparation</span>
        ) : null}
      </div>
      <RecipeIngredientDiagram data={data} listHref={listHref}/>
      <Section
        title="Ingredients"
        description="Quantities and contributions reported by CTB for this batch."
      >
        {stocks.length || usages.length ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Ingredient / sub-recipe</TableHead>
                <TableHead className="text-right">Quantity</TableHead>
                <TableHead className="text-right">CTB contribution</TableHead>
                <TableHead>Details</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {stocks.map((stock) => (
                <StockRow key={stock.lineId} stock={stock} />
              ))}
              {usages.map((usage) => (
                <UsageRow
                  key={usage.lineId}
                  usage={usage}
                  listHref={listHref}
                />
              ))}
            </TableBody>
          </Table>
        ) : (
          <p className="text-sm text-muted-foreground">
            No ingredient or sub-recipe lines recorded in this captured
            composition.
          </p>
        )}
        {stocks.some((s) => (s.quantity ?? 0) < 0) ||
        usages.some((u) => (u.quantity ?? 0) < 0) ? (
          <p className="text-xs text-muted-foreground">
            Negative quantities are source adjustments. Quantities and units are
            shown as recorded.
          </p>
        ) : null}
      </Section>
      <RecipeMethod header={header} />
      <RecipeCostingSection data={data} />
    </>
  );
}

function StockRow({ stock }: { stock: RecipeStock }) {
  return (
    <TableRow>
      <TableCell className="font-medium">
        {stock.description?.trim() || "Unnamed ingredient"}
        {stock.brokenLink ? (
          <Badge className="ml-2" variant="conflict">
            Broken source link
          </Badge>
        ) : null}
      </TableCell>
      <TableCell className="text-right tabular-nums">
        {formatNumber(stock.quantity, 4)} {stock.unit ?? "—"}
        {stock.quantity != null && stock.quantity < 0 ? (
          <Badge className="ml-2" variant="neutral">
            Adjustment
          </Badge>
        ) : null}
      </TableCell>
      <TableCell className="text-right tabular-nums">
        {formatCurrency(stock.cost)}
      </TableCell>
      <TableCell>
        <Popover>
          <PopoverTrigger asChild>
            <Button size="sm" variant="ghost">
              Stock & supplier
            </Button>
          </PopoverTrigger>
          <PopoverContent className="text-sm">
            <p>
              {stock.supplierName ?? "Supplier not supplied"} ·{" "}
              {stock.supplierStockCode ??
                stock.stockCode ??
                "Code not supplied"}{" "}
              · Pack {formatNumber(stock.stockPackSize, 4)}{" "}
              {stock.stockUnit ?? "—"} · {formatCurrency(stock.costPerPack)} ·
              Usable {formatNumber(stock.usablePercent)}% · Wastage{" "}
              {formatNumber(stock.wastagePercent)}%
            </p>
          </PopoverContent>
        </Popover>
      </TableCell>
    </TableRow>
  );
}

function UsageRow({
  usage,
  listHref,
}: {
  usage: RecipeUsage;
  listHref?: string;
}) {
  return (
    <TableRow>
      <TableCell className="font-medium">
        <Link
          className="underline underline-offset-4"
          href={recipeHref(usage.recipeId, listHref)}
        >
          {usage.name?.trim() || `Recipe #${usage.recipeId}`}
        </Link>
        <Badge className="ml-2" variant="neutral">
          Sub-recipe
        </Badge>
        {usage.brokenLink ? (
          <Badge className="ml-2" variant="conflict">
            Broken source link
          </Badge>
        ) : null}
      </TableCell>
      <TableCell className="text-right tabular-nums">
        {formatNumber(usage.quantity, 4)} {usage.unit ?? "—"}
        {usage.quantity != null && usage.quantity < 0 ? (
          <Badge className="ml-2" variant="neutral">
            Adjustment
          </Badge>
        ) : null}
      </TableCell>
      <TableCell className="text-right tabular-nums">
        {formatCurrency(usage.cost)}
      </TableCell>
      <TableCell className="text-muted-foreground">
        From {formatNumber(usage.originalPortionSize, 4)}{" "}
        {usage.originalUnit ?? "—"} yield
      </TableCell>
    </TableRow>
  );
}

function RecipeMethod({ header }: { header: RecipeHeader }) {
  const stockExport =
    header.description === "This recipe is exported from a stock item";
  return (
    <Section title="Method">
      {header.menuDescription ? (
        <p className="text-sm text-muted-foreground">
          {header.menuDescription}
        </p>
      ) : null}
      {header.description && !stockExport ? (
        <p className="whitespace-pre-wrap text-sm leading-relaxed">
          {header.description}
        </p>
      ) : (
        <p className="text-sm text-muted-foreground">
          {stockExport
            ? "Created from a stock item in CTB; no cooking method supplied."
            : "No method supplied in CTB."}
        </p>
      )}
      {header.hints ? (
        <div className="rounded-lg bg-muted/40 p-3 text-sm">
          <h3 className="mb-1 font-medium">Handy hints</h3>
          <p className="whitespace-pre-wrap">{header.hints}</p>
        </div>
      ) : null}
    </Section>
  );
}

function RecipeCostingSection({ data }: { data: RecipeComposition }) {
  const { header } = data.definition;
  const figures = [
    ["CTB batch cost", formatCurrency(header.totalCost)],
    ["CTB cost / portion", formatCurrency(header.costPerPortion)],
    [
      "Including labour / portion",
      formatCurrency(header.costPerPortionIncludingLabour),
    ],
    ["Labour / batch", formatCurrency(header.labourCost)],
    [
      "CTB selling price incl. GST",
      formatCurrency(header.sellingPriceIncludingGst),
    ],
    ["CTB revenue excl. GST", formatCurrency(header.revenueExcludingGst)],
    ["CTB gross profit", formatCurrency(header.grossProfit)],
    ["Recomputed batch estimate", formatCurrency(data.calculatedTotal)],
    ["Difference from CTB", formatCurrency(data.difference)],
  ];
  return (
    <Section
      title="Costing"
      description="CTB source estimates, separate from POS price observations and historical actual costs."
      card
    >
      <dl className="grid grid-cols-2 gap-4 lg:grid-cols-3">
        {figures.map(([label, value]) => (
          <div key={label}>
            <dt className="text-xs text-muted-foreground">{label}</dt>
            <dd className="mt-1 font-medium tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
      {data.issues.length ? (
        <div
          role="status"
          className="rounded-lg bg-status-conflict-soft p-3 text-sm"
        >
          {data.issues.some((i) => i.includes("COST_DISCREPANCY")) ? (
            <p>
              The recomputed estimate differs from CTB; review the quantities,
              units and batch costs.
            </p>
          ) : null}
          {data.stale || data.issues.some((i) => i.startsWith("STALE")) ? (
            <p>Refresh overdue for some recipe or folder observations.</p>
          ) : null}
          {data.calculatedTotal == null ? (
            <p>
              A complete cost estimate is unavailable: a linked recipe,
              quantity, unit or cost needs checking.
            </p>
          ) : null}
          <details className="mt-2">
            <summary className="cursor-pointer">
              Calculation checks ({data.issues.length})
            </summary>
            <ul className="mt-2 list-inside list-disc break-words text-xs">
              {data.issues.map((issue, index) => (
                <li key={`${issue}:${index}`}>{issue}</li>
              ))}
            </ul>
          </details>
        </div>
      ) : null}
    </Section>
  );
}
