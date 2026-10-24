"use client";

import { Suspense, useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { CalendarDays } from "lucide-react";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";
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
import { TablePagination } from "@/components/data-table/table-pagination";
import { useReportUrl } from "@/components/trust/use-report-url";
import { Badge } from "@/components/ui/badge";
import { ProvenanceSheet } from "@/components/trust/provenance-sheet";

/** The browser's "today" as YYYY-MM-DD, using the browser's local calendar date. */
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
    <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,9rem),1fr))] gap-4 md:grid-cols-4">
      <StatCard label="Bookings" value={formatNumber(s.bookings)} />
      <StatCard label="Attendance proxy" value={formatNumber(s.covers)} />
      <StatCard label="No-shows" value={formatNumber(s.noShows)} />
      <StatCard label="Avg party size" value={formatNumber(s.avgPartySize, 2)} />
      <StatCard label="Walk-ins" value={formatNumber(s.walkIns)} />
      <StatCard label="Cancelled" value={formatNumber(s.cancelled)} />
      <StatCard label="Declared no-show rate" value={formatPercent(s.noShowRate)} />
      <StatCard label="Booking→cover" value={formatPercent(s.bookingToCoverConversion)} />
    </div>
  );
}

export default function ReservationsPage() {
  return <Suspense fallback={<LoadingState rows={3} />}><ReservationsContent /></Suspense>;
}

function ReservationsContent() {
  const queryApi = useApi();
  const [{ from, to }] = useState(() => last30Days());
  const [summaryDate] = useState(() => today());
  const { update, page, pageSize, container, target, openTrace, closeTrace, restoreFocus } = useReportUrl("reservations.covers", "Covers");
  const summary = useQuery({ queryKey: ["getReservationSummary", summaryDate], queryFn: ({ signal }) => queryApi.getReservationSummary(summaryDate, { signal }) });
  const covers = useQuery({ queryKey: ["listDailyCovers", from, to], queryFn: ({ signal }) => queryApi.listDailyCovers(from, to, { signal }) });
  const columns = useMemo(
    () =>
      coversColumns((r) =>
        openTrace(r.date),
      ),
    [openTrace],
  );

  if (summary.isPending) return <LoadingState rows={3} />;
  if (summary.isLoadingError) {
    return (
      <ErrorState
        title="Couldn't load reservations"
        message={summary.error.message}
        onRetry={summary.refetch}
      />
    );
  }

  return (
    <div ref={container} tabIndex={-1} className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Reservations" description="Bookings and attendance proxies for a stated calendar date; daily covers are shown separately." />

      <p className="text-sm text-muted-foreground">Summary for {summary.data?.date ?? summaryDate}. Requests use the browser&apos;s calendar, not a configured trading-day calendar. No prior-period comparison is shown.</p>
      <details className="rounded-lg border p-3 text-sm">
        <summary className="cursor-pointer font-medium">How to read reservation measures</summary>
        <p className="mt-2 text-muted-foreground">Bookings count reservations; covers are an attendance proxy, not a verified headcount. Declared no-shows and cancelled bookings are separate states. Rates without a usable denominator are unavailable. Trace a daily covers row to inspect its sources and resolution before using the figure.</p>
      </details>
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
        <p className="text-sm text-muted-foreground">{from} to {to}, inclusive · browser calendar dates. This range differs from the single-date summary above. Column sorting applies to this page.</p>
        {covers.isPending ? <LoadingState rows={4} /> : null}
        {covers.error ? (
          <ErrorState
            title="Couldn't load daily covers"
            message={covers.error.message}
            onRetry={covers.refetch}
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
            data={[...covers.data].sort((a, b) => b.date.localeCompare(a.date)).slice(page * pageSize, (page + 1) * pageSize)}
            pageSize={false}
            initialSorting={[{ id: "date", desc: true }]}
            getRowId={(r) => r.date}
          />
        ) : null}
        {covers.data ? <TablePagination page={page} pageSize={pageSize} total={covers.data.length}
          onPageChange={(value) => update({ page: String(value) })}
          onPageSizeChange={(value) => update({ size: String(value), page: null })} /> : null}
      </Section>

      <ProvenanceSheet target={target} onClose={closeTrace} onRestoreFocus={restoreFocus} />
    </div>
  );
}
