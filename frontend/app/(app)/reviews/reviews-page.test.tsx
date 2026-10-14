import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import ReviewsPage from "./page";
import type {
  AspectSentimentSummary,
  RatingBucket,
  ReviewOverview,
  ReviewView,
  SentimentBucket,
  SourceSummary,
} from "@/lib/api";

// The reviews page fetches six datasets through `useApiData`, which reads the Api
// from `useApi`. Mock the Api at the source (like the forecasting page test) so the
// real hook runs and each view settles against the fixtures below.
const mocks = vi.hoisted(() => {
  const api = {
    getReviewOverview: vi.fn(),
    getReviewSentimentTrend: vi.fn(),
    getReviewAspects: vi.fn(),
    getReviewRatings: vi.fn(),
    getReviewSources: vi.fn(),
    getReviews: vi.fn(),
  };
  return { api };
});

vi.mock("@/lib/demo-mode", () => ({
  useApi: () => mocks.api,
}));

const overview: ReviewOverview = {
  total: 6,
  averageRating: 3.6,
  positive: 3,
  neutral: 1,
  negative: 2,
  wouldRecommendPct: 83.3,
};

const trend: SentimentBucket[] = [
  { periodStart: "2026-09-29", positive: 2, neutral: 1, negative: 1 },
  { periodStart: "2026-10-06", positive: 1, neutral: 0, negative: 1 },
];

const aspects: AspectSentimentSummary[] = [
  { aspect: "service", mentions: 3, positive: 1, neutral: 0, negative: 2 },
  { aspect: "food", mentions: 2, positive: 0, neutral: 2, negative: 0 },
  { aspect: "value", mentions: 1, positive: 0, neutral: 0, negative: 1 },
];

const ratings: RatingBucket[] = [
  { stars: 1, given: 0, predicted: 0 },
  { stars: 2, given: 1, predicted: 0 },
  { stars: 3, given: 1, predicted: 1 },
  { stars: 4, given: 2, predicted: 3 },
  { stars: 5, given: 1, predicted: 1 },
];

const sources: SourceSummary[] = [
  { source: "OPENTABLE", total: 3, averageRating: 4.0, positive: 1, negative: 1 },
  { source: "GOOGLE", total: 2, averageRating: 2.0, positive: 1, negative: 1 },
];

const reviews: ReviewView[] = [
  {
    reviewId: "rv-1004",
    source: "OPENTABLE",
    reviewedAt: "2026-10-02T18:45:00Z",
    overallRating: 3,
    sentiment: "NEGATIVE",
    sentimentConfidence: 0.84,
    predictedRating: 3.1,
    comment: "Slow service and a long wait for mains.",
    guestName: "Dave R.",
    aspects: [{ aspect: "service", label: "NEGATIVE", confidence: 0.85 }],
  },
  {
    reviewId: "rv-1002",
    source: "OPENTABLE",
    reviewedAt: "2026-10-04T19:10:00Z",
    overallRating: 5,
    sentiment: "POSITIVE",
    sentimentConfidence: 0.97,
    predictedRating: 4.8,
    comment: "Faultless service from start to finish.",
    guestName: "James T.",
    aspects: [
      { aspect: "service", label: "POSITIVE", confidence: 0.96 },
      { aspect: "ambience", label: "POSITIVE", confidence: 0.88 },
    ],
  },
];

describe("ReviewsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.api.getReviewOverview.mockResolvedValue(overview);
    mocks.api.getReviewSentimentTrend.mockResolvedValue(trend);
    mocks.api.getReviewAspects.mockResolvedValue(aspects);
    mocks.api.getReviewRatings.mockResolvedValue(ratings);
    mocks.api.getReviewSources.mockResolvedValue(sources);
    mocks.api.getReviews.mockResolvedValue(reviews);
  });

  it("renders all six analytics views", async () => {
    render(<ReviewsPage />);

    // Overview KPI strip.
    expect(await screen.findByText("Total reviews")).toBeInTheDocument();
    expect(screen.getByText("Average rating")).toBeInTheDocument();
    // The five remaining views each own a titled section.
    expect(screen.getByText("Sentiment trend")).toBeInTheDocument();
    expect(screen.getByText("Aspects")).toBeInTheDocument();
    expect(screen.getByText("Ratings")).toBeInTheDocument();
    expect(screen.getByText("Sources")).toBeInTheDocument();
    expect(screen.getByText("Review explorer")).toBeInTheDocument();
  });

  it("shows the service aspect row with its negative count", async () => {
    render(<ReviewsPage />);

    const breakdown = await screen.findByTestId("aspect-breakdown");
    expect(within(breakdown).getByText("Service")).toBeInTheDocument();
    expect(within(breakdown).getByText("1 positive · 0 neutral · 2 negative")).toBeInTheDocument();
  });

  it("lists a review whose sentiment is NEGATIVE in the explorer", async () => {
    render(<ReviewsPage />);

    const explorer = await screen.findByTestId("review-explorer");
    expect(await within(explorer).findByText("Negative")).toBeInTheDocument();
    expect(within(explorer).getByText("Dave R.")).toBeInTheDocument();
  });

  it("shows an empty state when the overview total is zero", async () => {
    mocks.api.getReviewOverview.mockResolvedValue({ ...overview, total: 0 });
    render(<ReviewsPage />);

    expect(await screen.findByText("No reviews yet")).toBeInTheDocument();
  });
});
