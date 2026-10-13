import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import InvoicesPage from "./page";

const mocks = vi.hoisted(() => ({
  demo: true,
}));

vi.mock("@/lib/demo-mode", () => ({
  useDemoMode: () => ({ demo: mocks.demo, setDemo: () => {} }),
}));

vi.mock("@/lib/use-api-data", () => ({
  useApiData: (fetcher: (api: unknown) => Promise<unknown>) => {
    const src = String(fetcher);
    if (src.includes("listInvoices")) {
      return {
        data: {
          items: [
            {
              supplierName: "Paramount Liquor",
              invoiceNumber: "INV-1042",
              invoiceDate: "2026-09-28",
              dueDate: "2026-10-28",
              totalAmount: 1420.15,
              purchaseNumber: "PO-88",
              hasPdf: true,
            },
            {
              supplierName: "Paramount Liquor",
              invoiceNumber: "INV-1091",
              invoiceDate: "2026-09-21",
              dueDate: null,
              totalAmount: 980.4,
              purchaseNumber: "PO-91",
              hasPdf: false,
            },
          ],
          total: 2,
          page: 0,
          size: 50,
        },
        loading: false,
        error: null,
        reload: async () => {},
      };
    }
    if (src.includes("getInvoice")) {
      return {
        data: {
          supplierName: "Paramount Liquor",
          invoiceNumber: "INV-1042",
          invoiceDate: "2026-09-28",
          dueDate: "2026-10-28",
          totalAmount: 1420.15,
          purchaseNumber: "PO-88",
          hasPdf: true,
          lines: [
            {
              productNameKey: "chicken breast",
              stockCode: "CB-1",
              quantity: 4,
              unitCost: 12.5,
              lineTotal: 50.0,
              uom: "CTN",
            },
          ],
        },
        loading: false,
        error: null,
        reload: async () => {},
      };
    }
    return { data: null, loading: false, error: null, reload: async () => {} };
  },
}));

describe("InvoicesPage", () => {
  it("renders the invoice list", () => {
    render(<InvoicesPage />);
    expect(screen.getByText("INV-1042")).toBeInTheDocument();
    expect(screen.getByText("INV-1091")).toBeInTheDocument();
    expect(screen.getAllByText("Paramount Liquor")).toHaveLength(2);
  });

  it("selecting a row renders lines and the PDF preview/download controls", () => {
    mocks.demo = false;
    render(<InvoicesPage />);
    fireEvent.click(screen.getByText("INV-1042"));

    expect(screen.getByText("chicken breast")).toBeInTheDocument();
    expect(screen.getByTestId("invoice-pdf-preview")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Download" })).toBeInTheDocument();
  });

  it("shows no preview/download controls for a row without a PDF", () => {
    mocks.demo = false;
    render(<InvoicesPage />);
    fireEvent.click(screen.getByText("INV-1091"));

    expect(screen.queryByTestId("invoice-pdf-preview")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Download" })).not.toBeInTheDocument();
  });

  it("replaces the PDF affordance with a muted note in demo mode", () => {
    mocks.demo = true;
    render(<InvoicesPage />);
    fireEvent.click(screen.getByText("INV-1042"));

    expect(screen.getByText("PDF preview unavailable in demo mode")).toBeInTheDocument();
    expect(screen.queryByTestId("invoice-pdf-preview")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Download" })).not.toBeInTheDocument();
  });
});
