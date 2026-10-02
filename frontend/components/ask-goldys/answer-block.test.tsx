import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AnswerBlock } from "./answer-block";
import type { AnswerPayload } from "./types";

describe("AnswerBlock", () => {
  it("shows summary, trace, as-of, and notices", () => {
    const answer: AnswerPayload = {
      widgets: [],
      trace: [{ tool: "get_sales_by_period", description: "Resolved daily sales totals." }],
      asOf: "2026-10-02T10:00:00Z",
      notices: ["1 date(s) have no resolved total (unresolved conflict)."],
    };
    render(<AnswerBlock summary="Sales were $27,650.66." answer={answer} error={null} />);
    expect(screen.getByText(/sales were/i)).toBeInTheDocument();
    expect(screen.getByText(/get_sales_by_period/i)).toBeInTheDocument();
    expect(screen.getByText(/no resolved total/i)).toBeInTheDocument();
  });

  it("shows the error message when present", () => {
    render(<AnswerBlock summary="" answer={null} error="You don't have access to that data." />);
    // PermissionDenied renders both a heading and the message; assert the unique message text.
    expect(screen.getByText(/that data/i)).toBeInTheDocument();
  });
});
