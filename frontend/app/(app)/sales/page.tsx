"use client";

import { Suspense, useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";
import { DataTable } from "@/components/data-table/data-table";
import { currencyColumn, dateColumn, sourceColumn, traceColumn } from "@/components/data-table/columns";
import { TablePagination } from "@/components/data-table/table-pagination";
import { ProvenanceSheet } from "@/components/trust/provenance-sheet";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { EmptyState } from "@/components/states/empty-state";
import type { DailySales } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { useReportUrl } from "@/components/trust/use-report-url";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { sourceIdentity } from "@/lib/sources";
import { SalesTrend } from "@/components/dashboard/sales-trend";

function salesColumns(onTrace: (row: DailySales) => void): ColumnDef<DailySales>[] {
  return [
    dateColumn<DailySales>("date"),
    sourceColumn<DailySales>((r) => r.source),
    currencyColumn<DailySales>("totalSales", "Total sales", (r) => r.totalSales),
    currencyColumn<DailySales>("gst", "GST", (r) => r.gst),
    currencyColumn<DailySales>("net", "Net", (r) => r.net),
    traceColumn<DailySales>((r) => `sales for ${r.date} from ${r.source}`, onTrace),
  ];
}

export default function SalesPage() {
  return <Suspense fallback={<LoadingState rows={5} />}><SalesContent /></Suspense>;
}

function SalesContent() {
  const queryApi = useApi();
  // The trend must render the reconciled/resolved daily totals, never a client-side
  // sum of competing per-source rows (audit finding F01). The per-source table below
  // stays for source comparison and pages on the server.
  const { params, update, page, pageSize, container, target, openTrace, closeTrace, restoreFocus } = useReportUrl("sales.gross", "Gross sales");
  const source = params.get("source") ?? "";
  const sales = useQuery({ queryKey: ["listDailySales", {}, page, pageSize], queryFn: ({ signal }) => queryApi.listDailySales({}, page, pageSize, { signal }) });
  const trend = useQuery({ queryKey: ["getSalesTrend", undefined, undefined], queryFn: ({ signal }) => queryApi.getSalesTrend(undefined, undefined, { signal }) });
  const columns = useMemo(
    () => salesColumns((r) => openTrace(r.date)),
    [openTrace],
  );

  if (sales.isPending || trend.isPending) return <LoadingState rows={5} />;
  const error = sales.error ?? trend.error;
  if (error) {
    return (
      <ErrorState
        title="Couldn't load sales"
        message={error.message}
        onRetry={() => {
          sales.refetch();
          trend.refetch();
        }}
      />
    );
  }
  const rows = (sales.data?.items ?? []).filter(row => `${row.source} ${sourceIdentity(row.source).label}`.toLowerCase().includes(source.toLowerCase()));
  const total = sales.data?.total ?? 0;

  return (
    <div ref={container} tabIndex={-1} className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Sales" description="Resolved net sales trend and separate daily observations from each source." />

      <SalesTrend points={trend.data?.points ?? []} notices={trend.data?.notices} provenance={trend.data?.provenance} />

      <details className="rounded-lg border p-3 text-sm">
        <summary className="cursor-pointer font-medium">How to read sales and trace a figure</summary>
        <p className="mt-2 text-muted-foreground">The trend uses resolved net sales excluding GST. The table shows individual source observations; do not add competing sources together. Trace opens the resolved gross-sales evidence for that date, rather than just the selected source. Missing amounts are unavailable, not zero. No prior-period comparison is shown.</p>
      </details>
      {total === 0 ? (
        <EmptyState
          title="No sales data"
          description="Daily sales will appear here once sources ingest."
        />
      ) : (
        <Section title="Daily totals by source">
          <p className="text-sm text-muted-foreground">All available trading dates, newest first. Source filtering and column sorting apply to this page only; this table is not restricted to the trend range above.</p>
          <div className="space-y-1.5">
            <Label htmlFor="sales-source">Filter sources on this page</Label>
            <Input id="sales-source" value={source} onChange={(event) => update({ source: event.target.value })} className="w-full sm:w-56" />
          </div>
          <DataTable
            columns={columns}
            data={rows}
            pageSize={false}
            initialSorting={[{ id: "date", desc: true }]}
            getRowId={(r) => `${r.date}-${r.source}`}
          />
          <TablePagination
            page={page}
            pageSize={pageSize}
            total={total}
            onPageChange={(value) => update({ page: String(value) })}
            onPageSizeChange={(value) => update({ size: String(value), page: null })}
          />
        </Section>
      )}

      <ProvenanceSheet target={target} onClose={closeTrace} onRestoreFocus={restoreFocus} />
    </div>
  );
}
