import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AwaitingData } from "./awaiting-data";

describe("AwaitingData", () => {
  it("renders label, description, and reason without any fabricated value", () => {
    render(
      <AwaitingData
        label="Sales"
        description="Net sales today and this week vs. last week."
        reason="Awaiting sales-reporting endpoint."
      />,
    );

    expect(screen.getByText("Sales")).toBeInTheDocument();
    expect(screen.getByText("Net sales today and this week vs. last week.")).toBeInTheDocument();
    expect(screen.getByText("Awaiting sales-reporting endpoint.")).toBeInTheDocument();
    // Never renders a numeric/currency value that would imply operational data.
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
    expect(screen.queryByText(/\d/)).not.toBeInTheDocument();
  });
});
