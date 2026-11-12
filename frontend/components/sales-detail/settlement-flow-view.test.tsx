import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { DemoModeProvider } from "@/lib/demo-mode";
import { ApiError, type Api } from "@/lib/api";
import { SettlementFlowView } from "./settlement-flow-view";

vi.mock("@/components/flow/flow-canvas", () => ({
  FlowCanvas: ({ ariaLabel }: { ariaLabel: string }) => <div aria-label={ariaLabel} />,
}));

const failingApi = (code: string, message: string): Api =>
  ({
    getSaleItemMix: async () => {
      throw new ApiError(code, message);
    },
    getPaymentMix: async () => {
      throw new ApiError(code, message);
    },
    getDeletedSaleTotals: async () => {
      throw new ApiError(code, message);
    },
  }) as unknown as Api;

const emptyApi = {
  getSaleItemMix: async () => [],
  getPaymentMix: async () => [],
  getDeletedSaleTotals: async () => [],
} as unknown as Api;

describe("SettlementFlowView", () => {
  it("shows a could-not-load message when the fetch fails", async () => {
    render(
      <DemoModeProvider>
        <SettlementFlowView
          from="2026-09-01"
          to="2026-09-30"
          apiOverride={failingApi("NETWORK_ERROR", "Could not reach the server.")}
        />
      </DemoModeProvider>,
    );
    expect(await screen.findByText(/couldn.t load sales and payments/i)).toBeInTheDocument();
  });

  it("shows an empty state when nothing ingests for the period", async () => {
    render(
      <DemoModeProvider>
        <SettlementFlowView from="2026-09-01" to="2026-09-30" apiOverride={emptyApi} />
      </DemoModeProvider>,
    );
    expect(await screen.findByText(/no sales or payment data/i)).toBeInTheDocument();
  });
});


it("shows date-basis and coverage qualifications alongside the comparison", async () => {
  const api = {
    ...emptyApi,
    getSaleItemMix: async () => [{ tradingDate: "2026-11-01", categoryName: "Food", amount: 59, quantity: 1, hasConflict: true }],
    getPaymentMix: async () => [{ tradingDate: "2026-11-02", paymentTypeName: "Cash", amount: 59, tip: 0, count: 1, hasConflict: false }],
  } as unknown as Api;
  render(<DemoModeProvider><SettlementFlowView from="2026-11-01" to="2026-11-02" apiOverride={api} /></DemoModeProvider>);
  expect(await screen.findByLabelText("Merchandise sales compared with POS payments")).toBeInTheDocument();
  expect(screen.getByLabelText("Sales and payment totals")).toHaveTextContent("$59.00");
  expect(screen.getByText(/Sales use the date the sale closed/)).toBeInTheDocument();
  expect(screen.getByText(/Sales and payments cover different dates/)).toBeInTheDocument();
  expect(screen.getByText(/Source conflicts remain/)).toBeInTheDocument();
});

it("preserves the permission-denied state", async () => {
  render(<DemoModeProvider><SettlementFlowView from="2026-11-01" to="2026-11-02" apiOverride={failingApi("NOT_PERMITTED", "Not permitted")} /></DemoModeProvider>);
  expect(await screen.findByText(/have access to sales and payments/i)).toBeInTheDocument();
});
