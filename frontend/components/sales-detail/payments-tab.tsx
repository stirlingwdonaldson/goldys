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
import type { PaymentRow } from "@/lib/api/types";
import { readSort, tableSorting, SORT_FIELDS } from "@/lib/record-sort";
import { listPage } from "@/lib/list-url";
import { useInvestigationUrl } from "@/lib/use-investigation-url";
import { useApi } from "@/lib/demo-mode";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { buildPaymentMixWidget } from "./payment-mix-chart";

interface PaymentsTabProps {
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
function viewSaleColumn(onViewSale: (saleNumber: string) => void): ColumnDef<PaymentRow> {
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

function paymentColumns(onViewSale?: (saleNumber: string) => void): ColumnDef<PaymentRow>[] {
  return [
    dateColumn<PaymentRow>("tradingDate", "Date"),
    textColumn<PaymentRow>("saleNumber", "Sale"),
    textColumn<PaymentRow>("paymentTypeName", "Payment type"),
    textColumn<PaymentRow>("lspayPaymentMode", "Payment mode"),
    textColumn<PaymentRow>("reconciled", "Reconciled"),
    textColumn<PaymentRow>("registerName", "Register"),
    textColumn<PaymentRow>("staffName", "Staff"),
    currencyColumn<PaymentRow>("amount", "Amount", (r) => r.amount),
    currencyColumn<PaymentRow>("tip", "Tip", (r) => r.tip),
    currencyColumn<PaymentRow>("surcharge", "Surcharge", (r) => r.surcharge),
    currencyColumn<PaymentRow>("tendered", "Tendered", (r) => r.tendered),
    numberColumn<PaymentRow>("paymentCount", "Count", (r) => r.paymentCount),
    ...(onViewSale ? [viewSaleColumn(onViewSale)] : []),
  ];
}

/**
 * The Payments tab: the resolved payment mix over time, then the canonical payment
 * tenders behind it. The chart reads `getPaymentMix`; the table pages `listPayments`
 * server-side, always scoped by the shared date range.
 */
export function PaymentsTab({ from, to, onViewSale, apiOverride }: PaymentsTabProps) {
  const queryApi = useApi();
  const sortUrl = useInvestigationUrl();
  const sort = readSort(sortUrl.params, SORT_FIELDS.payments, "tradingDate", "payments-");
  const sorting = tableSorting(sort);
  const paging = new URLSearchParams({ page: sortUrl.params.get("payments-page") ?? "0", size: sortUrl.params.get("payments-size") ?? "10" });
  const parsedPage = listPage(paging);
  const page = (sortUrl.params.has("from") && (sortUrl.params.get("from") !== from || sortUrl.params.get("to") !== to)) ? 0 : parsedPage.page;
  const pageSize = parsedPage.pageSize;
  const setPage = (value: number) => sortUrl.update({ "payments-page": String(value), from, to });
  const setPageSize = (value: number) => sortUrl.update({ "payments-size": String(value), "payments-page": null, from, to });
  const paymentType = (sortUrl.params.get("paymentType") ?? "").slice(0, 200);
  const setPaymentType = (value: string) => sortUrl.update({ paymentType: value, "payments-page": null, from, to });

  const mix = useQuery({ queryKey: ["getPaymentMix", from, to], queryFn: ({ signal }) => (apiOverride ?? queryApi).getPaymentMix(from, to, { signal }) });
  const rows = useQuery({ placeholderData: keepPreviousData, queryKey: ["listPayments", sort, { from, to, paymentType }, page, pageSize], queryFn: ({ signal }) => (apiOverride ?? queryApi).listPayments({ from, to, paymentType }, page, pageSize, { signal, sort }) });


  // The dropdown offers exactly the tender types present in the resolved mix.
  const paymentTypes = useMemo(() => {
    const seen: string[] = [];
    for (const entry of mix.data ?? []) {
      if (!seen.includes(entry.paymentTypeName)) seen.push(entry.paymentTypeName);
    }
    return seen;
  }, [mix.data]);

  const columns = useMemo(() => paymentColumns(onViewSale), [onViewSale]);

  // A denied request hides the whole tab behind one clear state.
  if (mix.error?.code === "NOT_PERMITTED" || rows.error?.code === "NOT_PERMITTED") {
    return <PermissionDenied subject="payments" />;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        {mix.isPending ? <LoadingState rows={2} /> : null}
        {mix.error ? (
          <ErrorState
            title="Couldn't load the payment mix"
            message={mix.error.message}
            onRetry={mix.refetch}
          />
        ) : null}
        {mix.data ? <WidgetRenderer widget={buildPaymentMixWidget(mix.data)} /> : null}
      </div>

      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-3">
          <span className="text-sm text-muted-foreground">Payment type</span>
          <Select
            value={paymentType || "all"}
            onValueChange={(v) => {
              setPaymentType(v === "all" ? "" : v);
              setPage(0);
            }}
          >
            <SelectTrigger aria-label="Payment type" className="w-44">
              <SelectValue placeholder="All payment types" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All payment types</SelectItem>
              {paymentTypes.map((type) => (
                <SelectItem key={type} value={type}>
                  {type}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {rows.isPending ? <LoadingState rows={4} /> : null}
        {rows.error ? (
          <ErrorState
            title="Couldn't load payments"
            message={rows.error.message}
            onRetry={rows.refetch}
          />
        ) : null}

        {rows.data && rows.data.items.length === 0 ? (
          <EmptyState
            title="No payments"
            description="No payment tenders match these dates and filters."
          />
        ) : null}

        {rows.data && rows.data.items.length > 0 ? (
          <DataTable
            columns={columns}
            data={rows.data.items}
            pageSize={false}
            // `dateColumn` derives its id from the accessor key, so sort on "tradingDate"
            // (the ruling's "date" refers to no column and silently sorts nothing).
            sorting={sorting}
            manualSorting
            sortingPending={rows.isPlaceholderData}
            onSortingChange={(change) => { const next = typeof change === "function" ? change(sorting) : change; sortUrl.update({ "payments-sort": next[0]?.id ?? "tradingDate", "payments-direction": next[0]?.desc ? "desc" : "asc", "payments-page": null, from, to }); }}
            getRowId={(r) => `${r.saleNumber}-${r.paymentTypeName}-${r.tradingDate}`}
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
