import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import DashboardPage from "./page";

vi.mock("@tanstack/react-query", async (importOriginal) => ({
  ...await importOriginal<typeof import("@tanstack/react-query")>(),
  useQuery: ({ queryFn: fetcher }: { queryFn: (context: unknown) => Promise<unknown> }) => {
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
          salesTrend: { points: [], notices: ["Incomplete sales subtotal"] },
          activity: [],
          topSellers: { items: [], notices: ["Incomplete product subtotal"] },
          coversToday: { covers: 132, bookings: 98, noShows: 4 },
          foodCost: { foodCostPercent: 0.3125, purchases: 15842.6, wastage: 214.5 },
        },
        isPending: false,
        error: null,
        refetch: async () => {},
      };
    }
    return { data: null, isPending: false, error: null, refetch: async () => {} };
  },
}));

vi.mock("@/components/app-shell/current-user-provider", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/components/app-shell/current-user-provider")>(),
  useCurrentUser: () => ({ user: { seniority: "OWNER" } }),
}));

describe("DashboardPage", () => {
  it("renders the resolved latest-sales total rather than summing per-source rows", () => {
    render(<DashboardPage />);
    // The KPI grid renders the single resolved value; if the page reverted to
    // summing `listDailySales` rows, this resolved figure would not appear.
    expect(screen.getByText("$10,865.72")).toBeInTheDocument();
    expect(screen.getByText("Incomplete sales subtotal")).toBeInTheDocument();
    expect(screen.getByText("Incomplete product subtotal")).toBeInTheDocument();
  });
});

vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({}), useDemoMode: () => ({ demo: true }) }));
