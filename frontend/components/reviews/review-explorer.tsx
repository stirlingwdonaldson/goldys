"use client";

import { useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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

const columns: ColumnDef<ReviewView>[] = [
  {
    id: "reviewedAt",
    accessorFn: (r) => r.reviewedAt.slice(0, 10),
    header: ({ column }) => <DataTableColumnHeader column={column} title="Date" />,
    cell: ({ row }) => (
      <span className="whitespace-nowrap">{formatDay(row.original.reviewedAt.slice(0, 10))}</span>
    ),
    meta: { title: "Date" },
    enableHiding: false,
  },
  {
    id: "source",
    accessorFn: (r) => reviewSourceLabel(r.source),
    header: "Source",
    cell: ({ row }) => (
      <SourceLabel source={row.original.source}>{reviewSourceLabel(row.original.source)}</SourceLabel>
    ),
    meta: { title: "Source" },
  },
  {
    id: "rating",
    accessorFn: (r) => r.overallRating ?? -1,
    header: ({ column }) => <DataTableColumnHeader column={column} title="Rating" />,
    cell: ({ row }) => {
      const rating = row.original.overallRating;
      return rating != null ? (
        <span className="tabular-nums">{rating}★</span>
      ) : (
        <span className="text-muted-foreground">—</span>
      );
    },
    meta: { title: "Rating", align: "right" },
  },
  {
    id: "sentiment",
    accessorFn: (r) => r.sentiment ?? "",
    header: "Sentiment",
    cell: ({ row }) => <SentimentBadge sentiment={row.original.sentiment} />,
    meta: { title: "Sentiment" },
  },
  {
    id: "guest",
    accessorFn: (r) => r.guestName ?? "",
    header: "Guest",
    cell: ({ row }) => row.original.guestName ?? <span className="text-muted-foreground">—</span>,
    meta: { title: "Guest" },
  },
  {
    id: "comment",
    accessorFn: (r) => r.comment ?? "",
    header: "Comment",
    cell: ({ row }) => (
      <span className="block max-w-72 truncate" title={row.original.comment ?? undefined}>
        {row.original.comment ?? "—"}
      </span>
    ),
    meta: { title: "Comment" },
  },
];

/** The drilled-in review: full text, sub-ratings, and per-aspect sentiment. */
function ReviewDetail({ review, onClose }: { review: ReviewView; onClose: () => void }) {
  return (
    <div className="flex flex-col gap-4 rounded-xl border bg-card p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <SourceLabel source={review.source}>{reviewSourceLabel(review.source)}</SourceLabel>
            <SentimentBadge sentiment={review.sentiment} />
            <span className="text-xs text-muted-foreground">
              {formatDay(review.reviewedAt.slice(0, 10))}
            </span>
          </div>
          <span className="text-sm text-muted-foreground">{review.guestName ?? "Anonymous"}</span>
        </div>
        <Button variant="outline" size="sm" onClick={onClose}>
          Close
        </Button>
      </div>

      <blockquote className="border-l-2 border-muted pl-3 text-sm">
        {review.comment ?? "No comment"}
      </blockquote>

      <div className="grid grid-cols-3 gap-2">
        <div>
          <p className="text-xs text-muted-foreground">Overall rating</p>
          <p className="tabular-nums">
            {review.overallRating != null ? `${review.overallRating}★` : "—"}
          </p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">Predicted rating</p>
          <p className="tabular-nums">
            {review.predictedRating != null ? `${review.predictedRating.toFixed(1)}★` : "—"}
          </p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">Confidence</p>
          <p className="tabular-nums">{formatPercent(review.sentimentConfidence)}</p>
        </div>
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
    </div>
  );
}

/**
 * The review explorer: every resolved review in a filterable table, with a row
 * click drilling into the full text, sub-ratings and per-aspect sentiment.
 * The shared filter bar already scopes `getReviews`, so the table itself adds no
 * second text filter.
 */
export function ReviewExplorer({ reviews }: { reviews: ReviewView[] }) {
  const [selected, setSelected] = useState<ReviewView | null>(null);
  return (
    <div data-testid="review-explorer" className="flex flex-col gap-4">
      <DataTable
        columns={columns}
        data={reviews}
        getRowId={(r) => r.reviewId}
        initialSorting={[{ id: "reviewedAt", desc: true }]}
        onRowClick={(r) => setSelected(r)}
        isRowSelected={(r) => r.reviewId === selected?.reviewId}
        emptyMessage="No reviews match this filter."
      />
      {selected ? <ReviewDetail review={selected} onClose={() => setSelected(null)} /> : null}
    </div>
  );
}
