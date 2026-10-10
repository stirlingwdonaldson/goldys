"use client";

import { useEffect, useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Button } from "@/components/ui/button";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { currencyColumn, dateColumn } from "@/components/data-table/columns";
import { StatCard } from "@/components/data-display/stat-card";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import type { Api } from "@/lib/api";
import type { DeletedSaleRow } from "@/lib/api/types";
import { formatCurrency, formatNumber, toNumber } from "@/lib/format";
import { useApiData } from "@/lib/use-api-data";
import { buildDeletedSalesTrend } from "./deleted-sales-trend";

const PAGE_SIZE = 50;

interface DeletedOrdersTabProps {
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

/** The row action that opens a sale, modelled on the data table's `traceColumn`. */
function viewSaleColumn(onViewSale: (saleNumber: string) => void): ColumnDef<DeletedSaleRow> {
  return {
    id: "view-sale",
    header: () => <span className="sr-only">View sale</span>,
    cell: ({ row }) => (
      <Button
        variant="ghost"
        size="sm"
        className="h-7"
        aria-label={`View sale ${row.original.saleNumber}`}
        onClick={() => onViewSale(row.original.saleNumber)}
      >
        View sale
      </Button>
    ),
    enableSorting: false,
    enableHiding: false,
    meta: { align: "right" },
  };
}

function deletedOrderColumns(
  onViewSale?: (saleNumber: string) => void,
): ColumnDef<DeletedSaleRow>[] {
  return [
    dateColumn<DeletedSaleRow>("tradingDate", "Date"),
    textColumn<DeletedSaleRow>("saleNumber", "Sale"),
    textColumn<DeletedSaleRow>("orderType", "Type"),
    textColumn<DeletedSaleRow>("note", "Note"),
    textColumn<DeletedSaleRow>("staffName", "Staff"),
    textColumn<DeletedSaleRow>("deletedByStaffName", "Deleted by"),
    // The deleted-sale fact carries both the opening and the deleting register; the
    // single "Register" column shows where the order was opened, matching the
    // register column on the payments and sale-items tabs.
    textColumn<DeletedSaleRow>("openedRegisterName", "Register"),
    textColumn<DeletedSaleRow>("tableNumber", "Table"),
    currencyColumn<DeletedSaleRow>("totalIncTax", "Total inc tax", (r) => r.totalIncTax),
    currencyColumn<DeletedSaleRow>("totalExTax", "Total ex tax", (r) => r.totalExTax),
    currencyColumn<DeletedSaleRow>("totalTax", "Tax", (r) => r.totalTax),
    // `currencyColumn` renders an em dash for a null cost, so no separate cell is needed.
    currencyColumn<DeletedSaleRow>("totalCost", "Cost", (r) => r.totalCost),
    ...(onViewSale ? [viewSaleColumn(onViewSale)] : []),
  ];
}

/** Server-side pager over the deleted-sales `DataPage`, mirroring the payments tab's. */
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
 * The Deleted orders tab: the resolved deleted-order trend over time, then the canonical
 * deleted orders behind it. The chart reads `getDeletedSaleTotals`; the table pages
 * `listDeletedSales` server-side, always scoped by the shared date range.
 */
export function DeletedOrdersTab({ from, to, onViewSale, apiOverride }: DeletedOrdersTabProps) {
  const [page, setPage] = useState(0);

  const totals = useApiData((api) => api.getDeletedSaleTotals(from, to), [from, to], apiOverride);
  const rows = useApiData(
    (api) => api.listDeletedSales({ from, to }, page, PAGE_SIZE),
    [from, to, page],
    apiOverride,
  );

  // A new date range starts a new result set; without this the old page index would
  // point past the new total and show an empty table.
  useEffect(() => {
    setPage(0);
  }, [from, to]);

  const columns = useMemo(() => deletedOrderColumns(onViewSale), [onViewSale]);

  // Period figures come from the resolved totals, never from the canonical rows.
  const periodCount = (totals.data ?? []).reduce((sum, t) => sum + t.count, 0);
  const periodValue = (totals.data ?? []).reduce((sum, t) => sum + (toNumber(t.totalIncTax) ?? 0), 0);

  // A denied request hides the whole tab behind one clear state.
  if (totals.error?.code === "NOT_PERMITTED" || rows.error?.code === "NOT_PERMITTED") {
    return <PermissionDenied subject="deleted orders" />;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-6">
        {totals.loading ? <LoadingState rows={2} /> : null}
        {totals.error ? (
          <ErrorState
            title="Couldn't load deleted orders"
            message={totals.error.message}
            onRetry={totals.reload}
          />
        ) : null}
        {totals.data ? (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <StatCard
                label="Deleted orders"
                value={formatNumber(periodCount)}
                hint="Voided or deleted sales in this period"
              />
              <StatCard
                label="Value inc tax"
                value={formatCurrency(periodValue)}
                hint="Resolved deleted value in this period"
              />
            </div>
            <WidgetRenderer widget={buildDeletedSalesTrend(totals.data)} />
          </>
        ) : null}
      </div>

      <div className="flex flex-col gap-4">
        {rows.loading ? <LoadingState rows={4} /> : null}
        {rows.error ? (
          <ErrorState
            title="Couldn't load deleted orders"
            message={rows.error.message}
            onRetry={rows.reload}
          />
        ) : null}

        {rows.data && rows.data.items.length === 0 ? (
          <EmptyState
            title="No deleted orders"
            description="No voided or deleted sales match these dates."
          />
        ) : null}

        {rows.data && rows.data.items.length > 0 ? (
          <DataTable
            columns={columns}
            data={rows.data.items}
            pageSize={false}
            // `dateColumn` derives its id from the accessor key, so sort on "tradingDate".
            initialSorting={[{ id: "tradingDate", desc: true }]}
            getRowId={(r) => r.saleNumber}
          />
        ) : null}

        {rows.data ? <Pager page={page} total={rows.data.total} onPage={setPage} /> : null}
      </div>
    </div>
  );
}
