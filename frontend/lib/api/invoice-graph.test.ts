import { beforeEach, expect, it, vi } from "vitest";
import { getInvoiceGraphInvoices, getInvoiceGraphLines, getInvoiceGraphInvoicesById, getInvoiceGraphLinesById } from "./invoice-graph";
const fetch = vi.hoisted(() => vi.fn().mockResolvedValue([]));
vi.mock("./client", () => ({fetchApi:fetch}));
beforeEach(() => fetch.mockClear());
it("keeps vendor numbers and names literal on legacy routes", async () => {
  await getInvoiceGraphLines("id:vendor");
  expect(fetch).toHaveBeenLastCalledWith("/api/inventory/graph/invoices/id%3Avendor/lines");
  await getInvoiceGraphInvoices("id:vendor","2026-10-01","2026-10-02");
  expect(fetch).toHaveBeenLastCalledWith("/api/inventory/graph/suppliers/id%3Avendor/invoices?from=2026-10-01&to=2026-10-02");
});
it("uses explicit routes for logical identities", async () => {
  await getInvoiceGraphLinesById("invoice-id");
  expect(fetch).toHaveBeenLastCalledWith("/api/inventory/graph/invoices/by-id/invoice-id/lines");
  await getInvoiceGraphInvoicesById("supplier-id","2026-10-01","2026-10-02");
  expect(fetch).toHaveBeenLastCalledWith("/api/inventory/graph/suppliers/by-id/supplier-id/invoices?from=2026-10-01&to=2026-10-02");
});
