import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
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
    expect(screen.getByText("chicken schnitzel")).toBeInTheDocument();
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
});
