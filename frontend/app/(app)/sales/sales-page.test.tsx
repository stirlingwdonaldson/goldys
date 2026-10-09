import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import SalesPage from "./page";
import type { SalesTrendPoint } from "@/lib/api";

// Two sources share a date; summing them would double-count to $20,000.
// The resolved trend must instead show the reconciled $12,000 (audit F01).
vi.mock("@/lib/use-api-data", () => ({
  useApiData: (fetcher: (api: unknown) => Promise<unknown>) => {
    const src = String(fetcher);
    if (src.includes("listDailySales")) {
      return {
        data: [
          { date: "2026-10-05", source: "CTB", totalSales: 12000, gst: 1000, net: 11000 },
          { date: "2026-10-05", source: "Lightspeed", totalSales: 8000, gst: 700, net: 7300 },
        ],
        loading: false,
        error: null,
        reload: async () => {},
      };
    }
    if (src.includes("getSalesTrend")) {
      return { data: [{ date: "2026-10-05", total: 12000 }], loading: false, error: null, reload: async () => {} };
    }
    return { data: null, loading: false, error: null, reload: async () => {} };
  },
}));

// Surface the resolved points as text so the test can assert the reconciled value
// flows through and the per-source sum does not.
vi.mock("@/components/dashboard/sales-trend", () => ({
  SalesTrend: ({ points }: { points: SalesTrendPoint[] }) => (
    <div data-testid="sales-trend">{points.map((p) => `resolved:${p.total}`).join(" ")}</div>
  ),
}));

describe("SalesPage", () => {
  it("renders the resolved daily-sales trend rather than summing per-source rows", () => {
    render(<SalesPage />);
    const trendEl = screen.getByTestId("sales-trend");
    expect(trendEl.textContent).toContain("resolved:12000");
    expect(trendEl.textContent).not.toContain("20000");
  });

  it("still renders the per-source breakdown table for comparison", () => {
    render(<SalesPage />);
    expect(screen.getAllByText(/5 Oct/)).toHaveLength(2);
    expect(screen.getByText("$12,000.00")).toBeInTheDocument();
    expect(screen.getByText("$8,000.00")).toBeInTheDocument();
  });
});
