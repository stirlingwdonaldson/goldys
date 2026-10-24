"use client";

import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";
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
import { TopSellers } from "@/components/dashboard/top-sellers";
import { SalesTrend } from "@/components/dashboard/sales-trend";
import { PageHeader } from "@/components/layout/page-header";

export default function DashboardPage() {
  const queryApi = useApi();
  // One bootstrap request replaces the previous five independent fetches.
  const { data, isPending: loading, error, refetch: reload } = useQuery({ queryKey: ["getDashboardBootstrap"], queryFn: ({ signal }) => queryApi.getDashboardBootstrap({ signal }) });
  const { user } = useCurrentUser();
  const queue = useWorkQueue();
  const [focus, setFocus] = useFocus();

  if (loading) return <LoadingState rows={2} />;
  if (error && !data) {
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

      <section>
        <SalesTrend points={data.salesTrend.points} notices={data.salesTrend.notices} provenance={data.salesTrend.provenance} />
      </section>

      <TopSellers items={data.topSellers.items} notices={data.topSellers.notices} coverage={data.topSellers.coverage} />

      <AttentionPanel
        notices={[
          ...(queue.data?.unavailable.length ? [`Not checked: ${queue.data.unavailable.join(", ")}.`] : []),
          ...(queue.data?.truncated.length ? [`Only the first 200 ${queue.data.truncated.join(" and ")} are included. Additional decisions are not included in this queue.`] : []),
          ...(queue.error ? ["The work queue could not be checked. Retry from My work."] : []),
        ]}
        items={queue.data ? filterByFocus(queue.data.items, focus) : null}
        loading={queue.isPending}
        openConflicts={data.summary.openConflicts}
      />
    </div>
  );
}
