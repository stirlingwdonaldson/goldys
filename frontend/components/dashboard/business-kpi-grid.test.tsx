import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { BusinessKpiGrid } from "./business-kpi-grid";

describe("BusinessKpiGrid", () => {
  it("renders all five business tiles for an Owner, including labor", () => {
    render(<BusinessKpiGrid seniority="Owner" />);
    expect(screen.getByText("Sales")).toBeInTheDocument();
    expect(screen.getByText("Labor cost %")).toBeInTheDocument();
    expect(screen.getByText("Covers today")).toBeInTheDocument();
    expect(screen.getByText("Top sellers")).toBeInTheDocument();
    expect(screen.getByText("Food cost")).toBeInTheDocument();
    expect(screen.queryByText(/owner only/i)).not.toBeInTheDocument();
  });

  it("locks the labor tile for a non-Owner seniority", () => {
    render(<BusinessKpiGrid seniority="Manager" />);
    expect(screen.queryByText(/awaiting deputy reporting/i)).not.toBeInTheDocument();
    expect(screen.getByText(/owner only/i)).toBeInTheDocument();
    // The other four tiles still render.
    expect(screen.getByText("Sales")).toBeInTheDocument();
    expect(screen.getByText("Covers today")).toBeInTheDocument();
  });

  it("locks the labor tile for any value other than Owner", () => {
    render(<BusinessKpiGrid seniority="Staff" />);
    expect(screen.getByText(/owner only/i)).toBeInTheDocument();
  });
});
