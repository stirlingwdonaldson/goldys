import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import SalesDetailPage from "./page";

// `useSearchParams` is read once on mount to seed the active tab from the URL,
// so the deep-link test can point it at `?tab=` before rendering.
const mockParams = vi.hoisted(() => ({ current: new URLSearchParams() }));

vi.mock("next/navigation", () => ({
  useSearchParams: () => mockParams.current,
}));

// The real tabs fetch through the Api context; this page test only covers the shell.
vi.mock("@/components/sales-detail/payments-tab", () => ({
  PaymentsTab: () => <div data-testid="payments-tab" />,
}));

vi.mock("@/components/sales-detail/deleted-orders-tab", () => ({
  DeletedOrdersTab: () => <div data-testid="deleted-orders-tab" />,
}));

vi.mock("@/components/sales-detail/sale-items-tab", () => ({
  SaleItemsTab: () => <div data-testid="sale-items-tab" />,
}));

beforeEach(() => {
  mockParams.current = new URLSearchParams();
});

describe("SalesDetailPage", () => {
  it("renders the Sales detail header and the three tabs", () => {
    render(<SalesDetailPage />);
    expect(screen.getByRole("heading", { name: "Sales detail" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Payments" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Deleted orders" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Sale items" })).toBeInTheDocument();
  });

  it("opens on the Payments tab by default", () => {
    render(<SalesDetailPage />);
    expect(screen.getByTestId("payments-tab")).toBeInTheDocument();
    expect(screen.queryByTestId("deleted-orders-tab")).not.toBeInTheDocument();
  });

  it("switches the visible tab when another tab is selected", () => {
    render(<SalesDetailPage />);
    // Radix Tabs activate on mouse-down, not click.
    fireEvent.mouseDown(screen.getByRole("tab", { name: "Deleted orders" }), { button: 0 });
    expect(screen.getByTestId("deleted-orders-tab")).toBeInTheDocument();
    expect(screen.queryByTestId("payments-tab")).not.toBeInTheDocument();
  });

  it("deep-links the active tab from the ?tab= search param", () => {
    mockParams.current = new URLSearchParams("tab=sale-items");
    render(<SalesDetailPage />);
    expect(screen.getByTestId("sale-items-tab")).toBeInTheDocument();
    expect(screen.queryByTestId("payments-tab")).not.toBeInTheDocument();
  });

  it("renders a shared date-range filter defaulting to a trailing 30-day window", () => {
    render(<SalesDetailPage />);
    const from = screen.getByLabelText("From") as HTMLInputElement;
    const to = screen.getByLabelText("To") as HTMLInputElement;
    expect(from.value).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(to.value).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    // 30 days inclusive of both ends => the window spans 29 days.
    const spanDays =
      (Date.parse(`${to.value}T00:00:00Z`) - Date.parse(`${from.value}T00:00:00Z`)) / 86_400_000;
    expect(spanDays).toBe(29);
  });
});
