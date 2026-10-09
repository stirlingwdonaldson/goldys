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
import { currencyColumn, numberColumn } from "@/components/data-table/columns";
import { StatCard } from "@/components/data-display/stat-card";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { InlineError } from "@/components/states/inline-error";
import { formatCurrency, formatPercent } from "@/lib/format";

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
  currencyColumn("lineTotal", "Line total", (r) => r.lineTotal),
  numberColumn("quantity", "Quantity", (r) => r.quantity, 3),
  currencyColumn("unitCost", "Unit cost", (r) => r.unitCost),
];

const supplierColumns: ColumnDef<SupplierCogs>[] = [
  {
    accessorKey: "supplier",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Supplier" />,
    meta: { title: "Supplier" },
    enableHiding: false,
  },
  currencyColumn("lineTotal", "COGS", (r) => r.lineTotal),
  currencyColumn("wetAmount", "WET", (r) => r.wetAmount),
];

function SummaryStats({ s }: { s: InventorySummary }) {
  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <StatCard label="Purchases" value={formatCurrency(s.purchases)} hint="Invoiced stock, last 30 days" />
      <StatCard label="Wastage" value={formatCurrency(s.wastage)} hint="Recorded waste, last 30 days" />
      <StatCard
        label="Food cost"
        value={formatPercent(s.foodCostPercent)}
        hint="Purchases as a share of gross sales"
      />
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
      <PageHeader
        title="Kitchen"
        description="Food cost, stock, and wastage — unit cost per measure and spend by supplier."
      />

      {summary.loading ? <LoadingState rows={1} /> : null}
      {summary.error ? (
        <InlineError>Couldn&apos;t load the 30-day totals. Refresh the page to try again.</InlineError>
      ) : null}
      {summary.data ? <SummaryStats s={summary.data} /> : null}

      <Section title="Purchase lineage">
        <InvoiceGraphView from={from} to={to} />
      </Section>

      {uom.length === 0 && suppliers.length === 0 ? (
        <EmptyState
          title="No line-level data"
          description="Unit cost and supplier spend appear once invoices are enriched."
        />
      ) : null}

      {uom.length > 0 ? (
        <Section title="Unit cost per measure">
          <DataTable
            columns={uomColumns}
            data={uom}
            initialSorting={[{ id: "lineTotal", desc: true }]}
            getRowId={(r) => r.uom ?? "(unmeasured)"}
          />
        </Section>
      ) : null}

      {suppliers.length > 0 ? (
        <Section title="Spend by supplier">
          <DataTable
            columns={supplierColumns}
            data={suppliers}
            filterColumn="supplier"
            filterPlaceholder="Filter suppliers"
            initialSorting={[{ id: "lineTotal", desc: true }]}
            getRowId={(r) => r.supplier}
          />
        </Section>
      ) : null}
    </div>
  );
}
