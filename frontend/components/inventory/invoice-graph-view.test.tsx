import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DemoModeProvider } from "@/lib/demo-mode";
import { ApiError, type Api } from "@/lib/api";
import { InvoiceGraphView } from "./invoice-graph-view";

const failingApi = (code: string, message: string): Api =>
  ({
    getInvoiceGraphSuppliers: async () => {
      throw new ApiError(code, message);
    },
  }) as unknown as Api;

describe("InvoiceGraphView", () => {
  it("shows a could-not-load message when the supplier fetch fails", async () => {
    render(
      <DemoModeProvider>
        <InvoiceGraphView
          from="2026-09-01"
          to="2026-09-30"
          apiOverride={failingApi("NETWORK_ERROR", "Could not reach the server.")}
        />
      </DemoModeProvider>,
    );
    expect(await screen.findByText(/couldn.t load the invoice graph/i)).toBeInTheDocument();
    expect(screen.queryByText("No invoices in range")).not.toBeInTheDocument();
  });

  it("shows the permission-denied state for a NOT_PERMITTED failure", async () => {
    render(
      <DemoModeProvider>
        <InvoiceGraphView
          from="2026-09-01"
          to="2026-09-30"
          apiOverride={failingApi("NOT_PERMITTED", "Not permitted")}
        />
      </DemoModeProvider>,
    );
    expect(await screen.findByText(/don.t have access to purchase lineage/i)).toBeInTheDocument();
  });
});
