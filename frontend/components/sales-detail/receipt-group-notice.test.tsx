import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DemoModeProvider } from "@/lib/demo-mode";
import { demoApi } from "@/lib/api/demo";
import { ReceiptGroupNotice } from "./receipt-group-notice";

describe("ReceiptGroupNotice", () => {
  it("identifies a shared sale/refund number without assigning its items", async () => {
    render(<DemoModeProvider><ReceiptGroupNotice api={demoApi} saleNumber="DEMO-AMBIGUOUS" /></DemoModeProvider>);
    expect(await screen.findByText(/Several source transactions share/)).toBeInTheDocument();
    expect(screen.getByText(/transaction demo-sale/)).toBeInTheDocument();
    expect(screen.getByText(/Source coverage is unverified/)).toBeInTheDocument();
  });
  it("does not imply that a single or absent transaction establishes identity", async () => {
    render(<DemoModeProvider><ReceiptGroupNotice api={demoApi} saleNumber="SALE-1002" /></DemoModeProvider>);
    expect(await screen.findByText(/relationship to individual transactions remains unverified/)).toBeInTheDocument();
  });
});
