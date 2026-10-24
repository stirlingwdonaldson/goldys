import { expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ExceptionsList } from "./exceptions-list";

it("keeps server page controls when a local filter matches nothing", () => {
  const next = vi.fn();
  render(<ExceptionsList kind="product" exceptions={[{ id: "2026-10-24:beer", recordId: "beer", entity: "beer", field: "amount", sources: [], status: "conflict" }]}
    status="missing" query="" selectedId={null} onReview={vi.fn()} keyboardEnabled={false}
    pagination={{ page: 0, pageSize: 10, total: 201, onPageChange: next, onPageSizeChange: vi.fn() }} />);
  expect(screen.getByText(/No matches on this page/)).toBeInTheDocument();
  expect(screen.queryByText("All reconciled")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Last page" }));
  expect(next).toHaveBeenCalledWith(20);
});

it("does not call an empty later page reconciled", () => {
  render(<ExceptionsList kind="daily" exceptions={[]} status="all" query="" selectedId={null} onReview={vi.fn()} keyboardEnabled={false}
    pagination={{ page: 20, pageSize: 10, total: 200, onPageChange: vi.fn(), onPageSizeChange: vi.fn() }} />);
  expect(screen.queryByText("All reconciled")).not.toBeInTheDocument();
});
