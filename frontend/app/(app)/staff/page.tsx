import type { Metadata } from "next";
import { Users } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";

export const metadata: Metadata = { title: "Staff & Labor" };

export default function StaffPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Staff &amp; Labor</h1>
        <p className="text-sm text-muted-foreground">Roster, hours, and labor cost.</p>
      </div>
      <AwaitingData
        label="Rostering and labor"
        description="Scheduled vs. actual hours and labor % of sales."
        reason="Awaiting Deputy reporting."
        icon={Users}
      />
    </div>
  );
}
