import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DemoModeProvider } from "@/lib/demo-mode";
import { ApiError, type Api } from "@/lib/api";
import { SettlementFlowView } from "./settlement-flow-view";

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
    expect(await screen.findByText(/couldn.t load the settlement/i)).toBeInTheDocument();
  });

  it("shows an empty state when nothing ingests for the period", async () => {
    render(
      <DemoModeProvider>
        <SettlementFlowView from="2026-09-01" to="2026-09-30" apiOverride={emptyApi} />
      </DemoModeProvider>,
    );
    expect(await screen.findByText(/no settlement data/i)).toBeInTheDocument();
  });
});
