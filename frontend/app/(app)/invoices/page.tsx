"use client";
import { listPage } from "@/lib/list-url";
import { useInvestigationUrl, investigationRange } from "@/lib/use-investigation-url";
import { QueryRegion } from "@/components/states/query-region";
import { invoiceReference, selectedInvoice, withSelectedInvoice } from "@/lib/invoice-reference";

import { Suspense, useCallback, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { ColumnDef } from "@tanstack/react-table";
import { readSort, tableSorting, SORT_FIELDS } from "@/lib/record-sort";
import { useApi } from "@/lib/demo-mode";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { LoadingState } from "@/components/states/loading-state";
import { EmptyState } from "@/components/states/empty-state";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { currencyColumn, dateColumn } from "@/components/data-table/columns";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { TimeRangeSelector, presetRange } from "@/components/ui/time-range-selector";
import { InvoiceSheet } from "@/components/invoices/invoice-sheet";
import { formatDay } from "@/lib/format";
import type { InvoiceSummary } from "@/lib/api/types";

const PAGE_SIZE = 25;

function invoiceColumns(onOpen: (reference: string) => void): ColumnDef<InvoiceSummary>[] {
  return [
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
      cell: ({ row }) => (
        <Button id={`invoice-${encodeURIComponent(invoiceReference(row.original))}`} variant="link" className="h-auto p-0 underline"
          aria-label={`Open invoice ${row.original.invoiceNumber ?? "without a number"} from ${row.original.supplierName}`}
          onClick={(event) => { event.stopPropagation(); onOpen(invoiceReference(row.original)); }}>
          {row.original.invoiceNumber ?? "View invoice"}
        </Button>
      ),
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
}

export default function InvoicesPage() {
  return (
    <Suspense fallback={<LoadingState rows={6} />}>
      <InvoicesContent />
    </Suspense>
  );
}

function InvoicesContent() {
  const queryApi = useApi();
  const sortUrl = useInvestigationUrl();
  const sort = readSort(sortUrl.params, SORT_FIELDS.invoices, "invoiceDate", "");
  const sorting = tableSorting(sort);
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const investigation = useInvestigationUrl();
  const supplier = (params.get("supplier") ?? "").slice(0, 200);
  const range = investigationRange(new URLSearchParams(params.toString()), presetRange("year"));
  const { page } = listPage(new URLSearchParams(params.toString()));
  const setPage = (value: number) => investigation.update({ page: String(value) });
  // Invoice numbers in the order the table currently shows them (follows column sorting).
  const [order, setOrder] = useState<string[]>([]);

  // The open invoice lives in the URL (?invoice=…) so Back closes it and the link is shareable.
  const selected = selectedInvoice(params);
  // Whether *we* pushed the open-invoice history entry; if so, closing pops it so Back stays meaningful.
  const pushedInvoice = useRef(false);

  const setSelected = useCallback(
    (invoiceNumber: string | null, mode: "push" | "replace") => {
      const next = withSelectedInvoice(new URLSearchParams(params.toString()), invoiceNumber);
      next.set("from", range.from); next.set("to", range.to);
      const qs = next.toString();
      const url = qs ? `${pathname}?${qs}` : pathname;
      if (mode === "push") router.push(url, { scroll: false });
      else router.replace(url, { scroll: false });
    },
    [params, pathname, router, range.from, range.to],
  );

  const invoiceTrigger = useRef("");
  function openInvoice(invoiceNumber: string) {
    if (!selected) invoiceTrigger.current = `invoice-${encodeURIComponent(invoiceNumber)}`;
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

  const invoices = useQuery({ placeholderData: keepPreviousData, queryKey: ["listInvoices", sort, { supplier: supplier || undefined, from: range.from, to: range.to }, page, PAGE_SIZE], queryFn: ({ signal }) => queryApi.listInvoices({ supplier: supplier || undefined, from: range.from, to: range.to }, page, PAGE_SIZE, { signal, sort }
      ) });

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
              investigation.update({ supplier: e.target.value, page: null, ...range });
            }}
            className="w-56 max-w-full"
          />
          <TimeRangeSelector
            value={range}
            onChange={(r) => {
              investigation.update({ ...r, page: null });
            }}
          />
        </div>

        <p className="text-sm text-muted-foreground">{range.from} to {range.to}, inclusive · invoice dates. No comparison period is shown.</p>
        <details className="rounded-lg border p-3 text-sm">
          <summary className="cursor-pointer font-medium">How to inspect invoice evidence</summary>
          <p className="mt-2 text-muted-foreground">Open an invoice to inspect its recorded lines and, where available, download the source PDF. Invoice purchases do not establish consumed food cost or current usable stock. Missing PDFs or values remain unavailable; they are not zero.</p>
        </details>
        <QueryRegion query={invoices} subject="Supplier invoices">
        {items.length === 0 ? (
          <EmptyState
            title="No invoices"
            description="No supplier invoices match this filter."
          />
        ) : (
          <DataTable
            columns={invoiceColumns(openInvoice)}
            data={items}
            pageSize={false}
            sorting={sorting}
            manualSorting
            sortingPending={invoices.isPlaceholderData}
            onSortingChange={(change) => { const next = typeof change === "function" ? change(sorting) : change; sortUrl.update({ "sort": next[0]?.id ?? "invoiceDate", "direction": next[0]?.desc ? "desc" : "asc", page: null });  }}
            getRowId={(r) => r.invoiceId ?? r.invoiceNumber}
            onRowClick={(r) => openInvoice(invoiceReference(r))}
            isRowSelected={(r) => (invoiceReference(r)) === selected}
            onRowOrderChange={(rows) => setOrder(rows.map((r) => invoiceReference(r)))}
            emptyMessage="No invoices match."
          />
        )}

        <Pager page={page} total={total} size={PAGE_SIZE} onPage={setPage} />
        </QueryRegion>
      </div>

      <InvoiceSheet
        invoiceNumber={selected}
        order={order}
        onNavigate={(n) => setSelected(n, "replace")}
        onClose={closeInvoice}
        onRestoreFocus={() => (document.getElementById(invoiceTrigger.current || `invoice-${encodeURIComponent(selected ?? "")}`) ?? document.getElementById("main-content"))?.focus()}
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
