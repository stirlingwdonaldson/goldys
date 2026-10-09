"use client";

import { useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { CalendarDays } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import type { DailyCovers, ReservationSummary } from "@/lib/api";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { AwaitingData } from "@/components/states/awaiting-data";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { StatCard } from "@/components/data-display/stat-card";
import { formatNumber, formatPercent } from "@/lib/format";
import { EmptyState } from "@/components/states/empty-state";
import { DataTable } from "@/components/data-table/data-table";
import { dateColumn, numberColumn, sourceColumn, traceColumn } from "@/components/data-table/columns";
import { Badge } from "@/components/ui/badge";
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

function coversColumns(onTrace: (row: DailyCovers) => void): ColumnDef<DailyCovers>[] {
  return [
    dateColumn<DailyCovers>("date"),
    numberColumn<DailyCovers>("covers", "Covers", (r) => r.covers),
    sourceColumn<DailyCovers>((r) => r.authoritativeSource, { id: "authoritativeSource" }),
    {
      accessorKey: "hasConflict",
      header: "Sources",
      cell: ({ row }) =>
        row.original.hasConflict ? (
          <Badge variant="conflict">Disagree</Badge>
        ) : (
          <span className="text-muted-foreground">Agree</span>
        ),
      meta: { title: "Sources" },
    },
    traceColumn<DailyCovers>((r) => `covers for ${r.date}`, onTrace),
  ];
}

function SummaryStats({ s }: { s: ReservationSummary }) {
  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
      <StatCard label="Bookings" value={formatNumber(s.bookings)} />
      <StatCard label="Covers" value={formatNumber(s.covers)} />
      <StatCard label="No-shows" value={formatNumber(s.noShows)} />
      <StatCard label="Avg party size" value={formatNumber(s.avgPartySize, 2)} />
      <StatCard label="Walk-ins" value={formatNumber(s.walkIns)} />
      <StatCard label="Cancelled" value={formatNumber(s.cancelled)} />
      <StatCard label="No-show rate" value={formatPercent(s.noShowRate)} />
      <StatCard label="Booking→cover" value={formatPercent(s.bookingToCoverConversion)} />
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
      coversColumns((r) =>
        setTrace({ metricId: "reservations.covers", metricLabel: "Covers", date: r.date }),
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
      <PageHeader title="Reservations" description="Covers, bookings, and no-shows." />

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

      <Section title="Covers, last 30 days">
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
      </Section>

      <ProvenanceSheet target={trace} onClose={() => setTrace(null)} />
    </div>
  );
}
