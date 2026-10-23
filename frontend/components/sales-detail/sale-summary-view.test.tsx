import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import { DemoModeProvider } from "@/lib/demo-mode";
import { ApiError, type Api } from "@/lib/api";
import { demoApi } from "@/lib/api/demo";
import { SaleSummaryView } from "./sale-summary-view";

function emptyApi(): Api {
  const empty = async () => ({ items: [], total: 0, page: 0, size: 200 });
  return {
    listPayments: empty,
    listSaleItems: empty,
    listDeletedSales: empty,
  } as unknown as Api;
}

function deniedApi(): Api {
  const fail = async () => {
    throw new ApiError("NOT_PERMITTED", "Not permitted");
  };
  return {
    listPayments: fail,
    listSaleItems: fail,
    listDeletedSales: fail,
  } as unknown as Api;
}

function renderView(saleNumber: string, api: Api) {
  return render(
    <DemoModeProvider>
      <SaleSummaryView saleNumber={saleNumber} apiOverride={api} />
    </DemoModeProvider>,
  );
}

describe("SaleSummaryView", () => {
  it("renders the verdict, headline, checks and both receipt columns", async () => {
    renderView("SALE-1002", demoApi);
    expect(await screen.findByText("Doesn't balance")).toBeInTheDocument();
    expect(screen.getByText(/2 items for \$54\.50, \$108\.90 paid by Tyro and Visa/)).toBeInTheDocument();
    expect(screen.getByRole("list", { name: "What stands out" })).toBeInTheDocument();
    expect(screen.getByRole("table", { name: "Line items" })).toBeInTheDocument();
    expect(screen.getByRole("table", { name: "Tenders" })).toBeInTheDocument();
    expect(within(screen.getByRole("table", { name: "Line items" })).getByText("chicken schnitzel")).toBeInTheDocument();
  });

  it("shows a voided sale as voided", async () => {
    renderView("SALE-999", demoApi);
    expect(await screen.findByText("Voided")).toBeInTheDocument();
  });

  it("says so when the sale has no records", async () => {
    renderView("SALE-404", emptyApi());
    expect(
      await screen.findByText("No payments or line items recorded for sale SALE-404."),
    ).toBeInTheDocument();
  });

  it("shows the permission state when the lists are not permitted", async () => {
    renderView("SALE-1", deniedApi());
    expect(await screen.findByText(/sale detail/i)).toBeInTheDocument();
  });

  it("cancels all three sale reads when the summary closes", async () => {
    const pending = vi.fn<Api["listPayments"]>(() => new Promise(() => {}));
    const api = {
      listPayments: pending,
      listSaleItems: pending,
      listDeletedSales: pending,
    } as unknown as Api;
    const { unmount } = renderView("SALE-1002", api);

    await waitFor(() => expect(pending).toHaveBeenCalledTimes(3));
    const signals = pending.mock.calls.map((call) => call[3]?.signal);
    expect(signals.every((signal) => signal instanceof AbortSignal && !signal.aborted)).toBe(true);

    unmount();
    expect(signals.every((signal) => signal?.aborted)).toBe(true);
  });
  it("keeps the receipt visible when recipe cost permission is denied", async () => {
    renderView("SALE-1002", { ...demoApi, getSaleCosting: async () => {
      throw new ApiError("NOT_PERMITTED", "Not permitted");
    }});
    expect(await screen.findByText("You do not have permission to view recipe costs.")).toBeInTheDocument();
    expect(screen.getByRole("table",{name:"Line items"})).toBeInTheDocument();
  });

  it("displays observed card mode without inferring it from tendered", async () => {
    renderView("SALE-1002", { ...demoApi, listPayments: async (...args) => {
      const rows = await demoApi.listPayments(...args);
      return {...rows, items:rows.items.map((p) => ({...p,lspayPaymentMode:"Card Payment"}))};
    }});
    const tenders = await screen.findByRole("table", {name:"Tenders"});
    expect(within(tenders).getAllByText(/^Card ·/)).toHaveLength(2);
  });
  it("labels the ex-GST assumption beside an estimated margin", async () => {
    renderView("SALE-1002", { ...demoApi, getSaleCosting: async () => ({
      lines: [], complete: true, recipeCost: 14.6, grossProfit: 24.49, margin: 0.6265,
      qualification: "EX_GST_COST_ASSUMED", recipeCostTaxBasis: "ASSUMED_EX_GST",
    })});
    expect(await screen.findByText("Estimated gross margin: 63%")).toBeInTheDocument();
    expect(screen.getByText(/Assumes CTB recipe costs exclude GST/)).toBeInTheDocument();
    expect(screen.getByText(/Estimated gross profit: \$24.49 excl. GST/)).toBeInTheDocument();
  });

  it("keeps a percentage unavailable when the tax assumption is disabled", async () => {
    renderView("SALE-1002", { ...demoApi, getSaleCosting: async () => ({
      lines: [], complete: true, recipeCost: 14.6, grossProfit: null, margin: null,
      qualification: "TAX_BASIS_UNVERIFIED", recipeCostTaxBasis: "UNVERIFIED",
    })});
    expect(await screen.findByText("The recipe cost tax basis is unverified, so gross margin is unavailable.")).toBeInTheDocument();
    expect(screen.queryByText(/Assumes CTB recipe costs exclude GST/)).not.toBeInTheDocument();
  });
  it("retains fractional portions when displaying recipe costs", async () => {
    renderView("SALE-1002", { ...demoApi, getSaleCosting: async () => ({
      lines: [{ receiptLineId: "fraction", itemName: "Half glass", quantity: 0.5,
        revenueIncTax: 7.5, revenueExTax: 6.82, recipeId: 57, recipeName: "Wine glass",
        matchMethod: "CTB_LINK", costPerPortion: 2.88, recipeCost: 1.44,
        costObservedAt: "2026-10-23T00:00:00Z", costSource: "CTB", status: "CURRENT_RECIPE_ESTIMATE" }],
      complete: true, recipeCost: 1.44, grossProfit: 5.38, margin: 0.7888563,
      qualification: "EX_GST_COST_ASSUMED", recipeCostTaxBasis: "ASSUMED_EX_GST",
    })});
    expect(await screen.findByText(/0.5 × \$2.88 = \$1.44 observed recipe cost/)).toBeInTheDocument();
  });
});
