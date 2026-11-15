import { render, screen, fireEvent } from "@testing-library/react";
import { it, expect, vi } from "vitest";
import { QueryRegion } from "./query-region";

it("keeps stale values explicitly qualified and retries only the failed region", () => {
  const refetch = vi.fn();
  render(<QueryRegion subject="History" query={{ isPending: false, error: new Error("Offline"), data: [], refetch }}><span>Last successful rows</span></QueryRegion>);
  expect(screen.getByText(/Showing the last successful result/)).toBeInTheDocument();
  expect(screen.getByText("Last successful rows")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Retry History" })); expect(refetch).toHaveBeenCalledOnce();
});
it("does not reveal stale data after permission denial", () => {
  render(<QueryRegion subject="History" query={{ isPending: false, error: Object.assign(new Error("Denied"), { code: "NOT_PERMITTED" }), data: [1], refetch: vi.fn() }}><span>Sensitive row</span></QueryRegion>);
  expect(screen.queryByText("Sensitive row")).not.toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "You don't have access to History" })).toBeInTheDocument();
});
it("shows empty content only after a successful result", () => {
  const query = { isPending: false, error: new Error("Offline"), data: undefined, refetch: vi.fn() };
  const { rerender } = render(<QueryRegion subject="History" query={query}><span>No changes yet</span></QueryRegion>);
  expect(screen.queryByText("No changes yet")).not.toBeInTheDocument();
  rerender(<QueryRegion subject="History" query={{ ...query, error: null, data: [] }}><span>No changes yet</span></QueryRegion>);
  expect(screen.getByText("No changes yet")).toBeInTheDocument();
});
