import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { InvoicePdf } from "./invoice-detail";

const state = vi.hoisted(() => ({ documents: [
  { documentId: "doc-a", filename: "a.pdf", linkedAt: "2026-10-01T00:00:00Z" },
  { documentId: "doc-b", filename: "b.pdf", linkedAt: "2026-10-02T00:00:00Z" },
] }));
vi.mock("@/lib/demo-mode", () => ({ useDemoMode: () => ({ demo: false }) }));
vi.mock("@/lib/use-api-data", () => ({ useApiData: () => ({ data: state.documents, loading: false, error: null }) }));
const invoice = { invoiceNumber: "SHARED", invoiceId: "invoice-a", hasPdf: true, canReadDocuments: true };

describe("qualified invoice documents", () => {
  beforeEach(() => { state.documents = [
    { documentId: "doc-a", filename: "a.pdf", linkedAt: "2026-10-01T00:00:00Z" },
    { documentId: "doc-b", filename: "b.pdf", linkedAt: "2026-10-02T00:00:00Z" },
  ]; });
  it("requires a choice instead of treating the newest receipt as authoritative", () => {
    render(<InvoicePdf invoice={invoice} />);
    expect(screen.queryByTitle("Invoice PDF preview")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Choose source document"), {target:{value:"doc-b"}});
    const path = "/api/invoices/by-id/invoice-a/documents/doc-b/pdf";
    expect(screen.getByTitle("Invoice PDF preview")).toHaveAttribute("src", path);
    expect(screen.getByRole("link",{name:"Download"})).toHaveAttribute("href",path+"?disposition=attachment");
  });
  it("shows source permission denial independently of invoice details", () => {
    render(<InvoicePdf invoice={{...invoice,canReadDocuments:false}} />);
    expect(screen.getByText("Not permitted to view original source documents.")).toBeInTheDocument();
    expect(screen.queryByTitle("Invoice PDF preview")).not.toBeInTheDocument();
  });
  it("does not describe unresolved evidence as no source document received", () => {
    state.documents = [];
    render(<InvoicePdf invoice={invoice} />);
    expect(screen.getByText(/Source documents may still be unresolved/)).toBeInTheDocument();
  });
});
