"use client";
import { invoiceReference, selectedInvoice, withSelectedInvoice } from "@/lib/invoice-reference";

import { Suspense, useCallback, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { ColumnDef } from "@tanstack/react-table";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { EmptyState } from "@/components/states/empty-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { currencyColumn, dateColumn } from "@/components/data-table/columns";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { TimeRangeSelector, presetRange, type DateRange } from "@/components/ui/time-range-selector";
import { InvoiceSheet } from "@/components/invoices/invoice-sheet";
import { formatDay } from "@/lib/format";
import type { InvoiceSummary } from "@/lib/api/types";

const PAGE_SIZE = 25;

const columns: ColumnDef<InvoiceSummary>[] = [
  {
    accessorKey: "supplierName",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Supplier" />,
    // One line per row: long supplier names truncate (full name on hover) instead of wrapping.
    cell: ({ getValue }) => (
      <span className="block max-w-56 truncate" title={getValue<string>()}>
        {getValue<string>()}
      </span>
    ),
    meta: { title: "Supplier" },
    enableHiding: false,
  },
  {
    accessorKey: "invoiceNumber",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Invoice" />,
    meta: { title: "Invoice" },
    enableHiding: false,
  },
  dateColumn("invoiceDate", "Date"),
  {
    id: "dueDate",
    accessorFn: (r) => r.dueDate ?? "",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Due" />,
    cell: ({ getValue }) => {
      const v = getValue<string>();
      return <span className="whitespace-nowrap">{v ? formatDay(v) : "—"}</span>;
    },
    meta: { title: "Due" },
    enableHiding: false,
  },
  currencyColumn("totalAmount", "Total", (r) => r.totalAmount),
  {
    accessorKey: "purchaseNumber",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Purchase" />,
    cell: ({ getValue }) => (getValue<string | null>() ?? "—"),
    meta: { title: "Purchase" },
  },
  {
    id: "pdf",
    accessorFn: (r) => (r.hasPdf ? "Yes" : "No"),
    header: ({ column }) => <DataTableColumnHeader column={column} title="PDF" />,
    cell: ({ row }) =>
      row.original.hasPdf ? (
        <Badge variant="secondary">Yes</Badge>
      ) : (
        <span className="text-muted-foreground">—</span>
      ),
    meta: { title: "PDF" },
  },
];

export default function InvoicesPage() {
  return (
    <Suspense fallback={<LoadingState rows={6} />}>
      <InvoicesContent />
    </Suspense>
  );
}

function InvoicesContent() {
  const queryApi = useApi();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const [supplier, setSupplier] = useState("");
  // Defaults to the trailing year: invoices are historical documents, and the CTB
  // ledger (which starts late Oct 2025) fits within it.
  const [range, setRange] = useState<DateRange>(() => presetRange("year"));
  const [page, setPage] = useState(0);
  // Invoice numbers in the order the table currently shows them (follows column sorting).
  const [order, setOrder] = useState<string[]>([]);

  // The open invoice lives in the URL (?invoice=…) so Back closes it and the link is shareable.
  const selected = selectedInvoice(params);
  // Whether *we* pushed the open-invoice history entry; if so, closing pops it so Back stays meaningful.
  const pushedInvoice = useRef(false);

  const setSelected = useCallback(
    (invoiceNumber: string | null, mode: "push" | "replace") => {
      const next = withSelectedInvoice(new URLSearchParams(params.toString()), invoiceNumber);
      const qs = next.toString();
      const url = qs ? `${pathname}?${qs}` : pathname;
      if (mode === "push") router.push(url, { scroll: false });
      else router.replace(url, { scroll: false });
    },
    [params, pathname, router],
  );

  function openInvoice(invoiceNumber: string) {
    if (selected) {
      // Already open: switch in place rather than stacking history entries.
      setSelected(invoiceNumber, "replace");
    } else {
      pushedInvoice.current = true;
      setSelected(invoiceNumber, "push");
    }
  }

  function closeInvoice() {
    if (pushedInvoice.current) {
      pushedInvoice.current = false;
      router.back();
    } else {
      setSelected(null, "replace");
    }
  }

  const invoices = useQuery({ queryKey: ["listInvoices", { supplier: supplier || undefined, from: range.from, to: range.to }, page, PAGE_SIZE], queryFn: ({ signal }) => queryApi.listInvoices({ supplier: supplier || undefined, from: range.from, to: range.to }, page, PAGE_SIZE, { signal }
      ) });

  if (invoices.isPending) return <LoadingState rows={6} />;
  if (invoices.isLoadingError) {
    if (invoices.error.code === "NOT_PERMITTED") return <PermissionDenied subject="supplier invoices" />;
    return (
      <ErrorState
        title="Couldn't load invoices"
        message={invoices.error.message}
        onRetry={invoices.refetch}
      />
    );
  }

  const items = invoices.data?.items ?? [];
  const total = invoices.data?.total ?? 0;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Invoices"
        description="Supplier invoices — browse the list, inspect line detail, and download the original PDF."
      />

      <div className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center gap-3">
          <Input
            aria-label="Supplier"
            placeholder="Filter by supplier"
            value={supplier}
            onChange={(e) => {
              setSupplier(e.target.value);
              setPage(0);
            }}
            className="w-56"
          />
          <TimeRangeSelector
            value={range}
            onChange={(r) => {
              setRange(r);
              setPage(0);
            }}
          />
        </div>

        {items.length === 0 ? (
          <EmptyState
            title="No invoices"
            description="No supplier invoices match this filter."
          />
        ) : (
          <DataTable
            columns={columns}
            data={items}
            pageSize={false}
            initialSorting={[{ id: "invoiceDate", desc: true }]}
            getRowId={(r) => r.invoiceId ?? r.invoiceNumber}
            onRowClick={(r) => openInvoice(invoiceReference(r))}
            isRowSelected={(r) => (invoiceReference(r)) === selected}
            onRowOrderChange={(rows) => setOrder(rows.map((r) => invoiceReference(r)))}
            emptyMessage="No invoices match."
          />
        )}

        <Pager page={page} total={total} size={PAGE_SIZE} onPage={setPage} />
      </div>

      <InvoiceSheet
        invoiceNumber={selected}
        order={order}
        onNavigate={(n) => setSelected(n, "replace")}
        onClose={closeInvoice}
      />
    </div>
  );
}

function Pager({
  page,
  total,
  size,
  onPage,
}: {
  page: number;
  total: number;
  size: number;
  onPage: (p: number) => void;
}) {
  const hasPrev = page > 0;
  const hasNext = (page + 1) * size < total;
  if (!hasPrev && !hasNext) return null;
  return (
    <div className="flex items-center gap-2 text-sm text-muted-foreground">
      <Button variant="outline" size="sm" disabled={!hasPrev} onClick={() => onPage(page - 1)}>
        Previous
      </Button>
      <span>
        {total > 0 ? `${page * size + 1}–${Math.min((page + 1) * size, total)}` : "0"} of {total}
      </span>
      <Button variant="outline" size="sm" disabled={!hasNext} onClick={() => onPage(page + 1)}>
        Next
      </Button>
    </div>
  );
}
