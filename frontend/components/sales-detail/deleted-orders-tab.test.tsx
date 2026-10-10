import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { DemoModeProvider } from "@/lib/demo-mode";
import { ApiError, type Api } from "@/lib/api";
import type { DeletedSaleDay, DeletedSaleRow } from "@/lib/api/types";
import { DeletedOrdersTab } from "./deleted-orders-tab";

const ROWS: DeletedSaleRow[] = [
  {
    tradingDate: "2026-10-04",
    saleNumber: "SALE-999",
    orderType: "Dine-in",
    note: "Voided — wrong table",
    totalIncTax: 120.0,
    totalExTax: 109.09,
    totalTax: 10.91,
    totalCost: 40.0,
    openedRegisterName: "Main Bar",
    deletedRegisterName: "Front Bar",
    staffName: "Stirling Donaldson",
    deletedByStaffName: "Stirling Donaldson",
    tableNumber: "8",
    siteId: "SITE-1",
    customerName: null,
  },
  {
    tradingDate: "2026-10-05",
    saleNumber: "SALE-998",
    orderType: "Takeaway",
    note: null,
    totalIncTax: 65.5,
    totalExTax: 59.55,
    totalTax: 5.95,
    totalCost: null,
    openedRegisterName: "Bistro",
    deletedRegisterName: "Bistro",
    staffName: "Alex Smith",
    deletedByStaffName: "Alex Smith",
    tableNumber: null,
    siteId: "SITE-1",
    customerName: "Walk-up",
  },
];

const TOTALS: DeletedSaleDay[] = [
  { tradingDate: "2026-10-04", count: 1, totalIncTax: 120.0, totalTax: 10.91, hasConflict: false },
  { tradingDate: "2026-10-05", count: 1, totalIncTax: 65.5, totalTax: 5.95, hasConflict: false },
];

function fakeApi(): Api {
  return {
    getDeletedSaleTotals: async () => TOTALS,
    listDeletedSales: async () => ({ items: ROWS, total: ROWS.length, page: 0, size: 50 }),
  } as unknown as Api;
}

function deniedApi(): Api {
  const fail = async () => {
    throw new ApiError("NOT_PERMITTED", "Not permitted");
  };
  return { getDeletedSaleTotals: fail, listDeletedSales: fail } as unknown as Api;
}

describe("DeletedOrdersTab", () => {
  it("renders the resolved trend chart and the deleted order rows with formatted currency", async () => {
    render(
      <DemoModeProvider>
        <DeletedOrdersTab from="2026-10-01" to="2026-10-05" apiOverride={fakeApi()} />
      </DemoModeProvider>,
    );

    expect(await screen.findByText("SALE-999")).toBeInTheDocument();
    // The resolved trend chart is titled from the builder.
    expect(screen.getByText("Deleted orders per day")).toBeInTheDocument();
    expect(screen.getByText("SALE-998")).toBeInTheDocument();
    // The "Deleted register" column shows where the order was deleted, paired with
    // "Deleted by" — never the opening register (SALE-999 opened at "Main Bar").
    expect(screen.getByText("Deleted register")).toBeInTheDocument();
    expect(screen.getByText("Front Bar")).toBeInTheDocument();
    expect(screen.queryByText("Main Bar")).not.toBeInTheDocument();
    // The same staff member is both the sale's staff and the deleter, so both columns show it.
    expect(screen.getAllByText("Stirling Donaldson")).toHaveLength(2);

    // Period stat cards come from the resolved totals: count 2, value 120 + 65.5.
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("$185.50")).toBeInTheDocument();

    // Currency columns are formatted (inc tax, ex tax, tax, cost).
    expect(screen.getByText("$120.00")).toBeInTheDocument();
    expect(screen.getByText("$109.09")).toBeInTheDocument();
    expect(screen.getByText("$10.91")).toBeInTheDocument();
    expect(screen.getByText("$40.00")).toBeInTheDocument();

    // Nullable text and cost fall back to an em dash (SALE-998 has no note, table or cost).
    expect(screen.getAllByText("—").length).toBeGreaterThan(0);
  });

  it("calls onViewSale with the row's sale number", async () => {
    const onViewSale = vi.fn();
    render(
      <DemoModeProvider>
        <DeletedOrdersTab
          from="2026-10-01"
          to="2026-10-05"
          apiOverride={fakeApi()}
          onViewSale={onViewSale}
        />
      </DemoModeProvider>,
    );

    fireEvent.click(await screen.findByRole("button", { name: "View sale SALE-999" }));
    expect(onViewSale).toHaveBeenCalledWith("SALE-999");
  });

  it("shows the permission-denied state for a NOT_PERMITTED failure", async () => {
    render(
      <DemoModeProvider>
        <DeletedOrdersTab from="2026-10-01" to="2026-10-05" apiOverride={deniedApi()} />
      </DemoModeProvider>,
    );

    expect(await screen.findByText(/don.t have access to deleted orders/i)).toBeInTheDocument();
  });
});
