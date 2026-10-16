import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { BusinessKpiGrid } from "./business-kpi-grid";

describe("BusinessKpiGrid", () => {
  it("renders all five business tiles for an Owner, including labor", () => {
    render(<BusinessKpiGrid seniority="OWNER" />);
    expect(screen.getByText("Sales")).toBeInTheDocument();
    expect(screen.getByText("Labour cost %")).toBeInTheDocument();
    expect(screen.getByText("Attendance proxy today")).toBeInTheDocument();
    expect(screen.getByText("Top sellers")).toBeInTheDocument();
    expect(screen.getByText("Invoice purchases")).toBeInTheDocument();
    expect(screen.queryByText(/owner only/i)).not.toBeInTheDocument();
  });

  it("shows aggregate labour to managers", () => {
    render(<BusinessKpiGrid seniority="MANAGER" />);
    expect(screen.getByText(/awaiting deputy reporting/i)).toBeInTheDocument();
    expect(screen.queryByText(/owner only/i)).not.toBeInTheDocument();
    expect(screen.getByText("Sales")).toBeInTheDocument();
    expect(screen.getByText("Attendance proxy today")).toBeInTheDocument();
  });

  it("locks the labor tile for any value other than Owner", () => {
    render(<BusinessKpiGrid seniority="STAFF" />);
    expect(screen.getByText(/owner only/i)).toBeInTheDocument();
  });

  it("fails closed (locks the labor tile) when seniority is absent", () => {
    render(<BusinessKpiGrid />);
    expect(screen.getByText(/owner only/i)).toBeInTheDocument();
  });

  it("shows the latest sales total when data is present", () => {
    render(
      <BusinessKpiGrid seniority="OWNER" latestSales={{ date: "2026-10-05", total: 10865.72 }} />,
    );
    expect(screen.getByText("$10,865.72")).toBeInTheDocument();
    expect(screen.queryByText(/awaiting sales-reporting/i)).not.toBeInTheDocument();
  });

  it("shows a needs-decision placeholder when the latest date is unresolved", () => {
    render(<BusinessKpiGrid seniority="OWNER" latestSales={{ date: "2026-10-05", total: null }} />);
    expect(screen.getByText(/needs review/i)).toBeInTheDocument();
    expect(screen.queryByText(/\$10,?865/)).not.toBeInTheDocument();
  });
});
