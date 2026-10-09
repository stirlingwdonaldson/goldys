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
import { toNumber } from "@/lib/format";

function polylinePoints(values: number[]): string {
  const max = Math.max(1, ...values);
  return values
    .map((v, i) => `${(i / Math.max(1, values.length - 1)) * 100},${100 - (v / max) * 100}`)
    .join(" ");
}

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
  const sales = useApiData((api) => api.listDailySales());
  const [trace, setTrace] = useState<ProvenanceTarget | null>(null);
  const columns = useMemo(
    () => salesColumns((r) => setTrace({ metricId: "sales.gross", metricLabel: "Gross sales", date: r.date })),
    [],
  );

  if (sales.loading) return <LoadingState rows={5} />;
  if (sales.error) {
    return (
      <ErrorState
        title="Couldn't load sales"
        message={sales.error.message}
        onRetry={sales.reload}
      />
    );
  }
  const rows = sales.data ?? [];

  // Trend: total per day, summed across sources, oldest first.
  const byDate = new Map<string, number>();
  for (const r of rows) {
    const n = toNumber(r.totalSales);
    if (n != null) byDate.set(r.date, (byDate.get(r.date) ?? 0) + n);
  }
  const points = [...byDate.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  const values = points.map(([, v]) => v);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Sales" description="Daily sales totals across your sources." />

      <Section title="Daily sales" card>
        <div role="img" aria-label="Daily sales trend" className="h-48">
          {values.length > 0 ? (
            <svg
              viewBox="0 0 100 100"
              preserveAspectRatio="none"
              className="h-full w-full"
              aria-hidden="true"
            >
              <polyline
                points={polylinePoints(values)}
                fill="none"
                stroke="hsl(var(--chart-1))"
                strokeWidth="2"
                vectorEffect="non-scaling-stroke"
              />
            </svg>
          ) : (
            <p className="text-sm text-muted-foreground">No sales data yet.</p>
          )}
        </div>
      </Section>

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
