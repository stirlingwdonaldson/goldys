"use client";

import { EmptyState } from "@/components/states/empty-state";
import { humanizeKey } from "@/lib/format";
import type { AspectSentimentSummary } from "@/lib/api";

/** Matches the sentiment colours in `sentiment-trend.tsx`. */
const SENTIMENT_COLORS = {
  positive: "hsl(var(--chart-1))",
  neutral: "hsl(var(--chart-4))",
  negative: "hsl(var(--chart-2))",
};

/**
 * Aspect frequency × sentiment: one row per theme, a stacked bar showing how the
 * mentions split by sentiment, and the exact counts alongside so the numbers are
 * readable without hovering.
 */
export function AspectBreakdown({ aspects }: { aspects: AspectSentimentSummary[] }) {
  if (aspects.length === 0) {
    return (
      <EmptyState
        title="No aspect data"
        description="Aspect sentiment appears once reviews are classified."
      />
    );
  }
  return (
    <ul data-testid="aspect-breakdown" className="flex flex-col gap-2.5" aria-label="Review aspects">
      {aspects.map((a) => {
        const total = a.positive + a.neutral + a.negative;
        const pct = (n: number) => `${total === 0 ? 0 : (n / total) * 100}%`;
        return (
          <li key={a.aspect} className="flex items-center gap-3 text-sm">
            <span className="w-24 shrink-0 font-medium">{humanizeKey(a.aspect)}</span>
            <span className="flex h-2 min-w-0 flex-1 overflow-hidden rounded-full" aria-hidden="true">
              <span style={{ width: pct(a.positive), backgroundColor: SENTIMENT_COLORS.positive }} />
              <span style={{ width: pct(a.neutral), backgroundColor: SENTIMENT_COLORS.neutral }} />
              <span style={{ width: pct(a.negative), backgroundColor: SENTIMENT_COLORS.negative }} />
            </span>
            <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
              {a.positive} positive · {a.neutral} neutral · {a.negative} negative
            </span>
          </li>
        );
      })}
    </ul>
  );
}
