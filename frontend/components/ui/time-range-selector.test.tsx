import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { TimeRangeSelector, presetRange, defaultRange } from "./time-range-selector";

function iso(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

describe("presetRange", () => {
  const today = new Date(2026, 9, 14); // 14 Oct 2026, local

  it("returns inclusive windows ending today", () => {
    expect(presetRange("today", today)).toEqual({ from: "2026-10-14", to: "2026-10-14" });
    expect(presetRange("week", today)).toEqual({ from: "2026-10-08", to: "2026-10-14" });
    expect(presetRange("month", today)).toEqual({ from: "2026-09-15", to: "2026-10-14" });
    expect(presetRange("year", today)).toEqual({ from: "2025-10-15", to: "2026-10-14" });
  });
});

describe("defaultRange", () => {
  it("is the trailing 30-day window", () => {
    expect(defaultRange(new Date(2026, 9, 14))).toEqual({ from: "2026-09-15", to: "2026-10-14" });
  });
});

describe("TimeRangeSelector", () => {
  it("renders the four presets and reports a preset click with its range", () => {
    const onChange = vi.fn();
    render(<TimeRangeSelector value={{ from: "2026-01-01", to: "2026-01-01" }} onChange={onChange} />);

    expect(screen.getByRole("button", { name: "Today" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Past week" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Past month" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Past year" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Past week" }));

    const now = new Date();
    const from = new Date(now);
    from.setDate(from.getDate() - 6);
    expect(onChange).toHaveBeenCalledWith({ from: iso(from), to: iso(now) });
  });
});
