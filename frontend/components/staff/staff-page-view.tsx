import { Lock, Percent, Users } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";
import { isOwner } from "@/lib/roles";

interface StaffPageViewProps {
  /** Current user's seniority; gates the Owner-only labor-cost surface. */
  seniority?: string;
}

/**
 * The Staff & Labor page. Roster and hours are visible to every signed-in role;
 * the wage/labor-cost surface is Owner-only per the permission model. Fails
 * closed: an absent seniority locks the labor-cost surface.
 */
export function StaffPageView({ seniority }: StaffPageViewProps) {
  const isOwnerRole = isOwner(seniority);
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Staff &amp; Labor</h1>
        <p className="text-sm text-muted-foreground">Roster, hours, and labor cost.</p>
      </div>

      <AwaitingData
        label="Rostering & hours"
        description="Scheduled vs. actual hours."
        reason="Awaiting Deputy reporting."
        icon={Users}
      />

      {isOwnerRole ? (
        <AwaitingData
          label="Labor cost %"
          description="Labor cost as a % of sales."
          reason="Awaiting Deputy reporting."
          icon={Percent}
        />
      ) : (
        <div className="rounded-lg border border-dashed bg-muted/40 p-4">
          <div className="flex items-center gap-2 text-sm font-medium">
            <Lock className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
            <span>Labor cost %</span>
          </div>
          <p className="mt-3 text-sm text-muted-foreground">Owner only.</p>
          <p className="mt-2 text-xs text-muted-foreground/70">
            Wage and labor-cost figures are restricted.
          </p>
        </div>
      )}
    </div>
  );
}
