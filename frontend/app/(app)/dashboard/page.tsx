"use client";

import { useApiData } from "@/lib/use-api-data";
import { useCurrentUser } from "@/components/app-shell/current-user-provider";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { NeedsDecisionBand } from "@/components/dashboard/needs-decision-band";
import { BusinessKpiGrid } from "@/components/dashboard/business-kpi-grid";
import { ActivityChart } from "@/components/dashboard/activity-chart";
import { TopSellers } from "@/components/dashboard/top-sellers";
import { SalesTrend } from "@/components/dashboard/sales-trend";

export default function DashboardPage() {
  const { data, loading, error, reload } = useApiData((api) => api.getDashboardSummary());
  const latest = useApiData((api) => api.getLatestSales());
  const activity = useApiData((api) => api.getDashboardActivity());
  const topSellers = useApiData((api) => api.getTopSellers());
  const salesTrend = useApiData((api) => api.getSalesTrend());
  const { user } = useCurrentUser();

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
        <p className="text-sm text-muted-foreground">Your numbers, verified.</p>
      </div>

      <NeedsDecisionBand openCount={data.openConflicts} />

      <BusinessKpiGrid seniority={user?.seniority} latestSales={latest.data} />

      <section className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-lg border p-4">
          <h2 className="text-sm font-semibold text-muted-foreground">Sales · last 14 days</h2>
          <div className="mt-3">
            <SalesTrend points={salesTrend.data ?? []} />
          </div>
        </div>
        <div className="rounded-lg border p-4">
          <h2 className="text-sm font-semibold text-muted-foreground">
            Connector activity · last 14 days
          </h2>
          <div className="mt-3">
            <ActivityChart points={activity.data ?? []} />
          </div>
        </div>
      </section>

      <section className="rounded-lg border p-4">
        <h2 className="text-sm font-semibold text-muted-foreground">Top sellers · last 30 days</h2>
        <div className="mt-2">
          <TopSellers items={topSellers.data ?? []} />
        </div>
      </section>
    </div>
  );
}
