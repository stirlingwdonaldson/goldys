import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StaffPageView } from "./staff-page-view";
import { ApiError, type LabourSummary } from "@/lib/api";

const summary: LabourSummary = {
  from: "2026-09-10",
  to: "2026-10-09",
  scheduledHours: 1184,
  actualHours: 1231.5,
  labourCost: 41872.4,
  variance: 47.5,
  hoursPerCover: 0.29,
  labourCostPerCover: 9.86,
  fohLabourCostPercent: 0.1412,
  bohLabourCostPercent: 0.1637,
};

const loaded = (data: LabourSummary | null, error: ApiError | null = null) => ({
  data,
  loading: false,
  error,
  reload: () => {},
});

describe("StaffPageView", () => {
  it("renders roster for everyone and labor cost for an Owner", () => {
    render(<StaffPageView seniority="OWNER" />);
    expect(screen.getByText("Rostering & hours")).toBeInTheDocument();
    expect(screen.getByText("Labor cost as a % of sales.")).toBeInTheDocument();
    expect(screen.queryByText(/owner only/i)).not.toBeInTheDocument();
  });

  it("locks the labor-cost surface for a non-Owner while roster stays visible", () => {
    render(<StaffPageView seniority="MANAGER" />);
    expect(screen.getByText("Rostering & hours")).toBeInTheDocument();
    expect(screen.getByText(/owner only/i)).toBeInTheDocument();
    expect(screen.queryByText("Labor cost as a % of sales.")).not.toBeInTheDocument();
  });

  it("fails closed (locks labor cost) when seniority is absent", () => {
    render(<StaffPageView />);
    expect(screen.getByText("Rostering & hours")).toBeInTheDocument();
    expect(screen.getByText(/owner only/i)).toBeInTheDocument();
  });

  it("shows resolved hours and, for an Owner, labour cost", () => {
    render(<StaffPageView seniority="OWNER" labour={loaded(summary)} />);
    expect(screen.getByText("1,231.5 h")).toBeInTheDocument();
    expect(screen.getByText("14.1%")).toBeInTheDocument();
    expect(screen.queryByText("Rostering & hours")).not.toBeInTheDocument();
  });

  it("never shows cost figures to a non-Owner even if the backend returned them", () => {
    render(<StaffPageView seniority="MANAGER" labour={loaded(summary)} />);
    expect(screen.getByText("1,231.5 h")).toBeInTheDocument();
    expect(screen.queryByText("14.1%")).not.toBeInTheDocument();
    expect(screen.getByText(/owner only/i)).toBeInTheDocument();
  });

  it("treats an all-null summary as awaiting data, not zero", () => {
    const empty = Object.fromEntries(
      Object.entries(summary).map(([k, v]) => [k, k === "from" || k === "to" ? v : null]),
    ) as unknown as LabourSummary;
    render(<StaffPageView seniority="OWNER" labour={loaded(empty)} />);
    expect(screen.getByText("Rostering & hours")).toBeInTheDocument();
  });

  it("shows an explicit permission-denied state when the role can't read labour", () => {
    render(
      <StaffPageView
        seniority="STAFF"
        labour={loaded(null, new ApiError("NOT_PERMITTED", "Not permitted"))}
      />,
    );
    expect(screen.getByText(/don.t have access to labour figures/i)).toBeInTheDocument();
  });
});
