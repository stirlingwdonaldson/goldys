import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ActivityChart } from "./activity-chart";
import type { ActivityPoint } from "@/lib/api";

function point(date: string, clean: number, failed: number): ActivityPoint {
  return { date, clean, failed };
}

describe("ActivityChart", () => {
  it("renders the title for a non-empty series", () => {
    render(
      <ActivityChart
        points={[point("2026-09-21", 5, 0), point("2026-09-22", 4, 1)]}
      />,
    );
    expect(screen.getByText("Connector activity")).toBeInTheDocument();
  });

  it("uses a caller-provided title", () => {
    render(<ActivityChart title="Run activity · last 14 days" points={[]} />);
    expect(screen.getByText("Run activity · last 14 days")).toBeInTheDocument();
  });

  it("shows an empty message when there is no activity", () => {
    render(<ActivityChart points={[]} />);
    expect(screen.getByText(/no data yet/i)).toBeInTheDocument();
  });
});
