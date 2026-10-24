import { expect, it, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { ApiError } from "@/lib/api/errors";
import { useExceptionPages, ExceptionStatus, ExceptionResults } from "./exception-page";

const api = vi.hoisted(() => ({ listReconciliationExceptions: vi.fn(), listProductExceptions: vi.fn() }));
vi.mock("@/lib/demo-mode", () => ({ useApi: () => api }));
beforeEach(() => {
  vi.resetAllMocks();
  api.listReconciliationExceptions.mockResolvedValue({ items: [], total: 201, page: 0, size: 10 });
  api.listProductExceptions.mockResolvedValue({ items: [], total: 0, page: 0, size: 10 });
});
function Queue() {
  const pages = useExceptionPages(new URLSearchParams("tab=product&page=20&size=10"));
  return <><ExceptionStatus daily={pages.daily} product={pages.product} />
    <ExceptionResults result={pages.current} kind="product" status="all" query="" selectedId={null} onReview={vi.fn()} keyboardEnabled={false} /></>;
}
it("uses server totals and requests the selected product page without loading every page", async () => {
  render(<Queue />);
  expect(await screen.findByText("201 open exceptions")).toBeInTheDocument();
  expect(api.listReconciliationExceptions).toHaveBeenCalledWith(0, 10, expect.any(Object));
  expect(api.listProductExceptions).toHaveBeenCalledWith(20, 10, expect.any(Object));
  expect(api.listProductExceptions).toHaveBeenCalledTimes(1);
});
for (const code of ["NOT_PERMITTED", "NETWORK_ERROR"] as const) {
  it(`does not treat ${code} as an empty product result`, async () => {
    api.listProductExceptions.mockRejectedValue(new ApiError(code, "Product read unavailable"));
    render(<Queue />);
    expect(await screen.findByText("Checks unavailable")).toBeInTheDocument();
    expect(screen.queryByText("All reconciled")).not.toBeInTheDocument();
    if (code === "NETWORK_ERROR") expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
    else expect(screen.getByText(/You don't have access to/)).toBeInTheDocument();
  });
}
