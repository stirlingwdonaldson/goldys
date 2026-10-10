import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DemoModeProvider } from "@/lib/demo-mode";
import { ApiError, type Api } from "@/lib/api";
import { PaymentsTab } from "./payments-tab";

const ROWS = [
  {
    tradingDate: "2026-10-04",
    saleNumber: "SALE-1001",
    paymentTypeName: "Tyro",
    paymentTypeCode: "4",
    paymentSourceType: "EFTPOS",
    lspayPaymentMode: "Tyro",
    clearingAccount: "Westpac 123",
    amount: 45.5,
    tip: 2,
    tendered: 47.5,
    surcharge: 0.35,
    paymentCount: 1,
    tipCount: 1,
    reconciled: "Yes",
    registerCode: "REG-1",
    registerName: "Main Bar",
    staffName: "Stirling Donaldson",
    staffCode: "S-1",
    siteId: "SITE-1",
    customerName: null,
  },
  {
    tradingDate: "2026-10-05",
    saleNumber: "SALE-1002",
    paymentTypeName: "Visa",
    paymentTypeCode: "VISA",
    paymentSourceType: "EFTPOS",
    lspayPaymentMode: null,
    clearingAccount: "Westpac 123",
    amount: 30,
    tip: 0,
    tendered: 30,
    surcharge: 0.45,
    paymentCount: 2,
    tipCount: 0,
    reconciled: "Yes",
    registerCode: null,
    registerName: null,
    staffName: null,
    staffCode: null,
    siteId: "SITE-1",
    customerName: null,
  },
];

const MIX = [
  { tradingDate: "2026-10-04", paymentTypeName: "Tyro", amount: 45.5, tip: 2, count: 1, hasConflict: false },
  { tradingDate: "2026-10-05", paymentTypeName: "Visa", amount: 30, tip: 0, count: 1, hasConflict: false },
];

function fakeApi(): Api {
  return {
    getPaymentMix: async () => MIX,
    listPayments: async (filter: { paymentType?: string }) => {
      const items = ROWS.filter(
        (r) => !filter.paymentType || r.paymentTypeName === filter.paymentType,
      );
      return { items, total: items.length, page: 0, size: 50 };
    },
  } as unknown as Api;
}

function deniedApi(): Api {
  const fail = async () => {
    throw new ApiError("NOT_PERMITTED", "Not permitted");
  };
  return { getPaymentMix: fail, listPayments: fail } as unknown as Api;
}

describe("PaymentsTab", () => {
  it("renders the resolved mix chart and the payment rows with formatted currency", async () => {
    render(
      <DemoModeProvider>
        <PaymentsTab from="2026-10-01" to="2026-10-05" apiOverride={fakeApi()} />
      </DemoModeProvider>,
    );

    expect(await screen.findByText("SALE-1001")).toBeInTheDocument();
    expect(screen.getByText("Payment mix")).toBeInTheDocument();
    expect(screen.getByText("SALE-1002")).toBeInTheDocument();
    expect(screen.getByText("Main Bar")).toBeInTheDocument();
    expect(screen.getByText("Stirling Donaldson")).toBeInTheDocument();

    // Currency columns are formatted (amount, tip, surcharge, tendered).
    expect(screen.getByText("$45.50")).toBeInTheDocument();
    expect(screen.getByText("$2.00")).toBeInTheDocument();
    expect(screen.getByText("$0.35")).toBeInTheDocument();
    expect(screen.getByText("$47.50")).toBeInTheDocument();

    // Nullable text falls back to an em dash (SALE-1002 has no register or staff).
    expect(screen.getAllByText("—").length).toBeGreaterThan(0);
  });

  it("filters the table by payment type from the mix", async () => {
    render(
      <DemoModeProvider>
        <PaymentsTab from="2026-10-01" to="2026-10-05" apiOverride={fakeApi()} />
      </DemoModeProvider>,
    );

    await screen.findByText("SALE-1001");
    fireEvent.change(screen.getByLabelText("Payment type"), { target: { value: "Visa" } });

    await waitFor(() => expect(screen.queryByText("SALE-1001")).not.toBeInTheDocument());
    expect(screen.getByText("SALE-1002")).toBeInTheDocument();
  });

  it("calls onViewSale with the row's sale number", async () => {
    const onViewSale = vi.fn();
    render(
      <DemoModeProvider>
        <PaymentsTab
          from="2026-10-01"
          to="2026-10-05"
          apiOverride={fakeApi()}
          onViewSale={onViewSale}
        />
      </DemoModeProvider>,
    );

    fireEvent.click(await screen.findByRole("button", { name: "View sale SALE-1001" }));
    expect(onViewSale).toHaveBeenCalledWith("SALE-1001");
  });

  it("shows the permission-denied state for a NOT_PERMITTED failure", async () => {
    render(
      <DemoModeProvider>
        <PaymentsTab from="2026-10-01" to="2026-10-05" apiOverride={deniedApi()} />
      </DemoModeProvider>,
    );

    expect(await screen.findByText(/don.t have access to payments/i)).toBeInTheDocument();
  });
});
