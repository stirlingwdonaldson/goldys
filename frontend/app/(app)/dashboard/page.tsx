"use client";

import { Activity, Scale, Timer, Wrench } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { StatCard } from "@/components/dashboard/stat-card";
import { ConnectorHealthStrip } from "@/components/dashboard/connector-health-strip";
import { ActivityChart } from "@/components/dashboard/activity-chart";
import { OpenConflicts } from "@/components/dashboard/open-conflicts";
import { Skeleton } from "@/components/ui/skeleton";

function SectionError({ message }: { message: string }) {
  return <p className="text-sm text-destructive">{message}</p>;
}

export default function DashboardPage() {
  const { data, loading, error, reload } = useApiData((api) => api.getDashboardSummary());
  const connectors = useApiData((api) => api.listConnectorStatuses());
  const activity = useApiData((api) => api.getDashboardActivity());
  const sales = useApiData((api) => api.listReconciliationExceptions());
  const products = useApiData((api) => api.listProductExceptions());

  if (loading) return <LoadingState rows={2} />;
  if (error) {
    if (error.code === "NOT_PERMITTED") return <PermissionDenied subject="dashboard data" />;
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
    <div className="flex flex-col gap-8">
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

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-semibold text-muted-foreground">Connector health</h2>
        {connectors.loading ? (
          <Skeleton className="h-16 w-full" />
        ) : connectors.error ? (
          <SectionError message="Couldn't load connector status." />
        ) : (
          <ConnectorHealthStrip statuses={connectors.data ?? []} />
        )}
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-semibold text-muted-foreground">Activity · last 14 days</h2>
        {activity.loading ? (
          <Skeleton className="h-40 w-full" />
        ) : activity.error ? (
          <SectionError message="Couldn't load activity." />
        ) : (
          <ActivityChart points={activity.data ?? []} />
        )}
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-semibold text-muted-foreground">Open conflicts</h2>
        {sales.loading || products.loading ? (
          <Skeleton className="h-16 w-full" />
        ) : sales.error || products.error ? (
          <SectionError message="Couldn't load open conflicts." />
        ) : (
          <OpenConflicts sales={sales.data ?? []} products={products.data ?? []} />
        )}
      </section>
    </div>
  );
}
