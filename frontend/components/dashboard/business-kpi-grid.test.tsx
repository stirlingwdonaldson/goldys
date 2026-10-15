import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { BusinessKpiGrid } from "./business-kpi-grid";

describe("BusinessKpiGrid", () => {
  it("renders all five business tiles for an Owner, including labor", () => {
    render(<BusinessKpiGrid seniority="OWNER" />);
    expect(screen.getByText("Sales")).toBeInTheDocument();
    expect(screen.getByText("Labour cost %")).toBeInTheDocument();
    expect(screen.getByText("Covers today")).toBeInTheDocument();
    expect(screen.getByText("Top sellers")).toBeInTheDocument();
    expect(screen.getByText("Food cost")).toBeInTheDocument();
    expect(screen.queryByText(/owner only/i)).not.toBeInTheDocument();
  });

  it("locks the labor tile for a non-Owner seniority", () => {
    render(<BusinessKpiGrid seniority="MANAGER" />);
    expect(screen.queryByText(/awaiting deputy reporting/i)).not.toBeInTheDocument();
    expect(screen.getByText(/owner only/i)).toBeInTheDocument();
    // The other four tiles still render.
    expect(screen.getByText("Sales")).toBeInTheDocument();
    expect(screen.getByText("Covers today")).toBeInTheDocument();
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
    expect(screen.getByText(/needs decision/i)).toBeInTheDocument();
    expect(screen.queryByText(/\$10,?865/)).not.toBeInTheDocument();
  });

  it("shows resolved covers, top seller, and food cost once data lands", () => {
    render(
      <BusinessKpiGrid
        seniority="OWNER"
        coversToday={{ covers: 132, bookings: 98, noShows: 4 }}
        foodCost={{ foodCostPercent: 0.3125, purchases: 15842.6, wastage: 214.5 }}
        topSellers={[{ name: "chicken schnitzel", quantitySold: 211, amount: 5591.5, hasConflict: false }]}
      />,
    );
    expect(screen.getByText("132")).toBeInTheDocument();
    expect(screen.getByText(/98 bookings/)).toBeInTheDocument();
    expect(screen.getByText("31.3%")).toBeInTheDocument();
    expect(screen.getByText("$5,591.50")).toBeInTheDocument();
    expect(screen.getByText("chicken schnitzel")).toBeInTheDocument();
  });
});
