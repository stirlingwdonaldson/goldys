import { CalendarDays, Percent, Scale, TrendingUp, Trophy, Utensils } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { AwaitingData } from "@/components/states/awaiting-data";
import { OwnerOnlyTile } from "@/components/states/owner-only";
import { StatCard, type StatDelta } from "@/components/data-display/stat-card";
import { formatCurrency, formatDay, toNumber } from "@/lib/format";
import { isOwner } from "@/lib/roles";
import type { SalesTrendPoint } from "@/lib/api";

interface BusinessKpiGridProps {
  /** Current user's seniority; gates the Owner-only labour tile. */
  seniority?: string;
  /**
   * The latest resolved daily-sales total, or null when there is no data yet. When present but
   * with a null `total`, the latest date is still unresolved and needs a decision.
   */
  latestSales?: { date: string | null; total: number | null } | null;
  /** Recent resolved daily totals, for the Sales tile's sparkline and week-on-week delta. */
  salesTrend?: SalesTrendPoint[];
}

// Definition from docs/metrics/catalog.md (`sales.gross`).
const GROSS_SALES_HELP =
  "Resolved gross sales including GST: the reconciled daily total, after any conflicting sources have been decided.";

/**
 * Compares the latest day against the same weekday a week earlier, when both are
 * resolved. Pubs trade very differently across the week, so day-on-day would mislead.
 */
function weekOnWeek(latest: { date: string | null; total: number | null }, trend: SalesTrendPoint[]): StatDelta | undefined {
  if (!latest.date || latest.total == null) return undefined;
  const d = new Date(`${latest.date}T00:00:00`);
  d.setDate(d.getDate() - 7);
  const prior = d.toISOString().slice(0, 10);
  const prev = toNumber(trend.find((p) => p.date === prior)?.total ?? null);
  if (prev == null || prev === 0) return undefined;
  const pct = ((latest.total - prev) / prev) * 100;
  const direction = Math.abs(pct) < 0.05 ? "flat" : pct > 0 ? "up" : "down";
  return {
    label: `${Math.abs(pct).toFixed(1)}% vs ${formatDay(prior)}`,
    direction,
    tone: direction === "flat" ? "neutral" : direction === "up" ? "good" : "bad",
  };
}

/**
 * The dashboard's five business KPI tiles. The Sales tile shows the latest resolved daily total
 * once data lands; the rest are placeholders until their reporting data lands.
 */
export function BusinessKpiGrid({ seniority, latestSales, salesTrend = [] }: BusinessKpiGridProps) {
  const isOwnerRole = isOwner(seniority);
  const unresolvedDays = salesTrend.filter((p) => p.total == null).length;
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
      {latestSales == null ? (
        <AwaitingData
          label="Sales"
          description="Gross sales today and this week vs. last week."
          reason="Awaiting sales-reporting endpoint."
          icon={TrendingUp}
        />
      ) : latestSales.total == null ? (
        <AwaitingData
          label="Sales"
          description="The latest day's totals disagree across sources."
          reason="Needs decision in Reconciliation."
          icon={Scale}
        />
      ) : (
        <StatCard
          label="Sales"
          value={formatCurrency(latestSales.total)}
          hint={latestSales.date ? `Gross, ${formatDay(latestSales.date)}` : undefined}
          help={GROSS_SALES_HELP}
          delta={weekOnWeek(latestSales, salesTrend)}
          spark={salesTrend.length > 1 ? salesTrend.map((p) => toNumber(p.total)) : undefined}
          footer={
            // Never hide that the trend includes undecided days (heuristic 5).
            unresolvedDays > 0 ? (
              <Badge variant="conflict">
                {unresolvedDays} {unresolvedDays === 1 ? "day" : "days"} disputed
              </Badge>
            ) : salesTrend.length > 0 ? (
              <Badge variant="success">Reconciled</Badge>
            ) : null
          }
        />
      )}
      {isOwnerRole ? (
        <AwaitingData
          label="Labour cost %"
          description="Scheduled vs. actual hours and labour % of sales."
          reason="Awaiting Deputy reporting."
          icon={Percent}
        />
      ) : (
        <OwnerOnlyTile label="Labour cost %" />
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
