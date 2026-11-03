import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ReviewView } from "@/lib/api";
import { ReviewExplorer } from "./review-explorer";

function review(index: number, overrides: Partial<ReviewView> = {}): ReviewView {
  return {
    reviewId: `review-${index}`,
    source: "OPENTABLE",
    reviewedAt: `2026-10-${String(index + 1).padStart(2, "0")}T12:00:00Z`,
    guestName: `Guest ${index}`,
    comment: `Full review ${index}\nWith a second line.`,
    overallRating: 4,
    foodRating: 5,
    serviceRating: 3,
    ambienceRating: 4,
    valueRating: 4,
    predictedRating: 4.2,
    sentiment: "POSITIVE",
    sentimentConfidence: 0.9,
    aspects: [{ aspect: "service", label: "POSITIVE", confidence: 0.8 }],
    ...overrides,
  };
}

const reviews = Array.from({ length: 12 }, (_, index) => review(index));

describe("ReviewExplorer", () => {
  it("shows full comments, ratings and aspects without opening a review", () => {
    render(<ReviewExplorer reviews={[review(0)]} />);
    const detail = screen.getByRole("article", { name: "Review by Guest 0" });
    expect(within(detail).getByText(/Full review 0/)).toHaveTextContent("With a second line.");
    expect(within(detail).getByText("Food")).toBeInTheDocument();
    expect(within(detail).getByText("5★")).toBeInTheDocument();
    expect(within(detail).getByText("Aspects")).toBeInTheDocument();
    expect(within(detail).getByText("80%")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Close" })).not.toBeInTheDocument();
  });

  it("pages through full reviews in newest-first order", () => {
    render(<ReviewExplorer reviews={reviews} />);
    expect(screen.getAllByRole("article")).toHaveLength(10);
    expect(screen.getAllByRole("article")[0]).toHaveAccessibleName("Review by Guest 11");
    expect(screen.queryByText("Guest 0")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    expect(screen.getAllByRole("article")).toHaveLength(2);
    expect(screen.getByText("Guest 0")).toBeInTheDocument();
    expect(screen.getByText("11–12 of 12")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next page" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Previous page" }));
    expect(screen.getAllByRole("article")).toHaveLength(10);
  });

  it("clamps the page when results shrink and handles missing fields", () => {
    const { rerender } = render(<ReviewExplorer reviews={reviews} />);
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    rerender(<ReviewExplorer reviews={[review(0, {
      guestName: null, comment: null, overallRating: null, foodRating: null,
      serviceRating: null, ambienceRating: null, valueRating: null,
      predictedRating: null, sentiment: null, aspects: [],
    })]} />);
    expect(screen.getByRole("article", { name: "Review by Anonymous" })).toBeInTheDocument();
    expect(screen.getByText("No comment")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Next page" })).not.toBeInTheDocument();
    rerender(<ReviewExplorer reviews={[]} />);
    expect(screen.getByText("No reviews match this filter.")).toBeInTheDocument();
  });

  it("returns to the first page on sort and keeps unrated reviews last", () => {
    render(<ReviewExplorer reviews={[
      ...reviews,
      review(12, { overallRating: null }),
      review(13, { overallRating: 1 }),
    ]} />);
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    fireEvent.click(screen.getByRole("combobox", { name: "Sort reviews" }));
    fireEvent.click(screen.getByRole("option", { name: "Lowest rated" }));
    expect(screen.getAllByRole("article")[0]).toHaveAccessibleName("Review by Guest 13");
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Last page" }));
    expect(screen.getAllByRole("article").at(-1)).toHaveAccessibleName("Review by Guest 12");
  });
});
