"use client";

import { useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { useApiData } from "@/lib/use-api-data";
import { DataTable } from "@/components/data-table/data-table";
import { currencyColumn, dateColumn, sourceColumn, traceColumn } from "@/components/data-table/columns";
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
  // The trend must render the reconciled/resolved daily totals, never a client-side
  // sum of competing per-source rows (audit finding F01). The per-source table below
  // stays for source comparison.
  const sales = useApiData((api) => api.listDailySales());
  const trend = useApiData((api) => api.getSalesTrend());
  const [trace, setTrace] = useState<ProvenanceTarget | null>(null);
  const columns = useMemo(
    () => salesColumns((r) => setTrace({ metricId: "sales.gross", metricLabel: "Gross sales", date: r.date })),
    [],
  );

  if (sales.loading || trend.loading) return <LoadingState rows={5} />;
  const error = sales.error ?? trend.error;
  if (error) {
    return (
      <ErrorState
        title="Couldn't load sales"
        message={error.message}
        onRetry={() => {
          sales.reload();
          trend.reload();
        }}
      />
    );
  }
  const rows = sales.data ?? [];

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Sales" description="Daily sales totals across your sources." />

      <SalesTrend points={trend.data ?? []} />

      {rows.length === 0 ? (
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
            initialSorting={[{ id: "date", desc: true }]}
            getRowId={(r) => `${r.date}-${r.source}`}
          />
        </Section>
      )}

      <ProvenanceSheet target={trace} onClose={() => setTrace(null)} />
    </div>
  );
}
