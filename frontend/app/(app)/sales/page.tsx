"use client";

import { useApiData } from "@/lib/use-api-data";
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

export default function SalesPage() {
  const sales = useApiData((api) => api.listDailySales());

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
        <section className="rounded-lg border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-xs text-muted-foreground">
                <th className="px-4 py-2 font-medium">Date</th>
                <th className="px-4 py-2 font-medium">Source</th>
                <th className="px-4 py-2 text-right font-medium">Total sales</th>
                <th className="px-4 py-2 text-right font-medium">GST</th>
                <th className="px-4 py-2 text-right font-medium">Net</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r: DailySales) => (
                <tr key={`${r.date}-${r.source}`} className="border-b">
                  <td className="px-4 py-2">{r.date}</td>
                  <td className="px-4 py-2 text-muted-foreground">{r.source}</td>
                  <td className="px-4 py-2 text-right">{money(r.totalSales)}</td>
                  <td className="px-4 py-2 text-right">{money(r.gst)}</td>
                  <td className="px-4 py-2 text-right">{money(r.net)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}
