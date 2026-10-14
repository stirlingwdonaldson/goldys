"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { EmptyState } from "@/components/states/empty-state";
import { AXIS_TICK, CHART_TOOLTIP_PROPS, formatXTick } from "@/components/widgets/chart-style";
import type { SentimentBucket } from "@/lib/api";

/**
 * Sentiment colours: green for positive (chart-1), teal for neutral (chart-4),
 * red for negative (chart-2) — the same "good first, red kept for bad" convention
 * as `widgets/chart-style.ts`.
 */
const SENTIMENT_COLORS = {
  positive: "hsl(var(--chart-1))",
  neutral: "hsl(var(--chart-4))",
  negative: "hsl(var(--chart-2))",
};

/** Weekly positive/neutral/negative counts, stacked so the bar height is the week's volume. */
export function SentimentTrend({ buckets }: { buckets: SentimentBucket[] }) {
  if (buckets.length === 0) {
    return (
      <EmptyState
        title="No sentiment data"
        description="Sentiment counts appear once reviews are resolved."
      />
    );
  }
  return (
    <div role="img" aria-label="Sentiment trend over time" className="h-56 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={buckets} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
          <XAxis
            dataKey="periodStart"
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            minTickGap={16}
            tickFormatter={formatXTick}
          />
          <YAxis
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            width={40}
            allowDecimals={false}
          />
          <Tooltip {...CHART_TOOLTIP_PROPS} labelFormatter={formatXTick} cursor={{ fill: "hsl(var(--muted))" }} />
          <Bar dataKey="positive" name="Positive" stackId="a" fill={SENTIMENT_COLORS.positive} maxBarSize={28} />
          <Bar dataKey="neutral" name="Neutral" stackId="a" fill={SENTIMENT_COLORS.neutral} maxBarSize={28} />
          <Bar
            dataKey="negative"
            name="Negative"
            stackId="a"
            fill={SENTIMENT_COLORS.negative}
            maxBarSize={28}
            radius={[4, 4, 0, 0]}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
