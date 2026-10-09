"use client";

import { useApiData } from "@/lib/use-api-data";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { EmptyState } from "@/components/states/empty-state";
import type { SupplierCogs, UomUnitCost } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";

function money(v: number | null | undefined): string {
  return v == null || !Number.isFinite(Number(v)) ? "—" : `$${Number(v).toFixed(2)}`;
}

/** Trailing 30-day window, so the screen renders without any date inputs. */
function defaultRange(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(Date.UTC(to.getUTCFullYear(), to.getUTCMonth(), to.getUTCDate() - 29));
  return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
}

export default function KitchenPage() {
  const { from, to } = defaultRange();
  const lines = useApiData((api) => api.getInventoryLines(from, to), [from, to]);

  if (lines.loading) return <LoadingState rows={6} />;
  if (lines.error) {
    return (
      <ErrorState
        title="Couldn't load food cost"
        message={lines.error.message}
        onRetry={lines.reload}
      />
    );
  }
  const data = lines.data;
  const uom = data?.unitCostByUom ?? [];
  const suppliers = data?.cogsBySupplier ?? [];

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Kitchen" description="Food cost, stock, and wastage — unit cost per measure and spend by supplier." />

      {uom.length === 0 && suppliers.length === 0 ? (
        <EmptyState
          title="No line-level data"
          description="Unit cost and supplier spend appear once invoices are enriched."
        />
      ) : null}

      {uom.length > 0 ? (
        <section className="rounded-lg border">
          <h2 className="border-b px-4 py-2 text-sm font-semibold text-muted-foreground">
            Unit cost per measure
          </h2>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-xs text-muted-foreground">
                <th className="px-4 py-2 font-medium">UOM</th>
                <th className="px-4 py-2 text-right font-medium">Line total</th>
                <th className="px-4 py-2 text-right font-medium">Quantity</th>
                <th className="px-4 py-2 text-right font-medium">Unit cost</th>
              </tr>
            </thead>
            <tbody>
              {uom.map((r: UomUnitCost) => (
                <tr key={r.uom ?? "(unmeasured)"} className="border-b last:border-0">
                  <td className="px-4 py-2">{r.uom ?? "Unmeasured"}</td>
                  <td className="px-4 py-2 text-right">{money(r.lineTotal)}</td>
                  <td className="px-4 py-2 text-right">{r.quantity}</td>
                  <td className="px-4 py-2 text-right">{money(r.unitCost)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ) : null}

      {suppliers.length > 0 ? (
        <section className="rounded-lg border">
          <h2 className="border-b px-4 py-2 text-sm font-semibold text-muted-foreground">
            Spend by supplier
          </h2>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-xs text-muted-foreground">
                <th className="px-4 py-2 font-medium">Supplier</th>
                <th className="px-4 py-2 text-right font-medium">COGS</th>
                <th className="px-4 py-2 text-right font-medium">WET</th>
              </tr>
            </thead>
            <tbody>
              {suppliers.map((r: SupplierCogs) => (
                <tr key={r.supplier} className="border-b last:border-0">
                  <td className="px-4 py-2">{r.supplier}</td>
                  <td className="px-4 py-2 text-right">{money(r.lineTotal)}</td>
                  <td className="px-4 py-2 text-right">{money(r.wetAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ) : null}
    </div>
  );
}
