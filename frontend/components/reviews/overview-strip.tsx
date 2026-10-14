"use client";

import { StatCard } from "@/components/data-display/stat-card";
import { formatNumber } from "@/lib/format";
import type { ReviewOverview } from "@/lib/api";

/**
 * The headline KPI strip above the charts: volume, average rating, the sentiment
 * split, and the share of reviewers who would recommend. A missing value is never
 * shown as a zero — `formatNumber` renders an em dash for nulls.
 */
export function OverviewStrip({ overview }: { overview: ReviewOverview | null }) {
  if (!overview) return null;
  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-6">
      <StatCard label="Total reviews" value={formatNumber(overview.total)} />
      <StatCard label="Average rating" value={formatNumber(overview.averageRating, 1)} />
      <StatCard label="Positive" value={formatNumber(overview.positive)} />
      <StatCard label="Neutral" value={formatNumber(overview.neutral)} />
      <StatCard label="Negative" value={formatNumber(overview.negative)} />
      <StatCard
        label="Would recommend"
        value={`${formatNumber(overview.wouldRecommendPct, 1)}%`}
        hint="Reviewers who would recommend the venue"
      />
    </div>
  );
}
