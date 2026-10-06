import { CalendarDays, Lock, Percent, TrendingUp, Trophy, Utensils } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";
import { StatCard } from "@/components/dashboard/stat-card";
import { isOwner } from "@/lib/roles";

interface BusinessKpiGridProps {
  /** Current user's seniority; gates the Owner-only labor tile. */
  seniority?: string;
  /** The latest daily-sales total (summed across sources), or null when no data yet. */
  latestSales?: { date: string; total: number } | null;
}

/**
 * The dashboard's five business KPI tiles. The Sales tile shows the latest resolved daily total
 * once data lands; the rest are placeholders until their reporting data lands.
 */
export function BusinessKpiGrid({ seniority, latestSales }: BusinessKpiGridProps) {
  const isOwnerRole = isOwner(seniority);
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
      {latestSales ? (
        <StatCard
          label="Sales"
          value={`$${latestSales.total.toFixed(2)}`}
          hint={`Latest: ${latestSales.date}`}
          icon={TrendingUp}
        />
      ) : (
        <AwaitingData
          label="Sales"
          description="Net sales today and this week vs. last week."
          reason="Awaiting sales-reporting endpoint."
          icon={TrendingUp}
        />
      )}
      {isOwnerRole ? (
        <AwaitingData
          label="Labor cost %"
          description="Scheduled vs. actual hours and labor % of sales."
          reason="Awaiting Deputy reporting."
          icon={Percent}
        />
      ) : (
        <LockedTile />
      )}
      <AwaitingData
        label="Covers today"
        description="Today's covers, bookings, and no-shows."
        reason="Awaiting OpenTable reporting."
        icon={CalendarDays}
      />
      <AwaitingData
        label="Top sellers"
        description="Best-selling items and revenue mix."
        reason="Awaiting product-sales reporting."
        icon={Trophy}
      />
      <AwaitingData
        label="Food cost"
        description="Cost of goods, stock, and wastage."
        reason="Inventory not yet connected."
        icon={Utensils}
      />
    </div>
  );
}

function LockedTile() {
  return (
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
  );
}
