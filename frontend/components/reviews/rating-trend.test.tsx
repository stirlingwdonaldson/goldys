import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RatingTrend } from "./rating-trend";
import type { RatingTrendBucket } from "@/lib/api";

function bucket(periodStart: string, avgRating: number | null, recommendPercent: number | null): RatingTrendBucket {
  return { periodStart, avgRating, recommendPercent };
}

describe("RatingTrend", () => {
  it("renders an empty state when there are no buckets", () => {
    render(<RatingTrend buckets={[]} />);
    expect(screen.getByText("No rating data")).toBeInTheDocument();
  });

  it("renders the chart for non-empty buckets", () => {
    render(
      <RatingTrend
        buckets={[
          bucket("2026-09-29", 3.5, 66.7),
          bucket("2026-10-06", 4.0, 100.0),
        ]}
      />,
    );
    expect(
      screen.getByRole("img", { name: "Average rating and recommend share over time" }),
    ).toBeInTheDocument();
  });
});
