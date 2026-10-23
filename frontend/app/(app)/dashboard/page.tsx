"use client";

import { useApiData } from "@/lib/use-api-data";
import { useCurrentUser } from "@/components/app-shell/current-user-provider";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { AttentionPanel } from "@/components/dashboard/attention-panel";
import { FocusShortcuts } from "@/components/dashboard/focus-shortcuts";
import { FocusSwitcher } from "@/components/my-work/work-list";
import { useFocus, useWorkQueue } from "@/components/my-work/use-work-queue";
import { filterByFocus } from "@/lib/work-queue";
import { BusinessKpiGrid } from "@/components/dashboard/business-kpi-grid";
import { ActivityChart } from "@/components/dashboard/activity-chart";
import { TopSellers } from "@/components/dashboard/top-sellers";
import { SalesTrend } from "@/components/dashboard/sales-trend";
import { PageHeader } from "@/components/layout/page-header";

export default function DashboardPage() {
  // One bootstrap request replaces the previous five independent fetches.
  const { data, loading, error, reload } = useApiData((api) => api.getDashboardBootstrap());
  const { user } = useCurrentUser();
  const queue = useWorkQueue();
  const [focus, setFocus] = useFocus();

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
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Home"
        description="Resolved sales and source qualifications."
        actions={<FocusSwitcher value={focus} onChange={setFocus} />}
      />

      <FocusShortcuts focus={focus} />

      <BusinessKpiGrid
        seniority={user?.seniority}
        latestSales={data.latestSales}
        salesTrend={data.salesTrend.points}
        coversToday={data.coversToday}
        foodCost={data.foodCost}
        topSellers={data.topSellers.items}
      />

      <section className="grid gap-4 lg:grid-cols-[1.4fr_1fr]">
        <SalesTrend points={data.salesTrend.points} notices={data.salesTrend.notices} provenance={data.salesTrend.provenance} />
        <ActivityChart points={data.activity} />
      </section>

      <TopSellers items={data.topSellers.items} notices={data.topSellers.notices} coverage={data.topSellers.coverage} />

      {/* Below the fold: the attention queue sits under the headline numbers so the
          dashboard reads top-down as "how is the business, then what needs doing". */}
      <AttentionPanel
        items={queue.data ? filterByFocus(queue.data.items, focus) : null}
        loading={queue.loading}
        openConflicts={data.summary.openConflicts}
      />
    </div>
  );
}
