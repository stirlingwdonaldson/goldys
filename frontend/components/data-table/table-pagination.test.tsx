import { act, fireEvent, render, renderHook, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { DEFAULT_PAGE_SIZE, TablePagination, useClientPagination } from "./table-pagination";

describe("TablePagination", () => {
  it("is hidden while everything fits on the smallest page", () => {
    const { container } = render(
      <TablePagination page={0} pageSize={10} total={10} onPageChange={vi.fn()} onPageSizeChange={vi.fn()} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("shows the range and pages forward, back, first and last", () => {
    const onPageChange = vi.fn();
    render(
      <TablePagination page={1} pageSize={10} total={35} onPageChange={onPageChange} onPageSizeChange={vi.fn()} />,
    );
    expect(screen.getByText("11–20 of 35")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    fireEvent.click(screen.getByRole("button", { name: "Previous page" }));
    fireEvent.click(screen.getByRole("button", { name: "First page" }));
    fireEvent.click(screen.getByRole("button", { name: "Last page" }));
    expect(onPageChange.mock.calls.map((c) => c[0])).toEqual([2, 0, 0, 3]);
  });

  it("disables moving past either end", () => {
    render(
      <TablePagination page={3} pageSize={10} total={35} onPageChange={vi.fn()} onPageSizeChange={vi.fn()} />,
    );
    expect(screen.getByText("31–35 of 35")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next page" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Last page" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Previous page" })).toBeEnabled();
  });

  it("stays visible at a larger size so the reader can shrink it again", () => {
    render(
      <TablePagination page={0} pageSize={50} total={30} onPageChange={vi.fn()} onPageSizeChange={vi.fn()} />,
    );
    expect(screen.getByText("1–30 of 30")).toBeInTheDocument();
  });
});

describe("useClientPagination", () => {
  const rows = Array.from({ length: 23 }, (_, i) => i);

  it("starts on a short first page", () => {
    const { result } = renderHook(() => useClientPagination(rows));
    expect(result.current.pagination.pageSize).toBe(DEFAULT_PAGE_SIZE);
    expect(result.current.pageRows).toEqual(rows.slice(0, 10));
  });

  it("pages, and returns to the first page when the size changes", () => {
    const { result } = renderHook(() => useClientPagination(rows));
    act(() => result.current.pagination.onPageChange(2));
    expect(result.current.pageRows).toEqual([20, 21, 22]);
    act(() => result.current.pagination.onPageSizeChange(25));
    expect(result.current.pagination.page).toBe(0);
    expect(result.current.pageRows).toEqual(rows);
  });

  it("clamps to the last page when the list shrinks", () => {
    const { result, rerender } = renderHook(({ list }) => useClientPagination(list), {
      initialProps: { list: rows },
    });
    act(() => result.current.pagination.onPageChange(2));
    rerender({ list: rows.slice(0, 12) });
    expect(result.current.pagination.page).toBe(1);
    expect(result.current.pageRows).toEqual([10, 11]);
  });
});
