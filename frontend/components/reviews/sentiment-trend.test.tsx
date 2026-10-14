import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SentimentTrend, negativeTrendSignal } from "./sentiment-trend";
import type { SentimentBucket } from "@/lib/api";

function bucket(periodStart: string, positive: number, neutral: number, negative: number): SentimentBucket {
  return { periodStart, positive, neutral, negative };
}

describe("negativeTrendSignal", () => {
  it("is insufficient with fewer than two buckets", () => {
    expect(negativeTrendSignal([])).toBe("insufficient");
    expect(negativeTrendSignal([bucket("2026-10-06", 2, 1, 1)])).toBe("insufficient");
  });

  it("is insufficient when the latest bucket has no reviews", () => {
    expect(
      negativeTrendSignal([bucket("2026-09-29", 2, 1, 1), bucket("2026-10-06", 0, 0, 0)]),
    ).toBe("insufficient");
  });

  it("reports down when the negative share falls", () => {
    // 1/3 negative → 1/6 negative: the share falls, so the trend is "down" (improving).
    expect(
      negativeTrendSignal([bucket("2026-09-29", 1, 1, 1), bucket("2026-10-06", 4, 1, 1)]),
    ).toBe("down");
  });

  it("reports up when the negative share rises", () => {
    // 1/6 negative → 1/3 negative: the share rises, so the trend is "up" (declining).
    expect(
      negativeTrendSignal([bucket("2026-09-29", 4, 1, 1), bucket("2026-10-06", 1, 1, 1)]),
    ).toBe("up");
  });

  it("reports steady when the negative share is unchanged", () => {
    expect(
      negativeTrendSignal([bucket("2026-09-29", 2, 1, 1), bucket("2026-10-06", 4, 2, 2)]),
    ).toBe("steady");
  });
});

describe("SentimentTrend", () => {
  it("renders an improving indicator when negative sentiment is falling", () => {
    render(
      <SentimentTrend
        buckets={[bucket("2026-09-29", 1, 1, 1), bucket("2026-10-06", 4, 1, 1)]}
      />,
    );
    expect(screen.getByText("Negative sentiment trending down")).toBeInTheDocument();
  });

  it("renders a declining indicator when negative sentiment is rising", () => {
    render(
      <SentimentTrend
        buckets={[bucket("2026-09-29", 4, 1, 1), bucket("2026-10-06", 1, 1, 1)]}
      />,
    );
    expect(screen.getByText("Negative sentiment trending up")).toBeInTheDocument();
  });

  it("renders an insufficient-data note for a single bucket", () => {
    render(<SentimentTrend buckets={[bucket("2026-10-06", 2, 1, 1)]} />);
    expect(screen.getByText("Not enough data yet to compare trends")).toBeInTheDocument();
  });
});
