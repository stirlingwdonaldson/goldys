import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { SalesTrend } from "./sales-trend";
import { TopSellers } from "./top-sellers";
import { BusinessKpiGrid } from "./business-kpi-grid";

vi.mock("@/components/widgets/widget-renderer", () => ({
  WidgetRenderer: ({ widget }: { widget: unknown }) => <pre data-testid="widget">{JSON.stringify(widget)}</pre>,
}));

describe("dashboard coverage", () => {
  it("labels net sales, keeps zero and null distinct, and qualifies gaps", () => {
    render(<SalesTrend points={[{ date: "2026-10-01", total: 0 }, { date: "2026-10-02", total: null }]}
      notices={["Incomplete subtotal: one date without a resolved record"]} />);
    const spec = JSON.parse(screen.getByTestId("widget").textContent!);
    expect(spec.title).toContain("Net sales");
    expect(spec.title).toContain("ex GST");
    expect(spec.description).not.toContain("awaiting a decision");
    expect(spec.series[0].points.map((p: { y: number | null }) => p.y)).toEqual([0, null]);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("qualifies empty rankings rather than implying no sales", () => {
    render(<TopSellers items={[]} notices={["Incomplete subtotal: no resolved records"]}
      coverage={{ from: "2026-10-01", to: "2026-10-02", observedDates: [], unresolvedDates: [], absentDates: ["2026-10-01", "2026-10-02"], expectedTradingPopulationKnown: false }} />);
    const spec = JSON.parse(screen.getByTestId("widget").textContent!);
    expect(spec.description).toContain("Ranked by known sales; full-period ranking unverified.");
    expect(spec.description).toContain("2026-10-01 to 2026-10-02");
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("does not call absent dates disputed or a full calendar complete", () => {
    const { rerender } = render(<BusinessKpiGrid seniority="OWNER" latestSales={{date: "2026-10-02", total: 20}}
      salesTrend={[{date: "2026-10-01", total: null}]} />);
    expect(screen.getByText("1 day unavailable")).toBeInTheDocument();
    expect(screen.queryByText(/disputed$/)).not.toBeInTheDocument();
    rerender(<BusinessKpiGrid seniority="OWNER" latestSales={{date: "2026-10-02", total: 20}}
      salesTrend={[{date: "2026-10-01", total: 0}]} />);
    expect(screen.getByText("Coverage unverified")).toBeInTheDocument();
  });
});
