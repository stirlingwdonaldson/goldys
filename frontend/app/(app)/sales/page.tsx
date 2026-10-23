"use client";

import { useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";
import { DataTable } from "@/components/data-table/data-table";
import { currencyColumn, dateColumn, sourceColumn, traceColumn } from "@/components/data-table/columns";
import { TablePagination, usePageState } from "@/components/data-table/table-pagination";
import { ProvenanceSheet, type ProvenanceTarget } from "@/components/trust/provenance-sheet";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { EmptyState } from "@/components/states/empty-state";
import type { DailySales } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { SalesTrend } from "@/components/dashboard/sales-trend";

function salesColumns(onTrace: (row: DailySales) => void): ColumnDef<DailySales>[] {
  return [
    dateColumn<DailySales>("date"),
    sourceColumn<DailySales>((r) => r.source),
    currencyColumn<DailySales>("totalSales", "Total sales", (r) => r.totalSales),
    currencyColumn<DailySales>("gst", "GST", (r) => r.gst),
    currencyColumn<DailySales>("net", "Net", (r) => r.net),
    traceColumn<DailySales>((r) => `sales for ${r.date}`, onTrace),
  ];
}

export default function SalesPage() {
  const queryApi = useApi();
  // The trend must render the reconciled/resolved daily totals, never a client-side
  // sum of competing per-source rows (audit finding F01). The per-source table below
  // stays for source comparison and pages on the server.
  const { page, setPage, pageSize, setPageSize } = usePageState("sales");
  const sales = useQuery({ queryKey: ["listDailySales", {}, page, pageSize], queryFn: ({ signal }) => queryApi.listDailySales({}, page, pageSize, { signal }) });
  const trend = useQuery({ queryKey: ["getSalesTrend", undefined, undefined], queryFn: ({ signal }) => queryApi.getSalesTrend(undefined, undefined, { signal }) });
  const [trace, setTrace] = useState<ProvenanceTarget | null>(null);
  const columns = useMemo(
    () => salesColumns((r) => setTrace({ metricId: "sales.gross", metricLabel: "Gross sales", date: r.date })),
    [],
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
  const rows = sales.data?.items ?? [];
  const total = sales.data?.total ?? 0;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Sales" description="Daily sales totals across your sources." />

      <SalesTrend points={trend.data?.points ?? []} notices={trend.data?.notices} provenance={trend.data?.provenance} />

      {total === 0 ? (
        <EmptyState
          title="No sales data"
          description="Daily sales will appear here once sources ingest."
        />
      ) : (
        <Section title="Daily totals by source">
          <DataTable
            columns={columns}
            data={rows}
            filterColumn="source"
            filterPlaceholder="Filter by source"
            pageSize={false}
            initialSorting={[{ id: "date", desc: true }]}
            getRowId={(r) => `${r.date}-${r.source}`}
          />
          <TablePagination
            page={page}
            pageSize={pageSize}
            total={total}
            onPageChange={setPage}
            onPageSizeChange={setPageSize}
          />
        </Section>
      )}

      <ProvenanceSheet target={trace} onClose={() => setTrace(null)} />
    </div>
  );
}
