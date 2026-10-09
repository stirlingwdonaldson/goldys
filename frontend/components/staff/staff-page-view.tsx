import { Percent, Users } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import type { ApiError, LabourSummary } from "@/lib/api";
import { isOwner } from "@/lib/roles";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { StatCard } from "@/components/data-display/stat-card";
import { OwnerOnlyTile } from "@/components/states/owner-only";
import { formatCurrency, formatHours, formatNumber, formatPercent } from "@/lib/format";

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
  const isOwnerRole = isOwner(seniority);
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

  const s = labour?.data && !isEmpty(labour.data) ? labour.data : null;

  return (
    <div className="flex flex-col gap-6">
      {header}

      {s ? (
        <Section title="Hours, last 30 days">
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard label="Scheduled" value={formatHours(s.scheduledHours)} />
            <StatCard label="Actual" value={formatHours(s.actualHours)} />
            <StatCard label="Over / under roster" value={formatHours(s.variance)} />
            <StatCard label="Hours per cover" value={formatNumber(s.hoursPerCover, 2)} />
          </div>
        </Section>
      ) : (
        <AwaitingData
          label="Rostering & hours"
          description="Scheduled vs. actual hours."
          reason="Awaiting Deputy reporting."
          icon={Users}
        />
      )}

      {!isOwnerRole ? (
        <OwnerOnlyTile label="Labour cost %" />
      ) : s ? (
        <Section title="Labour cost, last 30 days">
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard label="Labour cost" value={formatCurrency(s.labourCost)} />
            <StatCard label="Cost per cover" value={formatCurrency(s.labourCostPerCover)} />
            <StatCard label="FOH, % of sales" value={formatPercent(s.fohLabourCostPercent)} />
            <StatCard label="BOH, % of sales" value={formatPercent(s.bohLabourCostPercent)} />
          </div>
        </Section>
      ) : (
        <AwaitingData
          label="Labour cost %"
          description="Labour cost as a % of sales."
          reason="Awaiting Deputy reporting."
          icon={Percent}
        />
      )}
    </div>
  );
}
