"use client";

import { useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Download } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { EmptyState } from "@/components/states/empty-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { InlineError } from "@/components/states/inline-error";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { currencyColumn, dateColumn } from "@/components/data-table/columns";
import { useDemoMode } from "@/lib/demo-mode";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { formatCurrency, formatDay, formatNumber } from "@/lib/format";
import type { InvoiceSummary, LineGraphNode } from "@/lib/api/types";

const PAGE_SIZE = 50;

const columns: ColumnDef<InvoiceSummary>[] = [
  {
    accessorKey: "supplierName",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Supplier" />,
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
  const [supplier, setSupplier] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);

  const invoices = useApiData(
    (api) =>
      api.listInvoices(
        { supplier: supplier || undefined, from: from || undefined, to: to || undefined },
        page,
        PAGE_SIZE,
      ),
    [supplier, from, to, page],
  );

  if (invoices.loading) return <LoadingState rows={6} />;
  if (invoices.error) {
    if (invoices.error.code === "NOT_PERMITTED") return <PermissionDenied subject="supplier invoices" />;
    return (
      <ErrorState
        title="Couldn't load invoices"
        message={invoices.error.message}
        onRetry={invoices.reload}
      />
    );
  }

  const items = invoices.data?.items ?? [];
  const total = invoices.data?.total ?? 0;
  const selectedSummary = items.find((i) => i.invoiceNumber === selected) ?? null;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Invoices"
        description="Supplier invoices — browse the list, inspect line detail, and download the original PDF."
      />

      <div className="flex flex-col gap-6 xl:flex-row xl:items-start">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <div className="flex flex-wrap gap-3">
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
            <Input
              aria-label="From"
              type="date"
              value={from}
              onChange={(e) => {
                setFrom(e.target.value);
                setPage(0);
              }}
              className="w-44"
            />
            <Input
              aria-label="To"
              type="date"
              value={to}
              onChange={(e) => {
                setTo(e.target.value);
                setPage(0);
              }}
              className="w-44"
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
              getRowId={(r) => r.invoiceNumber}
              onRowClick={(r) => setSelected(r.invoiceNumber)}
              isRowSelected={(r) => r.invoiceNumber === selected}
              emptyMessage="No invoices match."
            />
          )}

          <Pager page={page} total={total} size={PAGE_SIZE} onPage={setPage} />
        </div>

        {selectedSummary ? (
          <aside className="w-full shrink-0 xl:w-96">
            <InvoiceDetailPanel
              invoiceNumber={selectedSummary.invoiceNumber}
              hasPdf={selectedSummary.hasPdf}
            />
          </aside>
        ) : null}
      </div>
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

function InvoiceDetailPanel({
  invoiceNumber,
  hasPdf,
}: {
  invoiceNumber: string;
  hasPdf: boolean;
}) {
  const { demo } = useDemoMode();
  const detail = useApiData((api) => api.getInvoice(invoiceNumber), [invoiceNumber]);
  const pdfUrl = `/api/invoices/${encodeURIComponent(invoiceNumber)}/pdf`;

  return (
    <Section title={`Invoice ${invoiceNumber}`} card>
      {detail.loading ? <LoadingState rows={3} /> : null}
      {detail.error ? (
        <InlineError>Couldn&apos;t load this invoice&apos;s lines. Refresh the page to try again.</InlineError>
      ) : null}
      {detail.data ? <LinesTable lines={detail.data.lines} /> : null}

      {hasPdf ? (
        demo ? (
          <p className="text-xs text-muted-foreground">PDF preview unavailable in demo mode</p>
        ) : (
          <div className="flex flex-col gap-3">
            <div>
              <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                Preview
              </h3>
              <iframe
                data-testid="invoice-pdf-preview"
                title="Invoice PDF preview"
                src={pdfUrl}
                className="mt-1.5 h-96 w-full rounded-lg border"
              />
            </div>
            <Button asChild variant="outline" size="sm" className="self-start">
              <a href={`${pdfUrl}?disposition=attachment`}>
                <Download className="mr-1.5 h-4 w-4" aria-hidden="true" />
                Download
              </a>
            </Button>
          </div>
        )
      ) : null}
    </Section>
  );
}

function LinesTable({ lines }: { lines: LineGraphNode[] }) {
  if (lines.length === 0) {
    return <p className="text-sm text-muted-foreground">No line detail for this invoice.</p>;
  }
  return (
    <div className="overflow-hidden rounded-lg border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b bg-muted/40 text-left text-xs text-muted-foreground">
            <th className="px-3 py-2 font-medium">Stock</th>
            <th className="px-3 py-2 font-medium">Product</th>
            <th className="px-3 py-2 text-right font-medium">Qty</th>
            <th className="px-3 py-2 text-right font-medium">Unit cost</th>
            <th className="px-3 py-2 text-right font-medium">Line total</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((l, i) => (
            <tr key={`${l.stockCode ?? ""}-${i}`} className="border-b last:border-0">
              <td className="px-3 py-2 font-mono text-xs text-muted-foreground">
                {l.stockCode ?? "—"}
              </td>
              <td className="px-3 py-2">{l.productNameKey}</td>
              <td className="px-3 py-2 text-right tabular-nums">{formatNumber(l.quantity, 3)}</td>
              <td className="px-3 py-2 text-right tabular-nums">{formatCurrency(l.unitCost)}</td>
              <td className="px-3 py-2 text-right tabular-nums">{formatCurrency(l.lineTotal)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
