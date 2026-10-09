import { Lock, Percent, Users } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import type { ApiError, LabourSummary } from "@/lib/api";
import { isOwner } from "@/lib/roles";
import { PageHeader } from "@/components/layout/page-header";

interface StaffPageViewProps {
  /** Current user's seniority; gates the Owner-only labor-cost surface. */
  seniority?: string;
  /** The 30-day labour summary request. Omitted → the "awaiting data" placeholders. */
  labour?: {
    data: LabourSummary | null;
    loading: boolean;
    error: ApiError | null;
    reload: () => void;
  };
}

function hours(v: number | null): string {
  return v == null ? "—" : `${Number(v).toLocaleString(undefined, { maximumFractionDigits: 1 })} h`;
}

function money(v: number | null): string {
  return v == null ? "—" : Number(v).toLocaleString("en-AU", { style: "currency", currency: "AUD" });
}

function pct(v: number | null): string {
  return v == null ? "—" : `${(Number(v) * 100).toFixed(1)}%`;
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

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border p-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
    </div>
  );
}

function OwnerOnly() {
  return (
    <div className="rounded-xl border border-dashed bg-muted/40 p-4">
      <div className="flex items-center gap-2 text-sm font-medium">
        <Lock className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
        <span>Labour cost %</span>
      </div>
      <p className="mt-3 text-sm text-muted-foreground">Owner only.</p>
      <p className="mt-2 text-xs text-muted-foreground/70">
        Wage and labour-cost figures are restricted.
      </p>
    </div>
  );
}

/**
 * The Staff & Labor page. Roster and hours are visible to every signed-in role;
 * the wage/labor-cost surface is Owner-only per the permission model. Fails
 * closed: an absent seniority locks the labor-cost surface even if the backend
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
        <section className="flex flex-col gap-2">
          <h2 className="text-sm font-semibold">Hours, last 30 days</h2>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <Stat label="Scheduled" value={hours(s.scheduledHours)} />
            <Stat label="Actual" value={hours(s.actualHours)} />
            <Stat label="Over / under roster" value={hours(s.variance)} />
            <Stat
              label="Hours per cover"
              value={s.hoursPerCover == null ? "—" : Number(s.hoursPerCover).toFixed(2)}
            />
          </div>
        </section>
      ) : (
        <AwaitingData
          label="Rostering & hours"
          description="Scheduled vs. actual hours."
          reason="Awaiting Deputy reporting."
          icon={Users}
        />
      )}

      {!isOwnerRole ? (
        <OwnerOnly />
      ) : s ? (
        <section className="flex flex-col gap-2">
          <h2 className="text-sm font-semibold">Labour cost, last 30 days</h2>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <Stat label="Labour cost" value={money(s.labourCost)} />
            <Stat label="Cost per cover" value={money(s.labourCostPerCover)} />
            <Stat label="FOH, % of sales" value={pct(s.fohLabourCostPercent)} />
            <Stat label="BOH, % of sales" value={pct(s.bohLabourCostPercent)} />
          </div>
        </section>
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
