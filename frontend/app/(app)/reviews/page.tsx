"use client";

import { useMemo, useState, type ReactNode } from "react";
import { useApiData } from "@/lib/use-api-data";
import type { ReviewFilter } from "@/lib/api";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { EmptyState } from "@/components/states/empty-state";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { Input } from "@/components/ui/input";
import { humanizeKey } from "@/lib/format";
import { OverviewStrip } from "@/components/reviews/overview-strip";
import { SentimentTrend } from "@/components/reviews/sentiment-trend";
import { AspectBreakdown } from "@/components/reviews/aspect-breakdown";
import { RatingDistribution } from "@/components/reviews/rating-distribution";
import { SourceComparison, reviewSourceLabel } from "@/components/reviews/source-comparison";
import { ReviewExplorer, sentimentLabel } from "@/components/reviews/review-explorer";

/** Trailing 30-day window, so the screen renders without the operator touching a date input. */
function defaultRange(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(Date.UTC(to.getUTCFullYear(), to.getUTCMonth(), to.getUTCDate() - 29));
  return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
}

const SENTIMENTS = ["POSITIVE", "NEUTRAL", "NEGATIVE"] as const;

/**
 * Reviews analytics: one shared filter bar drives six views. The date range feeds
 * every aggregate endpoint; source/sentiment/aspect/search additionally scope the
 * review explorer's list (the only endpoint that accepts those filters).
 */
export default function ReviewsPage() {
  const [range, setRange] = useState(defaultRange);
  const [source, setSource] = useState("");
  const [sentiment, setSentiment] = useState("");
  const [aspect, setAspect] = useState("");
  const [query, setQuery] = useState("");

  const overview = useApiData((api) => api.getReviewOverview(range.from, range.to), [range.from, range.to]);
  const trend = useApiData((api) => api.getReviewSentimentTrend(range.from, range.to), [range.from, range.to]);
  const aspects = useApiData((api) => api.getReviewAspects(range.from, range.to), [range.from, range.to]);
  const ratings = useApiData((api) => api.getReviewRatings(range.from, range.to), [range.from, range.to]);
  const sources = useApiData((api) => api.getReviewSources(range.from, range.to), [range.from, range.to]);

  const reviewFilter = useMemo<ReviewFilter>(
    () => ({
      from: range.from || undefined,
      to: range.to || undefined,
      source: source || undefined,
      sentiment: sentiment || undefined,
      aspect: aspect || undefined,
      query: query || undefined,
    }),
    [range.from, range.to, source, sentiment, aspect, query],
  );
  const reviews = useApiData((api) => api.getReviews(reviewFilter), [
    range.from,
    range.to,
    source,
    sentiment,
    aspect,
    query,
  ]);

  // The filter bar's source/aspect options derive from the aggregate data already on screen.
  const sourceOptions = sources.data ?? [];
  const aspectOptions = aspects.data ?? [];

  const header = (
    <PageHeader
      title="Reviews"
      description="Guest sentiment, ratings and themes across OpenTable, Google and TripAdvisor."
    />
  );

  const filterBar = (
    <div className="flex flex-wrap items-end gap-3">
      <Field label="From" htmlFor="reviews-from">
        <Input
          id="reviews-from"
          type="date"
          value={range.from}
          max={range.to}
          onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))}
          className="w-44"
        />
      </Field>
      <Field label="To" htmlFor="reviews-to">
        <Input
          id="reviews-to"
          type="date"
          value={range.to}
          min={range.from}
          onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))}
          className="w-44"
        />
      </Field>
      <Field label="Source" htmlFor="reviews-source">
        <select
          id="reviews-source"
          aria-label="Source"
          value={source}
          onChange={(e) => setSource(e.target.value)}
          className="h-10 rounded-md border border-input bg-background px-2 text-sm"
        >
          <option value="">All sources</option>
          {sourceOptions.map((s) => (
            <option key={s.source} value={s.source}>
              {reviewSourceLabel(s.source)}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Sentiment" htmlFor="reviews-sentiment">
        <select
          id="reviews-sentiment"
          aria-label="Sentiment"
          value={sentiment}
          onChange={(e) => setSentiment(e.target.value)}
          className="h-10 rounded-md border border-input bg-background px-2 text-sm"
        >
          <option value="">All sentiments</option>
          {SENTIMENTS.map((s) => (
            <option key={s} value={s}>
              {sentimentLabel(s)}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Aspect" htmlFor="reviews-aspect">
        <select
          id="reviews-aspect"
          aria-label="Aspect"
          value={aspect}
          onChange={(e) => setAspect(e.target.value)}
          className="h-10 rounded-md border border-input bg-background px-2 text-sm"
        >
          <option value="">All aspects</option>
          {aspectOptions.map((a) => (
            <option key={a.aspect} value={a.aspect}>
              {humanizeKey(a.aspect)}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Search" htmlFor="reviews-search">
        <Input
          id="reviews-search"
          aria-label="Search reviews"
          placeholder="Search comments or guests"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="w-56"
        />
      </Field>
    </div>
  );

  if (overview.loading) {
    return (
      <div className="flex flex-col gap-6">
        {header}
        {filterBar}
        <LoadingState rows={5} />
      </div>
    );
  }

  if (overview.error) {
    return (
      <div className="flex flex-col gap-6">
        {header}
        {filterBar}
        <ErrorState
          title="Couldn't load reviews"
          message={overview.error.message}
          onRetry={overview.reload}
        />
      </div>
    );
  }

  const overviewData = overview.data;

  if (overviewData && overviewData.total === 0) {
    return (
      <div className="flex flex-col gap-6">
        {header}
        {filterBar}
        <EmptyState
          title="No reviews yet"
          description="Guest reviews appear here once OpenTable and Google send their data."
        />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {header}
      {filterBar}

      <OverviewStrip overview={overviewData} />

      <Section
        title="Sentiment trend"
        description="Positive, neutral and negative reviews over time, stacked per week."
      >
        <SentimentTrend buckets={trend.data ?? []} />
      </Section>

      <Section
        title="Aspects"
        description="How often each theme is mentioned, and the sentiment behind it."
      >
        <AspectBreakdown aspects={aspectOptions} />
      </Section>

      <Section
        title="Ratings"
        description="The stars guests gave against the classifier's prediction."
      >
        <RatingDistribution buckets={ratings.data ?? []} />
      </Section>

      <Section title="Sources" description="Reviews and average rating per source.">
        <SourceComparison sources={sourceOptions} />
      </Section>

      <Section
        title="Review explorer"
        description="Every review, scoped by the filters above — click a row for the full text and per-aspect sentiment."
      >
        <ReviewExplorer reviews={reviews.data ?? []} />
      </Section>
    </div>
  );
}

/** A labelled filter input, matching the Sales-detail date filter's layout. */
function Field({
  label,
  htmlFor,
  children,
}: {
  label: string;
  htmlFor: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={htmlFor} className="text-xs text-muted-foreground">
        {label}
      </label>
      {children}
    </div>
  );
}
