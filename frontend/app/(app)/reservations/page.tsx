"use client";

import { CalendarDays } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { AwaitingData } from "@/components/states/awaiting-data";
import { PageHeader } from "@/components/layout/page-header";

/** The venue's "today" as YYYY-MM-DD, using the browser's local calendar date. */
function today(): string {
  return new Date().toLocaleDateString("en-CA");
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

export default function ReservationsPage() {
  const summary = useApiData((api) => api.getReservationSummary(today()));

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

  const s = summary.data;
  if (!s) {
    return (
      <div className="flex flex-col gap-6">
        <PageHeader title="Reservations" description="Covers, bookings, and no-shows." />
        <AwaitingData
          label="Reservations"
          description="Today's covers, bookings, no-shows, and walk-ins."
          reason="Awaiting OpenTable reporting."
          icon={CalendarDays}
        />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Reservations" description="Covers, bookings, and no-shows." />

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
    </div>
  );
}
