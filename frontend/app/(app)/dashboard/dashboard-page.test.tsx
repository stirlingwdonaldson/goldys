import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import DashboardPage from "./page";

vi.mock("@/lib/use-api-data", () => ({
  useApiData: (fetcher: (api: unknown) => Promise<unknown>) => {
    const src = String(fetcher);
    if (src.includes("getDashboardBootstrap")) {
      return {
        data: {
          summary: {
            ingestionCompleteness: 92,
            openConflicts: 1,
            timeToDetectFailure: "42m avg",
            overrideUsage: { count: 3, period: "this week" },
          },
          latestSales: { date: "2026-10-05", total: 10865.72, authoritativeSource: "agreed" },
          salesTrend: [],
          activity: [],
          topSellers: [],
        },
        loading: false,
        error: null,
        reload: async () => {},
      };
    }
    return { data: null, loading: false, error: null, reload: async () => {} };
  },
}));

vi.mock("@/components/app-shell/current-user-provider", () => ({
  useCurrentUser: () => ({ user: { seniority: "OWNER" } }),
}));

describe("DashboardPage", () => {
  it("renders the resolved latest-sales total rather than summing per-source rows", () => {
    render(<DashboardPage />);
    // The KPI grid renders the single resolved value; if the page reverted to
    // summing `listDailySales` rows, this resolved figure would not appear.
    expect(screen.getByText("$10865.72")).toBeInTheDocument();
  });
});
