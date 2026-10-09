"use client";

import { useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { CalendarDays, Workflow } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { sourceLabel } from "@/lib/rule-logic";
import type { DailyCovers, ReservationSummary } from "@/lib/api";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { AwaitingData } from "@/components/states/awaiting-data";
import { EmptyState } from "@/components/states/empty-state";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ProvenanceSheet, type ProvenanceTarget } from "@/components/trust/provenance-sheet";

/** The venue's "today" as YYYY-MM-DD, using the browser's local calendar date. */
function today(): string {
  return new Date().toLocaleDateString("en-CA");
}

/** The 30 days ending today, as local calendar dates. */
function last30Days(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getFullYear(), to.getMonth(), to.getDate() - 29);
  return { from: from.toLocaleDateString("en-CA"), to: to.toLocaleDateString("en-CA") };
}

function whole(v: number): string {
  return v.toLocaleString();
}

function pct(v: number | null): string {
  return v == null ? "—" : `${(v * 100).toFixed(1)}%`;
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border p-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
    </div>
  );
}

function coversColumns(onTrace: (date: string) => void): ColumnDef<DailyCovers>[] {
  return [
    {
      accessorKey: "date",
      header: ({ column }) => <DataTableColumnHeader column={column} title="Date" />,
      meta: { title: "Date" },
      enableHiding: false,
    },
    {
      accessorKey: "covers",
      header: ({ column }) => <DataTableColumnHeader column={column} title="Covers" />,
      cell: ({ row }) => <span className="tabular-nums">{whole(row.original.covers)}</span>,
      meta: { title: "Covers", align: "right" },
    },
    {
      accessorKey: "authoritativeSource",
      header: "Source",
      cell: ({ row }) => (
        <span className="text-muted-foreground">
          {row.original.authoritativeSource ? sourceLabel(row.original.authoritativeSource) : "—"}
        </span>
      ),
      meta: { title: "Source" },
    },
    {
      accessorKey: "hasConflict",
      header: "Sources",
      cell: ({ row }) =>
        row.original.hasConflict ? (
          <Badge className="border-transparent bg-status-warning text-status-warning-foreground">
            Disagree
          </Badge>
        ) : (
          <span className="text-muted-foreground">Agree</span>
        ),
      meta: { title: "Sources" },
    },
    {
      id: "trace",
      header: () => <span className="sr-only">Trace</span>,
      cell: ({ row }) => (
        <Button
          variant="ghost"
          size="sm"
          className="h-7"
          aria-label={`Trace covers for ${row.original.date}`}
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

function SummaryStats({ s }: { s: ReservationSummary }) {
  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
      <Stat label="Bookings" value={whole(s.bookings)} />
      <Stat label="Covers" value={whole(s.covers)} />
      <Stat label="No-shows" value={whole(s.noShows)} />
      <Stat
        label="Avg party size"
        value={s.avgPartySize == null ? "—" : s.avgPartySize.toFixed(2)}
      />
      <Stat label="Walk-ins" value={whole(s.walkIns)} />
      <Stat label="Cancelled" value={whole(s.cancelled)} />
      <Stat label="No-show rate" value={pct(s.noShowRate)} />
      <Stat label="Booking→cover" value={pct(s.bookingToCoverConversion)} />
    </div>
  );
}

export default function ReservationsPage() {
  const { from, to } = last30Days();
  const summary = useApiData((api) => api.getReservationSummary(today()));
  const covers = useApiData((api) => api.listDailyCovers(from, to), [from, to]);
  const [trace, setTrace] = useState<ProvenanceTarget | null>(null);
  const columns = useMemo(
    () =>
      coversColumns((date) =>
        setTrace({ metricId: "reservations.covers", metricLabel: "Covers", date }),
      ),
    [],
  );

  if (summary.loading) return <LoadingState rows={3} />;
  if (summary.error) {
    return (
      <ErrorState
        title="Couldn't load reservations"
        message={summary.error.message}
        onRetry={summary.reload}
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Reservations</h1>
        <p className="text-sm text-muted-foreground">Covers, bookings, and no-shows.</p>
      </div>

      {summary.data ? (
        <SummaryStats s={summary.data} />
      ) : (
        <AwaitingData
          label="Reservations"
          description="Today's covers, bookings, no-shows, and walk-ins."
          reason="Awaiting OpenTable reporting."
          icon={CalendarDays}
        />
      )}

      <section className="flex flex-col gap-2">
        <h2 className="text-sm font-semibold">Covers, last 30 days</h2>
        {covers.loading ? <LoadingState rows={4} /> : null}
        {covers.error ? (
          <ErrorState
            title="Couldn't load daily covers"
            message={covers.error.message}
            onRetry={covers.reload}
          />
        ) : null}
        {covers.data && covers.data.length === 0 ? (
          <EmptyState
            title="No covers yet"
            description="Daily covers appear here once OpenTable data is resolved."
          />
        ) : null}
        {covers.data && covers.data.length > 0 ? (
          <DataTable
            columns={columns}
            data={covers.data}
            initialSorting={[{ id: "date", desc: true }]}
            getRowId={(r) => r.date}
          />
        ) : null}
      </section>

      <ProvenanceSheet target={trace} onClose={() => setTrace(null)} />
    </div>
  );
}
