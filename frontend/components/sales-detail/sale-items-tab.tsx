"use client";

import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { DataTable } from "@/components/data-table/data-table";
import { TablePagination } from "@/components/data-table/table-pagination";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { currencyColumn, dateColumn, numberColumn } from "@/components/data-table/columns";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import type { Api } from "@/lib/api";
import type { SaleItemRow } from "@/lib/api/types";
import { readSort, tableSorting, SORT_FIELDS } from "@/lib/record-sort";
import { listPage } from "@/lib/list-url";
import { useInvestigationUrl } from "@/lib/use-investigation-url";
import { useApi } from "@/lib/demo-mode";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { buildSaleItemMixWidget } from "./sale-item-mix-chart";

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
    {
      accessorKey: "saleClosedTenMinuteTime",
      header: "Sale closed (10-minute bucket)",
      cell: ({ getValue }) => getValue<string | null>()?.slice(0, 5) ?? "—",
      meta: { title: "Sale closed (10-minute bucket)" },
    },
    textColumn<SaleItemRow>("saleNumber", "Sale"),
    textColumn<SaleItemRow>("receiptLineId", "Receipt line"),
    {
      accessorKey: "receiptAssociation",
      header: "Receipt association",
      cell: ({ row }) => ({
        MAPPING_MISSING: "Receipt mapping missing",
        SITE_UNOBSERVED: "Site not observed",
        MAPPING_CONFLICT: "Conflicting receipt mappings",
        RECEIPT_GROUP: "Receipt group; transaction unverified",
        LEGACY_RECEIPT_GROUP: "Prior receipt number; transaction unverified",
      }[row.original.receiptAssociation ?? "LEGACY_RECEIPT_GROUP"] ?? "Association needs review"),
      meta: { title: "Receipt association" },
    },
    textColumn<SaleItemRow>("itemName", "Item"),
    textColumn<SaleItemRow>("productNumber", "Product no."),
    textColumn<SaleItemRow>("sku", "SKU"),
    textColumn<SaleItemRow>("categoryName", "Category"),
    textColumn<SaleItemRow>("orderType", "Order type"),
    numberColumn<SaleItemRow>("quantitySold", "Qty", (r) => r.quantitySold),
    currencyColumn<SaleItemRow>("soldPriceIncTax", "Source sold-price measure", (r) => r.soldPriceIncTax),
    currencyColumn<SaleItemRow>("costIncTax", "POS reported cost", (r) => r.costIncTax),
    currencyColumn<SaleItemRow>("amount", "Total", (r) => r.amount),
    ...(onViewSale ? [viewSaleColumn(onViewSale)] : []),
  ];
}

/**
 * The Sale items tab: the resolved sale-item mix over time, then the canonical line
 * items behind it. The chart reads `getSaleItemMix`; the table pages `listSaleItems`
 * server-side, always scoped by the shared date range.
 */
export function SaleItemsTab({ from, to, onViewSale, apiOverride }: SaleItemsTabProps) {
  const queryApi = useApi();
  const sortUrl = useInvestigationUrl();
  const sort = readSort(sortUrl.params, SORT_FIELDS.items, "tradingDate", "items-");
  const sorting = tableSorting(sort);
  const paging = new URLSearchParams({ page: sortUrl.params.get("items-page") ?? "0", size: sortUrl.params.get("items-size") ?? "10" });
  const parsedPage = listPage(paging);
  const page = (sortUrl.params.has("from") && (sortUrl.params.get("from") !== from || sortUrl.params.get("to") !== to)) ? 0 : parsedPage.page;
  const pageSize = parsedPage.pageSize;
  const setPage = (value: number) => sortUrl.update({ "items-page": String(value), from, to });
  const setPageSize = (value: number) => sortUrl.update({ "items-size": String(value), "items-page": null, from, to });
  const category = (sortUrl.params.get("category") ?? "").slice(0, 200);
  const setCategory = (value: string) => sortUrl.update({ category: value, "items-page": null, from, to });

  const mix = useQuery({ queryKey: ["getSaleItemMix", from, to], queryFn: ({ signal }) => (apiOverride ?? queryApi).getSaleItemMix(from, to, { signal }) });
  const rows = useQuery({ placeholderData: keepPreviousData, queryKey: ["listSaleItems", sort, { from, to, category }, page, pageSize], queryFn: ({ signal }) => (apiOverride ?? queryApi).listSaleItems({ from, to, category }, page, pageSize, { signal, sort }) });


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
        {mix.isPending ? <LoadingState rows={2} /> : null}
        {mix.error ? (
          <ErrorState
            title="Couldn't load the sale item mix"
            message={mix.error.message}
            onRetry={mix.refetch}
          />
        ) : null}
        {mix.data ? <WidgetRenderer widget={buildSaleItemMixWidget(mix.data)} /> : null}
      </div>

      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-3">
          <span className="text-sm text-muted-foreground">Category</span>
          <Select
            value={category || "all"}
            onValueChange={(v) => {
              setCategory(v === "all" ? "" : v);
              setPage(0);
            }}
          >
            <SelectTrigger aria-label="Category" className="w-44">
              <SelectValue placeholder="All categories" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All categories</SelectItem>
              {categories.map((name) => (
                <SelectItem key={name} value={name}>
                  {name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {rows.isPending ? <LoadingState rows={4} /> : null}
        {rows.error ? (
          <ErrorState
            title="Couldn't load sale items"
            message={rows.error.message}
            onRetry={rows.refetch}
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
            sorting={sorting}
            manualSorting
            sortingPending={rows.isPlaceholderData}
            onSortingChange={(change) => { const next = typeof change === "function" ? change(sorting) : change; sortUrl.update({ "items-sort": next[0]?.id ?? "tradingDate", "items-direction": next[0]?.desc ? "desc" : "asc", "items-page": null, from, to }); }}
            getRowId={(r) => r.receiptLineId}
          />
        ) : null}

        {rows.data ? (
          <TablePagination
            page={page}
            pageSize={pageSize}
            total={rows.data.total}
            onPageChange={setPage}
            onPageSizeChange={setPageSize}
          />
        ) : null}
      </div>
    </div>
  );
}
