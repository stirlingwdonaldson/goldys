import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ActivityChart } from "./activity-chart";
import type { ActivityPoint } from "@/lib/api";

function point(date: string, clean: number, failed: number): ActivityPoint {
  return { date, clean, failed };
}

describe("ActivityChart", () => {
  it("renders one labelled bar per day and the day labels", () => {
    render(
      <ActivityChart
        points={[
          point("2026-09-21", 5, 0),
          point("2026-09-22", 4, 1),
        ]}
      />,
    );

    expect(screen.getByRole("img", { name: /connector runs per day/i })).toBeInTheDocument();
    expect(screen.getByTitle("2026-09-21: 5 clean, 0 failed")).toBeInTheDocument();
    expect(screen.getByTitle("2026-09-22: 4 clean, 1 failed")).toBeInTheDocument();
    expect(screen.getByText("21")).toBeInTheDocument();
    expect(screen.getByText("22")).toBeInTheDocument();
  });

  it("shows an empty message when there is no activity", () => {
    render(<ActivityChart points={[]} />);

    expect(screen.getByText(/no activity yet/i)).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("renders a muted placeholder for zero-activity days", () => {
    render(<ActivityChart points={[point("2026-09-21", 0, 0), point("2026-09-22", 3, 0)]} />);

    expect(screen.getByTitle("2026-09-21: 0 clean, 0 failed")).toBeInTheDocument();
    expect(screen.getByTitle("2026-09-22: 3 clean, 0 failed")).toBeInTheDocument();
  });
});
