import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RecomputeBanner } from "./recompute-banner";
import type { RecomputeStatus } from "@/lib/api";

function status(state: RecomputeStatus["state"], lastChangedAt: string | null = null): RecomputeStatus {
  return { state, lastChangedAt };
}

describe("RecomputeBanner", () => {
  it("shows the last-changed time when complete", () => {
    render(<RecomputeBanner status={status("complete", "2026-09-30T08:00:00Z")} />);
    expect(screen.getByText(/rules apply immediately/i)).toBeInTheDocument();
    // The rendered timestamp is locale-formatted, so assert on the year only.
    expect(screen.getByText(/2026/i)).toBeInTheDocument();
  });

  it("warns while recomputing", () => {
    render(<RecomputeBanner status={status("recomputing")} />);
    expect(screen.getByText(/recomputing/i)).toBeInTheDocument();
  });

  it("treats failed distinctly from complete", () => {
    render(<RecomputeBanner status={status("failed")} />);
    expect(screen.getByText(/failed/i)).toBeInTheDocument();
  });

  it("renders an idle note when never completed", () => {
    render(<RecomputeBanner status={status("idle")} />);
    expect(screen.getByText(/never/i)).toBeInTheDocument();
  });
});
