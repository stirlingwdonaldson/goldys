import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import KitchenPage from "./page";

vi.mock("@/lib/use-api-data", () => ({
  useApiData: (fetcher: (api: unknown) => Promise<unknown>) => {
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
        loading: false,
        error: null,
        reload: async () => {},
      };
    }
    if (src.includes("getInventoryLines")) {
      return {
        data: { unitCostByUom: [], cogsBySupplier: [] },
        loading: false,
        error: null,
        reload: async () => {},
      };
    }
    if (src.includes("getInventorySummary")) {
      return {
        data: { purchases: 0, wastage: 0, foodCostPercent: 0 },
        loading: false,
        error: null,
        reload: async () => {},
      };
    }
    return { data: null, loading: false, error: null, reload: async () => {} };
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
