import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import InvoicePage from "./page";

vi.mock("@/lib/demo-mode", () => ({
  useDemoMode: () => ({ demo: false, setDemo: () => {} }),
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ invoiceNumber: "INV%2F1042" }),
}));

const ok = (data: unknown) => ({ data, loading: false, error: null, reload: async () => {} });

vi.mock("@/lib/use-api-data", () => ({
  useApiData: (fetcher: (api: unknown) => Promise<unknown>) => {
    const src = String(fetcher);
    if (src.includes("listInvoiceFlags") || src.includes("page.items")) return ok(null);
    return ok({
      supplierName: "Oranges & Lemons",
      invoiceNumber: "INV/1042",
      invoiceDate: "2026-10-11",
      dueDate: "2026-10-18",
      totalAmount: 50,
      purchaseNumber: null,
      hasPdf: true,
      lines: [{ productNameKey: "basil x bunch", stockCode: "BASIL X BUNCH", quantity: 5, unitCost: 10, lineTotal: 50, uom: null }],
    });
  },
}));

describe("InvoicePage (full page)", () => {
  it("shows the lines and the PDF together, with a way back to the list", () => {
    render(<InvoicePage />);
    expect(screen.getByRole("heading", { name: "Invoice INV/1042" })).toBeInTheDocument();
    expect(screen.getByText("Oranges & Lemons")).toBeInTheDocument();
    expect(screen.getByText("Basil x bunch")).toBeInTheDocument();
    expect(screen.getByTestId("invoice-pdf-preview")).toHaveAttribute("src", "/api/invoices/INV%2F1042/pdf");
    expect(screen.getByRole("link", { name: "Invoices" })).toHaveAttribute("href", "/invoices");
  });
});
