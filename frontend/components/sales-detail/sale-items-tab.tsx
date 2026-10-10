"use client";

import { useEffect, useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Button } from "@/components/ui/button";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { currencyColumn, dateColumn, numberColumn } from "@/components/data-table/columns";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import type { Api } from "@/lib/api";
import type { SaleItemRow } from "@/lib/api/types";
import { useApiData } from "@/lib/use-api-data";
import { buildSaleItemMixWidget } from "./sale-item-mix-chart";

const PAGE_SIZE = 50;

interface SaleItemsTabProps {
  from: string;
  to: string;
  /** Present when the parent can navigate to a single sale (e.g. a drill-down). */
  onViewSale?: (saleNumber: string) => void;
  /** Injected by tests; defaults to the demo/live singleton. */
  apiOverride?: Api;
}

/** A nullable text column: sorts on the raw value, shows an em dash when absent. */
function textColumn<T>(key: keyof T & string, title: string): ColumnDef<T> {
  return {
    accessorKey: key,
    header: ({ column }) => <DataTableColumnHeader column={column} title={title} />,
    cell: ({ getValue }) => getValue<string | null>() ?? "—",
    meta: { title },
  };
}

/**
 * The row action that opens a sale. A receipt line may not have matched a sale (its
 * `saleNumber` is null), in which case there is nothing to open and no button renders.
 */
function viewSaleColumn(onViewSale: (saleNumber: string) => void): ColumnDef<SaleItemRow> {
  return {
    id: "view-sale",
    header: () => <span className="sr-only">View sale</span>,
    cell: ({ row }) => {
      const saleNumber = row.original.saleNumber;
      if (saleNumber == null) return null;
      return (
        <Button
          variant="ghost"
          size="sm"
          className="h-7"
          aria-label={`View sale ${saleNumber}`}
          onClick={() => onViewSale(saleNumber)}
        >
          View sale
        </Button>
      );
    },
    enableSorting: false,
    enableHiding: false,
    meta: { align: "right" },
  };
}

function saleItemColumns(onViewSale?: (saleNumber: string) => void): ColumnDef<SaleItemRow>[] {
  return [
    dateColumn<SaleItemRow>("tradingDate", "Date"),
    textColumn<SaleItemRow>("saleNumber", "Sale"),
    textColumn<SaleItemRow>("receiptLineId", "Receipt line"),
    textColumn<SaleItemRow>("itemName", "Item"),
    textColumn<SaleItemRow>("productNumber", "Product no."),
    textColumn<SaleItemRow>("sku", "SKU"),
    textColumn<SaleItemRow>("categoryName", "Category"),
    textColumn<SaleItemRow>("orderType", "Order type"),
    numberColumn<SaleItemRow>("quantitySold", "Qty", (r) => r.quantitySold),
    currencyColumn<SaleItemRow>("soldPriceIncTax", "Sold price", (r) => r.soldPriceIncTax),
    currencyColumn<SaleItemRow>("costIncTax", "Cost", (r) => r.costIncTax),
    currencyColumn<SaleItemRow>("amount", "Total", (r) => r.amount),
    ...(onViewSale ? [viewSaleColumn(onViewSale)] : []),
  ];
}

/** Server-side pager over the sale-items `DataPage`, mirroring the payments tab's. */
function Pager({ page, total, onPage }: { page: number; total: number; onPage: (p: number) => void }) {
  const hasPrev = page > 0;
  const hasNext = (page + 1) * PAGE_SIZE < total;
  if (!hasPrev && !hasNext) return null;
  return (
    <div className="flex items-center gap-2 text-sm text-muted-foreground">
      <Button variant="outline" size="sm" disabled={!hasPrev} onClick={() => onPage(page - 1)}>
        Previous
      </Button>
      <span>
        {total > 0 ? `${page * PAGE_SIZE + 1}–${Math.min((page + 1) * PAGE_SIZE, total)}` : "0"} of{" "}
        {total}
      </span>
      <Button variant="outline" size="sm" disabled={!hasNext} onClick={() => onPage(page + 1)}>
        Next
      </Button>
    </div>
  );
}

/**
 * The Sale items tab: the resolved sale-item mix over time, then the canonical line
 * items behind it. The chart reads `getSaleItemMix`; the table pages `listSaleItems`
 * server-side, always scoped by the shared date range.
 */
export function SaleItemsTab({ from, to, onViewSale, apiOverride }: SaleItemsTabProps) {
  const [page, setPage] = useState(0);
  const [category, setCategory] = useState("");

  const mix = useApiData((api) => api.getSaleItemMix(from, to), [from, to], apiOverride);
  const rows = useApiData(
    (api) => api.listSaleItems({ from, to, category }, page, PAGE_SIZE),
    [from, to, category, page],
    apiOverride,
  );

  // A new date range starts a new result set; without this the old page index would
  // point past the new total and show an empty table.
  useEffect(() => {
    setPage(0);
  }, [from, to]);

  // The dropdown offers exactly the categories present in the resolved mix.
  const categories = useMemo(() => {
    const seen: string[] = [];
    for (const entry of mix.data ?? []) {
      if (!seen.includes(entry.categoryName)) seen.push(entry.categoryName);
    }
    return seen;
  }, [mix.data]);

  const columns = useMemo(() => saleItemColumns(onViewSale), [onViewSale]);

  // A denied request hides the whole tab behind one clear state.
  if (mix.error?.code === "NOT_PERMITTED" || rows.error?.code === "NOT_PERMITTED") {
    return <PermissionDenied subject="sale items" />;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        {mix.loading ? <LoadingState rows={2} /> : null}
        {mix.error ? (
          <ErrorState
            title="Couldn't load the sale item mix"
            message={mix.error.message}
            onRetry={mix.reload}
          />
        ) : null}
        {mix.data ? <WidgetRenderer widget={buildSaleItemMixWidget(mix.data)} /> : null}
      </div>

      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-3">
          <span className="text-sm text-muted-foreground">Category</span>
          <select
            aria-label="Category"
            className="rounded-md border bg-background px-2 py-1 text-sm"
            value={category}
            onChange={(e) => {
              setCategory(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All categories</option>
            {categories.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </div>

        {rows.loading ? <LoadingState rows={4} /> : null}
        {rows.error ? (
          <ErrorState
            title="Couldn't load sale items"
            message={rows.error.message}
            onRetry={rows.reload}
          />
        ) : null}

        {rows.data && rows.data.items.length === 0 ? (
          <EmptyState
            title="No sale items"
            description="No sale items match these dates and filters."
          />
        ) : null}

        {rows.data && rows.data.items.length > 0 ? (
          <DataTable
            columns={columns}
            data={rows.data.items}
            pageSize={false}
            // `dateColumn` derives its id from the accessor key, so sort on "tradingDate".
            initialSorting={[{ id: "tradingDate", desc: true }]}
            getRowId={(r) => r.receiptLineId}
          />
        ) : null}

        {rows.data ? <Pager page={page} total={rows.data.total} onPage={setPage} /> : null}
      </div>
    </div>
  );
}
