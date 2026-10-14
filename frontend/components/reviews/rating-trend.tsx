"use client";

import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { EmptyState } from "@/components/states/empty-state";
import { AXIS_TICK, CHART_TOOLTIP_PROPS, formatXTick } from "@/components/widgets/chart-style";
import type { RatingTrendBucket } from "@/lib/api";

/** Avg rating in the primary (good) hue, recommend share in a secondary hue (red kept for "bad"). */
const RATING_COLOR = "hsl(var(--chart-1))";
const RECOMMEND_COLOR = "hsl(var(--chart-3))";

function tooltipFormatter(value: unknown, name: unknown): [string, string] {
  if (name === "Would recommend") return [`${value}%`, "Would recommend"];
  return [`${value}`, String(name)];
}

/**
 * Weekly average rating (0–5, left axis) and the share of reviewers who would recommend
 * (0–100%, right axis), so the two reputation signals read against the same weeks.
 */
export function RatingTrend({ buckets }: { buckets: RatingTrendBucket[] }) {
  if (buckets.length === 0) {
    return (
      <EmptyState
        title="No rating data"
        description="Average rating and recommend share appear once reviews are resolved."
      />
    );
  }
  return (
    <div role="img" aria-label="Average rating and recommend share over time" className="h-56 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={buckets} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
          <XAxis
            dataKey="periodStart"
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            minTickGap={16}
            tickFormatter={formatXTick}
          />
          <YAxis yAxisId="rating" domain={[0, 5]} tick={AXIS_TICK} tickLine={false} axisLine={false} width={40} />
          <YAxis
            yAxisId="recommend"
            orientation="right"
            domain={[0, 100]}
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            width={44}
            tickFormatter={(v: number) => `${v}%`}
          />
          <Tooltip {...CHART_TOOLTIP_PROPS} labelFormatter={formatXTick} formatter={tooltipFormatter} />
          <Legend />
          <Line
            yAxisId="rating"
            type="monotone"
            dataKey="avgRating"
            name="Avg rating"
            stroke={RATING_COLOR}
            strokeWidth={2}
            dot={false}
          />
          <Line
            yAxisId="recommend"
            type="monotone"
            dataKey="recommendPercent"
            name="Would recommend"
            stroke={RECOMMEND_COLOR}
            strokeWidth={2}
            dot={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
