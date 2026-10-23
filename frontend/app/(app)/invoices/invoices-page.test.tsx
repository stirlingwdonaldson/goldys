import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, within } from "@testing-library/react";
import InvoicesPage from "./page";

const mocks = vi.hoisted(() => ({
  demo: true,
  params: new URLSearchParams(),
  push: vi.fn(),
  replace: vi.fn(),
  back: vi.fn(),
}));

vi.mock("@/lib/demo-mode", () => ({
  useDemoMode: () => ({ demo: mocks.demo, setDemo: () => {} }),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/invoices",
  useSearchParams: () => mocks.params,
  useRouter: () => ({ push: mocks.push, replace: mocks.replace, back: mocks.back }),
}));

const headers: import("@/lib/api/types").InvoiceSummary[] = [
  {
    supplierName: "Paramount Liquor",
    invoiceNumber: "INV-1042",
    invoiceDate: "2026-09-28",
    dueDate: "2026-10-28",
    totalAmount: 140.0,
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
];

const ok = (data: unknown) => ({ data, loading: false, error: null, reload: async () => {} });

vi.mock("@/lib/use-api-data", () => ({
  useApiData: (fetcher: (api: unknown) => Promise<unknown>) => {
    const src = String(fetcher);
    if (src.includes("listInvoiceFlags")) {
      return ok([
        {
          flagType: "PDF_ONLY_LINE",
          invoiceNumber: "INV-1042",
          pdfFilename: "inv-1042.pdf",
          stockCode: "CB-1",
          detail: "PDF line has no matching CSV line",
          occurredAt: "2026-10-08T18:30:00Z",
        },
      ]);
    }
    // The previous-invoice lookup (supplier's newest invoice before this one).
    if (src.includes("page.items")) {
      return ok({
        ...headers[1],
        lines: [
          { productNameKey: "chicken breast", stockCode: "CB-1", quantity: 4, unitCost: 10, lineTotal: 40, uom: "CTN" },
        ],
      });
    }
    if (src.includes("listInvoices")) {
      return ok({ items: headers, total: 2, page: 0, size: 25 });
    }
    if (src.includes("getInvoice")) {
      const open = mocks.params.get("invoice") ?? "INV-1042";
      const header = headers.find((h) => h.invoiceNumber === open) ?? headers[0];
      return ok({
        ...header,
        lines:
          header.invoiceNumber === "INV-1042"
            ? [
                { productNameKey: "chicken breast", stockCode: "CB-1", quantity: 4, unitCost: 12.5, lineTotal: 50.0, uom: "CTN" },
                { productNameKey: "beef mince", stockCode: "BEEF MINCE", quantity: 10, unitCost: 9.0, lineTotal: 90.0, uom: "KG" },
              ]
            : [],
      });
    }
    return ok(null);
  },
}));

beforeEach(() => {
  mocks.demo = false;
  mocks.params = new URLSearchParams();
  mocks.push.mockReset();
  mocks.replace.mockReset();
  mocks.back.mockReset();
});

describe("InvoicesPage", () => {
  it("renders the invoice list with no detail open", () => {
    render(<InvoicesPage />);
    expect(screen.getByText("INV-1042")).toBeInTheDocument();
    expect(screen.getByText("INV-1091")).toBeInTheDocument();
    expect(screen.getAllByText("Paramount Liquor")).toHaveLength(2);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("clicking a row puts the invoice in the URL", () => {
    render(<InvoicesPage />);
    fireEvent.click(screen.getByText("INV-1042"));
    expect(mocks.push).toHaveBeenCalledWith("/invoices?invoice=INV-1042", { scroll: false });
  });

  it("opens the slide-over for the invoice in the URL, with close and expand controls", () => {
    mocks.params = new URLSearchParams("invoice=INV-1042");
    render(<InvoicesPage />);
    const sheet = screen.getByRole("dialog");

    expect(within(sheet).getByText("Invoice INV-1042")).toBeInTheDocument();
    expect(within(sheet).getByText("Chicken breast")).toBeInTheDocument();
    expect(within(sheet).getByRole("link", { name: "Expand to full page" })).toHaveAttribute(
      "href",
      "/invoices/INV-1042",
    );
    expect(within(sheet).getByRole("link", { name: "Download" })).toBeInTheDocument();

    fireEvent.click(within(sheet).getByRole("button", { name: "Close" }));
    expect(mocks.replace).toHaveBeenCalledWith("/invoices", { scroll: false });
  });

  it("annotates lines with flags, price movement and a hidden duplicate stock code", () => {
    mocks.params = new URLSearchParams("invoice=INV-1042");
    render(<InvoicesPage />);
    const sheet = screen.getByRole("dialog");

    expect(within(sheet).getByText("Invoice line only on the PDF")).toBeInTheDocument();
    expect(within(sheet).getByLabelText("Flagged line")).toBeInTheDocument();
    // 10.00 → 12.50 against the previous invoice.
    expect(within(sheet).getByText(/25%/)).toBeInTheDocument();
    // "BEEF MINCE" repeats the product name, so only CB-1 is shown as a code.
    expect(within(sheet).getByText("CB-1")).toBeInTheDocument();
    expect(within(sheet).queryByText("BEEF MINCE")).not.toBeInTheDocument();
  });

  it("steps to the next invoice with the arrow key", () => {
    mocks.params = new URLSearchParams("invoice=INV-1042");
    render(<InvoicesPage />);
    fireEvent.keyDown(screen.getByRole("button", { name: "Next invoice" }), { key: "ArrowDown" });
    expect(mocks.replace).toHaveBeenCalledWith("/invoices?invoice=INV-1091", { scroll: false });
  });

  it("shows no PDF actions for an invoice without a PDF", () => {
    mocks.params = new URLSearchParams("invoice=INV-1091");
    render(<InvoicesPage />);
    const sheet = screen.getByRole("dialog");
    expect(within(sheet).queryByRole("link", { name: "Download" })).not.toBeInTheDocument();
    expect(within(sheet).getByRole("button", { name: "Previous invoice" })).toBeEnabled();
    expect(within(sheet).getByRole("button", { name: "Next invoice" })).toBeDisabled();
  });

  it("hides PDF actions in demo mode", () => {
    mocks.demo = true;
    mocks.params = new URLSearchParams("invoice=INV-1042");
    render(<InvoicesPage />);
    const sheet = screen.getByRole("dialog");
    expect(within(sheet).queryByRole("link", { name: "Download" })).not.toBeInTheDocument();
  });
});

it("selects duplicate invoice numbers by invoice identity", () => {
  const previous = headers.map(h => ({...h}));
  try {
    headers[0] = {...headers[0],invoiceNumber:"SHARED",invoiceId:"10000000-0000-0000-0000-000000000001"};
    headers[1] = {...headers[1],invoiceNumber:"SHARED",invoiceId:"10000000-0000-0000-0000-000000000002"};
    render(<InvoicesPage />);
    fireEvent.click(screen.getAllByText("SHARED")[1]);
    expect(mocks.push).toHaveBeenCalledWith("/invoices?invoiceId=10000000-0000-0000-0000-000000000002",{scroll:false});
  } finally { headers.splice(0,headers.length,...previous); }
});
