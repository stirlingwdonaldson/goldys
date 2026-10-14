"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { EmptyState } from "@/components/states/empty-state";
import { AXIS_TICK, CHART_TOOLTIP_PROPS } from "@/components/widgets/chart-style";
import type { RatingBucket } from "@/lib/api";

/**
 * The star histogram: the stars guests actually gave against the stars the
 * classifier predicted, so a systematic over/under-prediction reads at a glance.
 */
export function RatingDistribution({ buckets }: { buckets: RatingBucket[] }) {
  if (buckets.length === 0) {
    return (
      <EmptyState
        title="No rating data"
        description="Star counts appear once reviews are resolved."
      />
    );
  }
  return (
    <div role="img" aria-label="Rating distribution: given vs predicted" className="h-56 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={buckets} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
          <XAxis
            dataKey="stars"
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            tickFormatter={(v: number) => `${v}★`}
          />
          <YAxis tick={AXIS_TICK} tickLine={false} axisLine={false} width={40} allowDecimals={false} />
          <Tooltip {...CHART_TOOLTIP_PROPS} cursor={{ fill: "hsl(var(--muted))" }} />
          <Legend />
          <Bar dataKey="given" name="Given" fill="hsl(var(--chart-1))" maxBarSize={22} radius={[4, 4, 0, 0]} />
          <Bar dataKey="predicted" name="Predicted" fill="hsl(var(--chart-3))" maxBarSize={22} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
