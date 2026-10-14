"use client";

import { EmptyState } from "@/components/states/empty-state";
import { SourceTile } from "@/components/sources/source-tile";
import { formatNumber } from "@/lib/format";
import type { SourceSummary } from "@/lib/api";

/**
 * Human label for a review source code. The source registry (`lib/sources.ts`)
 * falls back to the raw code for sources it does not know (Google, TripAdvisor),
 * so review sources get their own friendly names here.
 */
export function reviewSourceLabel(source: string): string {
  const known: Record<string, string> = {
    OPENTABLE: "OpenTable",
    GOOGLE: "Google",
    TRIPADVISOR: "TripAdvisor",
  };
  return known[source] ?? source;
}

/** OpenTable vs Google vs TripAdvisor: volume, average rating, and the sentiment split. */
export function SourceComparison({ sources }: { sources: SourceSummary[] }) {
  if (sources.length === 0) {
    return (
      <EmptyState
        title="No source data"
        description="Per-source figures appear once reviews are resolved."
      />
    );
  }
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b text-left text-xs uppercase text-muted-foreground">
          <th className="py-1.5 pr-4 font-medium">Source</th>
          <th className="py-1.5 pr-4 text-right font-medium">Reviews</th>
          <th className="py-1.5 pr-4 text-right font-medium">Avg rating</th>
          <th className="py-1.5 pr-4 text-right font-medium">Positive</th>
          <th className="py-1.5 text-right font-medium">Negative</th>
        </tr>
      </thead>
      <tbody>
        {sources.map((s) => (
          <tr key={s.source} className="border-b border-muted">
            <td className="py-1.5 pr-4">
              <span className="inline-flex items-center gap-1.5">
                <SourceTile source={s.source} />
                {reviewSourceLabel(s.source)}
              </span>
            </td>
            <td className="py-1.5 pr-4 text-right tabular-nums">{s.total}</td>
            <td className="py-1.5 pr-4 text-right tabular-nums">{formatNumber(s.averageRating, 1)}</td>
            <td className="py-1.5 pr-4 text-right tabular-nums">{s.positive}</td>
            <td className="py-1.5 text-right tabular-nums">{s.negative}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
