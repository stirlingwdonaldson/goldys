import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StaffPageView } from "./staff-page-view";

describe("StaffPageView", () => {
  it("renders roster for everyone and labor cost for an Owner", () => {
    render(<StaffPageView seniority="OWNER" />);
    expect(screen.getByText("Rostering & hours")).toBeInTheDocument();
    expect(screen.getByText("Labour cost as a % of sales.")).toBeInTheDocument();
    expect(screen.queryByText(/owner only/i)).not.toBeInTheDocument();
  });

  it("locks the labor-cost surface for a non-Owner while roster stays visible", () => {
    render(<StaffPageView seniority="MANAGER" />);
    expect(screen.getByText("Rostering & hours")).toBeInTheDocument();
    expect(screen.getByText(/owner only/i)).toBeInTheDocument();
    expect(screen.queryByText("Labour cost as a % of sales.")).not.toBeInTheDocument();
  });

  it("fails closed (locks labor cost) when seniority is absent", () => {
    render(<StaffPageView />);
    expect(screen.getByText("Rostering & hours")).toBeInTheDocument();
    expect(screen.getByText(/owner only/i)).toBeInTheDocument();
  });
});
