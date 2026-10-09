"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { useApiData } from "@/lib/use-api-data";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { EmptyState } from "@/components/states/empty-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { InvoiceGraphView } from "@/components/inventory/invoice-graph-view";
import type { InventorySummary, SupplierCogs, UomUnitCost } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";
import { formatCurrency } from "@/lib/format";

function money(v: number | null | undefined): string {
  return v == null || !Number.isFinite(Number(v)) ? "—" : formatCurrency(Number(v));
}

function pct(v: number | null): string {
  return v == null ? "—" : `${(v * 100).toFixed(1)}%`;
}

/** Trailing 30-day window, so the screen renders without any date inputs. */
function defaultRange(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(Date.UTC(to.getUTCFullYear(), to.getUTCMonth(), to.getUTCDate() - 29));
  return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
}

const uomColumns: ColumnDef<UomUnitCost>[] = [
  {
    id: "uom",
    accessorFn: (r) => r.uom ?? "Unmeasured",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Measure" />,
    meta: { title: "Measure" },
    enableHiding: false,
  },
  {
    accessorKey: "lineTotal",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Line total" />,
    cell: ({ row }) => <span className="tabular-nums">{money(row.original.lineTotal)}</span>,
    meta: { title: "Line total", align: "right" },
  },
  {
    accessorKey: "quantity",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Quantity" />,
    cell: ({ row }) => <span className="tabular-nums">{row.original.quantity}</span>,
    meta: { title: "Quantity", align: "right" },
  },
  {
    accessorKey: "unitCost",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Unit cost" />,
    cell: ({ row }) => <span className="tabular-nums">{money(row.original.unitCost)}</span>,
    meta: { title: "Unit cost", align: "right" },
  },
];

const supplierColumns: ColumnDef<SupplierCogs>[] = [
  {
    accessorKey: "supplier",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Supplier" />,
    meta: { title: "Supplier" },
    enableHiding: false,
  },
  {
    accessorKey: "lineTotal",
    header: ({ column }) => <DataTableColumnHeader column={column} title="COGS" />,
    cell: ({ row }) => <span className="tabular-nums">{money(row.original.lineTotal)}</span>,
    meta: { title: "COGS", align: "right" },
  },
  {
    accessorKey: "wetAmount",
    header: ({ column }) => <DataTableColumnHeader column={column} title="WET" />,
    cell: ({ row }) => <span className="tabular-nums">{money(row.original.wetAmount)}</span>,
    meta: { title: "WET", align: "right" },
  },
];

function Stat({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <div className="rounded-xl border p-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
      <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
    </div>
  );
}

function SummaryStats({ s }: { s: InventorySummary }) {
  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <Stat label="Purchases" value={money(s.purchases)} hint="Invoiced stock, last 30 days" />
      <Stat label="Wastage" value={money(s.wastage)} hint="Recorded waste, last 30 days" />
      <Stat label="Food cost" value={pct(s.foodCostPercent)} hint="Purchases as a share of gross sales" />
    </div>
  );
}

export default function KitchenPage() {
  const { from, to } = defaultRange();
  const lines = useApiData((api) => api.getInventoryLines(from, to), [from, to]);
  const summary = useApiData((api) => api.getInventorySummary(from, to), [from, to]);

  if (lines.loading) return <LoadingState rows={6} />;
  if (lines.error) {
    if (lines.error.code === "NOT_PERMITTED") return <PermissionDenied subject="food cost" />;
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

      {summary.loading ? <LoadingState rows={1} /> : null}
      {summary.error ? (
        <p className="rounded-xl bg-destructive-soft p-4 text-sm text-destructive">
          Couldn&apos;t load the 30-day totals. Refresh the page to try again.
        </p>
      ) : null}
      {summary.data ? <SummaryStats s={summary.data} /> : null}

      <section className="flex flex-col gap-2">
        <h2 className="text-sm font-semibold">Purchase lineage</h2>
        <InvoiceGraphView from={from} to={to} />
      </section>

      {uom.length === 0 && suppliers.length === 0 ? (
        <EmptyState
          title="No line-level data"
          description="Unit cost and supplier spend appear once invoices are enriched."
        />
      ) : null}

      {uom.length > 0 ? (
        <section className="flex flex-col gap-2">
          <h2 className="text-sm font-semibold">Unit cost per measure</h2>
          <DataTable
            columns={uomColumns}
            data={uom}
            initialSorting={[{ id: "lineTotal", desc: true }]}
            getRowId={(r) => r.uom ?? "(unmeasured)"}
          />
        </section>
      ) : null}

      {suppliers.length > 0 ? (
        <section className="flex flex-col gap-2">
          <h2 className="text-sm font-semibold">Spend by supplier</h2>
          <DataTable
            columns={supplierColumns}
            data={suppliers}
            filterColumn="supplier"
            filterPlaceholder="Filter suppliers"
            initialSorting={[{ id: "lineTotal", desc: true }]}
            getRowId={(r) => r.supplier}
          />
        </section>
      ) : null}
    </div>
  );
}
