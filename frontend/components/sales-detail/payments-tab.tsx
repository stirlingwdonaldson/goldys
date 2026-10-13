"use client";

import { useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Button } from "@/components/ui/button";
import { DataTable } from "@/components/data-table/data-table";
import { TablePagination, usePageState } from "@/components/data-table/table-pagination";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { currencyColumn, dateColumn, numberColumn } from "@/components/data-table/columns";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import type { Api } from "@/lib/api";
import type { PaymentRow } from "@/lib/api/types";
import { useApiData } from "@/lib/use-api-data";
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
  const { page, setPage, pageSize, setPageSize } = usePageState(`${from}|${to}`);
  const [paymentType, setPaymentType] = useState("");

  const mix = useApiData((api) => api.getPaymentMix(from, to), [from, to], apiOverride);
  const rows = useApiData(
    (api) => api.listPayments({ from, to, paymentType }, page, pageSize),
    [from, to, paymentType, page, pageSize],
    apiOverride,
  );


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
        {mix.loading ? <LoadingState rows={2} /> : null}
        {mix.error ? (
          <ErrorState
            title="Couldn't load the payment mix"
            message={mix.error.message}
            onRetry={mix.reload}
          />
        ) : null}
        {mix.data ? <WidgetRenderer widget={buildPaymentMixWidget(mix.data)} /> : null}
      </div>

      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-3">
          <span className="text-sm text-muted-foreground">Payment type</span>
          <select
            aria-label="Payment type"
            className="rounded-md border bg-background px-2 py-1 text-sm"
            value={paymentType}
            onChange={(e) => {
              setPaymentType(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All payment types</option>
            {paymentTypes.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </div>

        {rows.loading ? <LoadingState rows={4} /> : null}
        {rows.error ? (
          <ErrorState
            title="Couldn't load payments"
            message={rows.error.message}
            onRetry={rows.reload}
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
            initialSorting={[{ id: "tradingDate", desc: true }]}
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
