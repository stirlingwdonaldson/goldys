"use client";

import { Activity, Scale, Timer, Wrench } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { StatCard } from "@/components/dashboard/stat-card";

export default function DashboardPage() {
  const { data, loading, error, reload } = useApiData((api) => api.getDashboardSummary());

  if (loading) return <LoadingState rows={2} />;
  if (error) {
    return (
      <ErrorState
        title="Couldn't load the dashboard"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={reload}
      />
    );
  }
  if (!data) {
    return (
      <EmptyState
        title="No dashboard data"
        description="Metrics will appear once connectors run and conflicts are surfaced."
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Operational overview of ingestion and reconciliation.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Ingestion completeness"
          value={data.ingestionCompleteness == null ? "—" : `${data.ingestionCompleteness}%`}
          hint="Share of scheduled connector runs that succeed"
          icon={Activity}
        />
        <StatCard
          label="Open conflicts"
          value={String(data.openConflicts)}
          hint="Fields awaiting a decision"
          icon={Scale}
        />
        <StatCard
          label="Time to detect failure"
          value={data.timeToDetectFailure ?? "—"}
          hint="How quickly a failed run becomes visible"
          icon={Timer}
        />
        <StatCard
          label="Manual overrides"
          value={data.overrideUsage ? String(data.overrideUsage.count) : "—"}
          hint={data.overrideUsage ? data.overrideUsage.period : "How often staff resolve by hand"}
          icon={Wrench}
        />
      </div>
    </div>
  );
}
