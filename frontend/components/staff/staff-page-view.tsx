import { Percent, Users } from "lucide-react";
import { LabourDaysTable } from "./labour-days-table";
import { AwaitingData } from "@/components/states/awaiting-data";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import type { ApiError, LabourSummary } from "@/lib/api";
import { isOwner } from "@/lib/roles";
import { DataQualifications } from "@/components/data-display/data-qualifications";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { StatCard } from "@/components/data-display/stat-card";
import { OwnerOnlyTile } from "@/components/states/owner-only";
import { formatCurrency, formatHours, formatNumber } from "@/lib/format";

interface StaffPageViewProps {
  /** Current user's seniority; gates the Owner-only labour-cost surface. */
  seniority?: string;
  /** The 30-day labour summary request. Omitted → the "awaiting data" placeholders. */
  labour?: {
    data: LabourSummary | null;
    loading: boolean;
    error: ApiError | null;
    reload: () => void;
  };
}

/** True when nothing resolved for the period — every figure is null, which is not the same as zero. */
function isEmpty(s: LabourSummary): boolean {
  return [
    s.scheduledHours,
    s.actualHours,
    s.labourCost,
    s.variance,
    s.hoursPerCover,
    s.labourCostPerCover,
    s.fohLabourCostPercent,
    s.bohLabourCostPercent,
  ].every((v) => v == null);
}

/**
 * The Staff & labour page. Roster and hours are visible to every signed-in role;
 * the wage/labour-cost surface is Owner-only per the permission model. Fails
 * closed: an absent seniority locks the labour-cost surface even if the backend
 * returned cost figures.
 */
export function StaffPageView({ seniority, labour }: StaffPageViewProps) {
  const canSeeWageTotals = isOwner(seniority) || seniority?.trim().toUpperCase() === "MANAGER";
  const header = <PageHeader title="Staff & labour" description="Roster, hours, and labour cost." />;

  if (labour?.loading) return <LoadingState rows={3} />;
  if (labour?.error) {
    return (
      <div className="flex flex-col gap-6">
        {header}
        {labour.error.code === "NOT_PERMITTED" ? (
          <PermissionDenied subject="labour figures" />
        ) : (
          <ErrorState
            title="Couldn't load labour figures"
            message={labour.error.message}
            onRetry={labour.reload}
          />
        )}
      </div>
    );
  }

  const s = labour?.data && (!isEmpty(labour.data) || labour.data.days?.length) ? labour.data : null;

  return (
    <div className="flex flex-col gap-6">
      {header}
      <p className="text-sm text-muted-foreground">Wages only; super and other employment costs excluded pending confirmation. Per-cover figures use booking-size attendance proxies.</p>

      {!s && isOwner(seniority) && (
        <section aria-label="Tanda connection" className="rounded-lg border p-5">
          <h2 className="text-base font-semibold">Tanda reporting</h2>
          <p className="mt-2 max-w-prose text-sm text-muted-foreground">No labour figures are available for this period yet. Check the latest sync and any validation issues in Data health.</p>
          <a href="/data-health" className="mt-3 inline-flex text-sm font-medium underline underline-offset-4">Open Tanda connection status</a>
        </section>
      )}

      {s ? (
        <Section title="Hours, last 30 days"><DataQualifications label="Hours" scope="Last 30 days" notices={s.notices}>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard label="Scheduled (recorded)" value={formatHours(s.scheduledHours)} />
            <StatCard label="Actual (recorded)" value={formatHours(s.actualHours)} />
            <StatCard label="Roster variance" value={formatHours(s.variance)} />
            <StatCard label="Hours per attendance proxy" value={formatNumber(s.hoursPerCover, 2)} />
          </div>
        </DataQualifications></Section>
      ) : (
        <AwaitingData
          label="Rostering & hours"
          description="Scheduled vs. actual hours."
          reason="Awaiting validated Tanda reporting."
          icon={Users}
        />
      )}


      {!canSeeWageTotals ? (
        <OwnerOnlyTile label="Labour cost" />
      ) : s ? (
        <Section title="Labour cost, last 30 days"><DataQualifications label="Wages" scope="Last 30 days" notices={s.notices}>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard label="Labour cost (wages only)" value={formatCurrency(s.labourCost)} />
            <StatCard label="Wages per attendance proxy" value={formatCurrency(s.labourCostPerCover)} />
          </div>
        </DataQualifications></Section>
      ) : (
        <AwaitingData
          label="Labour cost"
          description="Labour cost as a % of sales."
          reason="Awaiting validated Tanda reporting."
          icon={Percent}
        />
      )}
      <LabourDaysTable summary={s} showWages={canSeeWageTotals} />
    </div>
  );
}
