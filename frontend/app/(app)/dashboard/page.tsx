"use client";

import { useMemo } from "react";
import { useApiData } from "@/lib/use-api-data";
import { useCurrentUser } from "@/components/app-shell/current-user-provider";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { NeedsDecisionBand } from "@/components/dashboard/needs-decision-band";
import { BusinessKpiGrid } from "@/components/dashboard/business-kpi-grid";

export default function DashboardPage() {
  const { data, loading, error, reload } = useApiData((api) => api.getDashboardSummary());
  const sales = useApiData((api) => api.listDailySales());
  const { user } = useCurrentUser();

  const latestSales = useMemo(() => {
    if (!sales.data?.length) return null;
    const byDate = new Map<string, number>();
    for (const r of sales.data) {
      const n = typeof r.totalSales === "number" ? r.totalSales : Number(r.totalSales);
      if (Number.isFinite(n)) byDate.set(r.date, (byDate.get(r.date) ?? 0) + n);
    }
    if (byDate.size === 0) return null;
    const latestDate = [...byDate.keys()].sort().pop()!;
    return { date: latestDate, total: byDate.get(latestDate)! };
  }, [sales.data]);

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

      <BusinessKpiGrid seniority={user?.seniority} latestSales={latestSales} />
    </div>
  );
}
