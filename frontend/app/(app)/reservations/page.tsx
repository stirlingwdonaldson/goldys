import type { Metadata } from "next";
import { CalendarDays } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";

export const metadata: Metadata = { title: "Reservations" };

export default function ReservationsPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Reservations</h1>
        <p className="text-sm text-muted-foreground">Covers, bookings, and no-shows.</p>
      </div>
      <AwaitingData
        label="Reservations"
        description="Today's covers, bookings, no-shows, and walk-ins."
        reason="Awaiting OpenTable reporting."
        icon={CalendarDays}
      />
    </div>
  );
}
