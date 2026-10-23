import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import KitchenPage from "./page";

vi.mock("@tanstack/react-query", async (importOriginal) => ({
  ...await importOriginal<typeof import("@tanstack/react-query")>(),
  useQuery: ({ queryFn: fetcher }: { queryFn: (context: unknown) => Promise<unknown> }) => {
    const src = String(fetcher);
    if (src.includes("listInvoiceFlags")) {
      return {
        data: [
          {
            flagType: "MISSING_PDF",
            invoiceNumber: "INV-1042",
            pdfFilename: null,
            stockCode: null,
            detail: "no PDF filename in CSV",
            occurredAt: "2026-10-09T00:24:00Z",
          },
        ],
        isPending: false,
        error: null,
        refetch: async () => {},
      };
    }
    if (src.includes("getInventoryLines")) {
      return {
        data: { unitCostByUom: [], cogsBySupplier: [] },
        isPending: false,
        error: null,
        refetch: async () => {},
      };
    }
    if (src.includes("getInventorySummary")) {
      return {
        data: { purchases: 0, wastage: 0, foodCostPercent: 0 },
        isPending: false,
        error: null,
        refetch: async () => {},
      };
    }
    return { data: null, isPending: false, error: null, refetch: async () => {} };
  },
}));

vi.mock("@/components/inventory/invoice-graph-view", () => ({
  InvoiceGraphView: () => <div data-testid="invoice-graph" />,
}));

describe("KitchenPage", () => {
  it("renders recorded invoice ingestion flags", () => {
    render(<KitchenPage />);
    expect(screen.getByText("Invoice ingestion flags")).toBeInTheDocument();
    expect(screen.getByText("MISSING_PDF")).toBeInTheDocument();
    expect(screen.getByText("no PDF filename in CSV")).toBeInTheDocument();
  });
});

vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({}), useDemoMode: () => ({ demo: true }) }));
