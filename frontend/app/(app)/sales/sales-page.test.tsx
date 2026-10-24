import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import SalesPage from "./page";
import type { SalesTrendPoint } from "@/lib/api";

const navigation = vi.hoisted(() => ({ query: "" }));
beforeEach(() => { navigation.query = ""; });

// Two sources share a date; summing them would double-count to $20,000.
// The resolved trend must instead show the reconciled $12,000 (audit F01).
vi.mock("@tanstack/react-query", async (importOriginal) => ({
  ...await importOriginal<typeof import("@tanstack/react-query")>(),
  useQuery: ({ queryFn: fetcher }: { queryFn: (context: unknown) => Promise<unknown> }) => {
    const src = String(fetcher);
    if (src.includes("listDailySales")) {
      return {
        data: {
          items: [
            { date: "2026-10-05", source: "CTB", totalSales: 12000, gst: 1000, net: 11000 },
            { date: "2026-10-05", source: "Lightspeed", totalSales: 8000, gst: 700, net: 7300 },
          ],
          total: 2,
          page: 0,
          size: 10,
        },
        isPending: false,
        error: null,
        refetch: async () => {},
      };
    }
    if (src.includes("getSalesTrend")) {
      return { data: { points: [{ date: "2026-10-05", total: 12000 }], notices: ["Incomplete subtotal"] }, isPending: false, error: null, refetch: async () => {} };
    }
    return { data: null, isPending: false, error: null, refetch: async () => {} };
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

  it("matches a familiar source abbreviation as well as its display name", () => {
    navigation.query = "source=CTB";
    render(<SalesPage />);
    expect(screen.getByText("$12,000.00")).toBeInTheDocument();
    expect(screen.queryByText("$8,000.00")).not.toBeInTheDocument();
  });

  it("still renders the per-source breakdown table for comparison", () => {
    render(<SalesPage />);
    expect(screen.getAllByText(/5 Oct/)).toHaveLength(2);
    expect(screen.getByText("$12,000.00")).toBeInTheDocument();
    expect(screen.getByText("$8,000.00")).toBeInTheDocument();
  });
});

vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({}), useDemoMode: () => ({ demo: true }) }));

vi.mock("next/navigation", () => ({ usePathname: () => "/sales", useSearchParams: () => new URLSearchParams(navigation.query), useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }) }));
