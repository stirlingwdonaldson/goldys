import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DemoModeProvider } from "@/lib/demo-mode";
import { ApiError, type Api } from "@/lib/api";
import type { SaleItemMix, SaleItemRow } from "@/lib/api/types";
import { SaleItemsTab } from "./sale-items-tab";

const ROWS: SaleItemRow[] = [
  {
    tradingDate: "2026-10-04",
    saleNumber: "SALE-1001",
    receiptLineId: "LINE-1",
    itemName: "pint carlton draught",
    productNumber: "P-100",
    sku: "SKU-100",
    categoryName: "Beer",
    quantitySold: 2,
    amount: 24.0,
    soldPriceIncTax: 12.0,
    totalTax: 2.18,
    costIncTax: 6.4,
    orderType: "Dine-in",
    saleType: "Sale",
    staffName: "Stirling Donaldson",
    registerName: "Main Bar",
    tableNumber: "8",
  },
  {
    tradingDate: "2026-10-05",
    // A receipt line with no matched sale: the row still lists, but offers no navigation.
    saleNumber: null,
    receiptLineId: "LINE-3",
    itemName: "chicken schnitzel",
    productNumber: null,
    sku: null,
    categoryName: "Food",
    quantitySold: 1,
    amount: 26.5,
    soldPriceIncTax: 26.5,
    totalTax: 2.41,
    costIncTax: null,
    orderType: "Takeaway",
    saleType: "Sale",
    staffName: null,
    registerName: null,
    tableNumber: null,
  },
];

const MIX: SaleItemMix[] = [
  { tradingDate: "2026-10-04", categoryName: "Beer", quantity: 3, amount: 35.0, hasConflict: false },
  { tradingDate: "2026-10-05", categoryName: "Food", quantity: 2, amount: 54.5, hasConflict: false },
];

function fakeApi(rows: SaleItemRow[] = ROWS): Api {
  return {
    getSaleItemMix: async () => MIX,
    listSaleItems: async (filter: { category?: string }) => {
      const items = rows.filter((r) => !filter.category || r.categoryName === filter.category);
      return { items, total: items.length, page: 0, size: 50 };
    },
  } as unknown as Api;
}

function deniedApi(): Api {
  const fail = async () => {
    throw new ApiError("NOT_PERMITTED", "Not permitted");
  };
  return { getSaleItemMix: fail, listSaleItems: fail } as unknown as Api;
}

describe("SaleItemsTab", () => {
  it("renders the resolved mix chart and the sale-item rows with formatted currency", async () => {
    render(
      <DemoModeProvider>
        <SaleItemsTab from="2026-10-01" to="2026-10-05" apiOverride={fakeApi()} />
      </DemoModeProvider>,
    );

    expect(await screen.findByText("pint carlton draught")).toBeInTheDocument();
    // The resolved mix chart is titled from the builder.
    expect(screen.getByText("Sale item mix")).toBeInTheDocument();
    expect(screen.getByText("chicken schnitzel")).toBeInTheDocument();
    expect(screen.getByText("SKU-100")).toBeInTheDocument();
    expect(screen.getByText("P-100")).toBeInTheDocument();

    // Currency columns are formatted (sold price, cost, total).
    expect(screen.getByText("$12.00")).toBeInTheDocument();
    expect(screen.getByText("$6.40")).toBeInTheDocument();
    expect(screen.getByText("$24.00")).toBeInTheDocument();
    // The second row's sold price and total are both $26.50.
    expect(screen.getAllByText("$26.50").length).toBeGreaterThan(0);

    // Nullable text and cost fall back to an em dash (LINE-3 has no product, SKU or cost).
    expect(screen.getAllByText("—").length).toBeGreaterThan(0);
  });

  it("filters the table by category from the mix", async () => {
    render(
      <DemoModeProvider>
        <SaleItemsTab from="2026-10-01" to="2026-10-05" apiOverride={fakeApi()} />
      </DemoModeProvider>,
    );

    await screen.findByText("pint carlton draught");
    fireEvent.change(screen.getByLabelText("Category"), { target: { value: "Food" } });

    await waitFor(() =>
      expect(screen.queryByText("pint carlton draught")).not.toBeInTheDocument(),
    );
    expect(screen.getByText("chicken schnitzel")).toBeInTheDocument();
  });

  it("calls onViewSale with the row's sale number", async () => {
    const onViewSale = vi.fn();
    render(
      <DemoModeProvider>
        <SaleItemsTab
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

  it("renders no View sale action for a row with a null sale number", async () => {
    const onViewSale = vi.fn();
    render(
      <DemoModeProvider>
        <SaleItemsTab
          from="2026-10-01"
          to="2026-10-05"
          apiOverride={fakeApi([ROWS[1]])}
          onViewSale={onViewSale}
        />
      </DemoModeProvider>,
    );

    expect(await screen.findByText("chicken schnitzel")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /View sale/ })).not.toBeInTheDocument();
  });

  it("shows the permission-denied state for a NOT_PERMITTED failure", async () => {
    render(
      <DemoModeProvider>
        <SaleItemsTab from="2026-10-01" to="2026-10-05" apiOverride={deniedApi()} />
      </DemoModeProvider>,
    );

    expect(await screen.findByText(/don.t have access to sale items/i)).toBeInTheDocument();
  });
});
