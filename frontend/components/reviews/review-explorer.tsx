"use client";

import { useMemo, useState } from "react";
import { TablePagination, useClientPagination } from "@/components/data-table/table-pagination";
import { Badge } from "@/components/ui/badge";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { SourceLabel } from "@/components/sources/source-tile";
import { formatDay, formatPercent, humanizeKey } from "@/lib/format";
import type { ReviewView } from "@/lib/api";
import { reviewSourceLabel } from "./source-comparison";

const SENTIMENT_VARIANT: Record<string, "success" | "neutral" | "failed"> = {
  POSITIVE: "success",
  NEUTRAL: "neutral",
  NEGATIVE: "failed",
};

/** "NEGATIVE" → "Negative" (the enum is uppercase; the badge reads like a word). */
export function sentimentLabel(sentiment: string): string {
  const lower = sentiment.toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

function SentimentBadge({ sentiment }: { sentiment: string | null }) {
  if (!sentiment) return <span className="text-muted-foreground">—</span>;
  return (
    <Badge variant={SENTIMENT_VARIANT[sentiment] ?? "neutral"}>{sentimentLabel(sentiment)}</Badge>
  );
}

/** One nullable star sub-rating, rendered as a dash when absent. */
function SubRating({ label, value }: { label: string; value: number | null }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="tabular-nums">{value != null ? `${value}★` : "—"}</p>
    </div>
  );
}

/** Full review content stays visible while browsing. */
function ReviewDetail({ review }: { review: ReviewView }) {
  return (
    <article
      aria-label={`Review by ${review.guestName ?? "Anonymous"}`}
      className="flex flex-col gap-4 border-b p-5 last:border-b-0"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <SourceLabel source={review.source}>{reviewSourceLabel(review.source)}</SourceLabel>
            <SentimentBadge sentiment={review.sentiment} />
            <span className="text-xs text-muted-foreground">
              {formatDay(review.reviewedAt.slice(0, 10))}
            </span>
          </div>
          <h3 className="break-words text-sm font-semibold">{review.guestName ?? "Anonymous"}</h3>
        </div>
        <div className="shrink-0 text-right">
          <p className="text-xs text-muted-foreground">Overall rating</p>
          <p className="text-lg font-semibold tabular-nums">
            {review.overallRating != null ? `${review.overallRating}★` : "—"}
          </p>
        </div>
      </div>

      <blockquote className="whitespace-pre-wrap break-words text-sm leading-relaxed">
        {review.comment ?? "No comment"}
      </blockquote>

      <div className="grid grid-cols-4 gap-2">
        <SubRating label="Food" value={review.foodRating} />
        <SubRating label="Service" value={review.serviceRating} />
        <SubRating label="Ambience" value={review.ambienceRating} />
        <SubRating label="Value" value={review.valueRating} />
      </div>

      <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
        <span>
          Predicted rating: {review.predictedRating != null ? `${review.predictedRating.toFixed(1)}★` : "—"}
        </span>
        <span>Sentiment confidence: {formatPercent(review.sentimentConfidence)}</span>
      </div>

      {review.aspects.length > 0 ? (
        <div className="flex flex-col gap-1.5">
          <p className="text-xs font-medium uppercase text-muted-foreground">Aspects</p>
          <ul className="flex flex-wrap gap-2">
            {review.aspects.map((a) => (
              <li key={a.aspect} className="flex items-center gap-1.5">
                <Badge variant="outline">{humanizeKey(a.aspect)}</Badge>
                <SentimentBadge sentiment={a.label} />
                <span className="text-xs tabular-nums text-muted-foreground">
                  {formatPercent(a.confidence)}
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </article>
  );
}

/** Paginated full reviews, scoped by the page's shared filters. */
export function ReviewExplorer({ reviews }: { reviews: ReviewView[] }) {
  const [sort, setSort] = useState("newest");
  const sortedReviews = useMemo(
    () => [...reviews].sort((a, b) => {
      if (sort === "highest" || sort === "lowest") {
        if (a.overallRating == null) return b.overallRating == null ? 0 : 1;
        if (b.overallRating == null) return -1;
        return sort === "highest"
          ? b.overallRating - a.overallRating
          : a.overallRating - b.overallRating;
      }
      return sort === "oldest"
        ? a.reviewedAt.localeCompare(b.reviewedAt)
        : b.reviewedAt.localeCompare(a.reviewedAt);
    }),
    [reviews, sort],
  );
  const { pageRows, pagination } = useClientPagination(sortedReviews, sort);

  return (
    <div data-testid="review-explorer" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">{reviews.length} reviews</p>
        <Select value={sort} onValueChange={setSort}>
          <SelectTrigger aria-label="Sort reviews" className="w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="newest">Newest first</SelectItem>
            <SelectItem value="oldest">Oldest first</SelectItem>
            <SelectItem value="highest">Highest rated</SelectItem>
            <SelectItem value="lowest">Lowest rated</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {pageRows.length > 0 ? (
        <div className="min-w-0 rounded-xl border bg-card">
          {pageRows.map((review) => (
            <ReviewDetail key={review.reviewId} review={review} />
          ))}
        </div>
      ) : (
        <p className="py-10 text-center text-sm text-muted-foreground">No reviews match this filter.</p>
      )}
      <TablePagination {...pagination} />
    </div>
  );
}
