"use client";

import { useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Workflow } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { sourceLabel } from "@/lib/rule-logic";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { Button } from "@/components/ui/button";
import { ProvenanceSheet, type ProvenanceTarget } from "@/components/trust/provenance-sheet";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { EmptyState } from "@/components/states/empty-state";
import type { DailySales } from "@/lib/api";

function money(v: number | string): string {
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? `$${n.toFixed(2)}` : "—";
}

function polylinePoints(values: number[]): string {
  const max = Math.max(1, ...values);
  return values
    .map((v, i) => `${(i / Math.max(1, values.length - 1)) * 100},${100 - (v / max) * 100}`)
    .join(" ");
}

function toNumber(v: number | string): number {
  return typeof v === "number" ? v : Number(v);
}

function salesColumns(onTrace: (date: string) => void): ColumnDef<DailySales>[] {
  const moneyColumn = (key: "totalSales" | "gst" | "net", title: string): ColumnDef<DailySales> => ({
    id: key,
    accessorFn: (r) => toNumber(r[key]),
    header: ({ column }) => <DataTableColumnHeader column={column} title={title} />,
    cell: ({ row }) => <span className="tabular-nums">{money(row.original[key])}</span>,
    meta: { title, align: "right" },
  });
  return [
    {
      accessorKey: "date",
      header: ({ column }) => <DataTableColumnHeader column={column} title="Date" />,
      meta: { title: "Date" },
      enableHiding: false,
    },
    {
      // Filter and sort on the label the user sees, not the source-system code.
      id: "source",
      accessorFn: (r) => sourceLabel(r.source),
      header: "Source",
      cell: ({ getValue }) => <span className="text-muted-foreground">{getValue() as string}</span>,
      meta: { title: "Source" },
    },
    moneyColumn("totalSales", "Total sales"),
    moneyColumn("gst", "GST"),
    moneyColumn("net", "Net"),
    {
      id: "trace",
      header: () => <span className="sr-only">Trace</span>,
      cell: ({ row }) => (
        <Button
          variant="ghost"
          size="sm"
          className="h-7"
          aria-label={`Trace sales for ${row.original.date}`}
          onClick={() => onTrace(row.original.date)}
        >
          <Workflow className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
          Trace
        </Button>
      ),
      enableSorting: false,
      enableHiding: false,
      meta: { align: "right" },
    },
  ];
}

export default function SalesPage() {
  const sales = useApiData((api) => api.listDailySales());
  const [trace, setTrace] = useState<ProvenanceTarget | null>(null);
  const columns = useMemo(
    () => salesColumns((date) => setTrace({ metricId: "sales.gross", metricLabel: "Gross sales", date })),
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
    const n = typeof r.totalSales === "number" ? r.totalSales : Number(r.totalSales);
    if (Number.isFinite(n)) byDate.set(r.date, (byDate.get(r.date) ?? 0) + n);
  }
  const points = [...byDate.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  const values = points.map(([, v]) => v);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Sales</h1>
        <p className="text-sm text-muted-foreground">Daily sales totals across your sources.</p>
      </div>

      <section className="rounded-lg border p-4">
        <h2 className="text-sm font-semibold text-muted-foreground">Daily sales</h2>
        <div role="img" aria-label="Daily sales trend" className="mt-3 h-48">
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
                stroke="#2563eb"
                strokeWidth="2"
                vectorEffect="non-scaling-stroke"
              />
            </svg>
          ) : (
            <p className="text-sm text-muted-foreground">No sales data yet.</p>
          )}
        </div>
      </section>

      {rows.length === 0 ? (
        <EmptyState
          title="No sales data"
          description="Daily sales will appear here once sources ingest."
        />
      ) : (
        <section className="flex flex-col gap-2">
          <h2 className="text-sm font-semibold">Daily totals by source</h2>
          <DataTable
            columns={columns}
            data={rows}
            filterColumn="source"
            filterPlaceholder="Filter by source"
            initialSorting={[{ id: "date", desc: true }]}
            getRowId={(r) => `${r.date}-${r.source}`}
          />
        </section>
      )}

      <ProvenanceSheet target={trace} onClose={() => setTrace(null)} />
    </div>
  );
}
