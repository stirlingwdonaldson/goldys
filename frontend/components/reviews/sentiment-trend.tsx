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
import { Badge } from "@/components/ui/badge";
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

/** The direction of the negative-sentiment share between the two most recent buckets. */
type NegativeTrend = "down" | "up" | "steady" | "insufficient";

/** Negative share (0..1) of a bucket, or null when the bucket has no reviews to divide by. */
function negativeShare(bucket: SentimentBucket): number | null {
  const total = bucket.positive + bucket.neutral + bucket.negative;
  if (total === 0) return null;
  return bucket.negative / total;
}

/**
 * Compares the most recent bucket's negative share against the previous bucket's (spec §7.2). A
 * falling negative share is "down" (improving); a rising share is "up" (declining). Fewer than two
 * buckets — or a bucket with no reviews — yields "insufficient" so we never claim a trend from
 * nothing.
 */
export function negativeTrendSignal(buckets: SentimentBucket[]): NegativeTrend {
  if (buckets.length < 2) return "insufficient";
  const previous = negativeShare(buckets[buckets.length - 2]);
  const latest = negativeShare(buckets[buckets.length - 1]);
  if (previous === null || latest === null) return "insufficient";
  if (latest < previous) return "down";
  if (latest > previous) return "up";
  return "steady";
}

const TREND_LABEL: Record<NegativeTrend, { text: string; variant?: "success" | "failed" | "neutral" }> = {
  down: { text: "Negative sentiment trending down", variant: "success" },
  up: { text: "Negative sentiment trending up", variant: "failed" },
  steady: { text: "Negative sentiment steady", variant: "neutral" },
  insufficient: { text: "Not enough data yet to compare trends" },
};

function NegativeTrendIndicator({ signal }: { signal: NegativeTrend }) {
  const { text, variant } = TREND_LABEL[signal];
  if (!variant) {
    return <p className="text-xs text-muted-foreground">{text}</p>;
  }
  return (
    <Badge variant={variant}>
      <span aria-hidden>{signal === "down" ? "↓" : signal === "up" ? "↑" : "→"}</span>
      {text}
    </Badge>
  );
}

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
  const signal = negativeTrendSignal(buckets);
  return (
    <div className="flex flex-col gap-2">
      <NegativeTrendIndicator signal={signal} />
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
    </div>
  );
}
