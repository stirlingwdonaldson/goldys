import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import SalesPage from "./page";
import type { DailySales } from "@/lib/api";

const sales: DailySales[] = [
  { date: "2026-10-05", source: "CTB", totalSales: 10865.72, gst: 985.44, net: 9880.28 },
  { date: "2026-10-04", source: "CTB", totalSales: 29605.13, gst: 2689.54, net: 26915.6 },
];

vi.mock("@/lib/use-api-data", () => ({
  useApiData: () => ({ data: sales, loading: false, error: null, reload: async () => {} }),
}));

describe("SalesPage", () => {
  it("renders the daily sales table", () => {
    render(<SalesPage />);
    expect(screen.getByText(/5 Oct/)).toBeInTheDocument();
    expect(screen.getByText("$10,865.72")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: /daily sales trend/i })).toBeInTheDocument();
  });
});
