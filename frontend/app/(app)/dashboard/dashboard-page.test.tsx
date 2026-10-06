import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import DashboardPage from "./page";

const summary = {
  ingestionCompleteness: 92,
  openConflicts: 1,
  timeToDetectFailure: "42m avg",
  overrideUsage: { count: 3, period: "this week" },
};

vi.mock("@/lib/use-api-data", () => ({
  useApiData: (fetcher: (api: unknown) => Promise<unknown>) => {
    const src = String(fetcher);
    // Distinguish the fetchers by their source text so the dashboard gets a
    // resolved latest-sales value (not a per-source list) back from the mock.
    if (src.includes("getLatestSales")) {
      return {
        data: { date: "2026-10-05", total: 10865.72, authoritativeSource: "agreed" },
        loading: false,
        error: null,
        reload: async () => {},
      };
    }
    if (src.includes("getDashboardActivity")) {
      return { data: [], loading: false, error: null, reload: async () => {} };
    }
    if (src.includes("getTopSellers")) {
      return { data: [], loading: false, error: null, reload: async () => {} };
    }
    if (src.includes("getSalesTrend")) {
      return { data: [], loading: false, error: null, reload: async () => {} };
    }
    return { data: summary, loading: false, error: null, reload: async () => {} };
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
