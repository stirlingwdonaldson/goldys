import type { Metadata } from "next";
import { Activity, Scale, Timer, Wrench } from "lucide-react";

export const metadata: Metadata = { title: "Dashboard" };

const indicators = [
  { label: "Ingestion completeness", hint: "Share of scheduled connector runs that succeed", icon: Activity },
  { label: "Open conflicts", hint: "Field-level discrepancies awaiting a decision", icon: Scale },
  { label: "Time to detect failure", hint: "How quickly a failed run becomes visible", icon: Timer },
  { label: "Manual overrides", hint: "How often staff resolve a conflict by hand", icon: Wrench },
];

export default function DashboardPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Operational overview of ingestion and reconciliation.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {indicators.map((indicator) => (
          <div key={indicator.label} className="rounded-lg border bg-card p-4">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <indicator.icon className="h-4 w-4" aria-hidden="true" />
              <span>{indicator.label}</span>
            </div>
            <p className="mt-3 text-2xl font-semibold">&mdash;</p>
            <p className="mt-1 text-xs text-muted-foreground">{indicator.hint}</p>
          </div>
        ))}
      </div>
      <p className="text-sm text-muted-foreground">
        No data yet &mdash; metrics will populate as connectors run and conflicts are surfaced.
      </p>
    </div>
  );
}
